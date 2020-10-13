package main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class ChatNode {

    private String name;
    private int packetLoss;
    private int timeoutToDisconnect = 15000;
    private int port;
    private Map<UUID,Neighbour> neighbours = new HashMap<>();
    private DatagramSocket recvSocket;
    private Neighbour delegate = null;

    ChatNode(String name, int packetLoss, int port) {
        this.name = name;
        this.port = port;
        try {
            recvSocket = new DatagramSocket(port);
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
        this.packetLoss = packetLoss;
    }

    ChatNode(String name, int packetLoss, int port, String neighbourIP, int neighbourPort) {
        this(name, packetLoss, port);
        try {
            Neighbour neighbour = new Neighbour(InetAddress.getByName(neighbourIP), neighbourPort);
            neighbours.put(neighbour.getId(), neighbour);
            sendConnectionRequest(name, neighbour);
        } catch (UnknownHostException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    void startCommunication() {
        new Thread(this::startReceiving).start();
        Scanner scanner = new Scanner(System.in);
        String message;
        while(!Thread.currentThread().isInterrupted()) {
            message = scanner.nextLine();
            sendMessageToAll(message);
        }

    }

    private void startReceiving() {
        int maxDatagramSize = 512;
        byte[] recvBuffer = new byte[maxDatagramSize];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(recvBuffer, recvBuffer.length);
                recvSocket.receive(packet);
                handleResponse(packet);
            }
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private void handleResponse(DatagramPacket packet) {
        UUID fromId = UUID.nameUUIDFromBytes((packet.getAddress().toString() + packet.getPort()).getBytes());
        Neighbour fromNeighbour = neighbours.get(fromId);
        if(fromNeighbour == null) {
            fromNeighbour = new Neighbour(packet.getAddress(), packet.getPort());
            neighbours.put(fromId, fromNeighbour);
        }
        try(    ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData());
                ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)    ) {

            DTO dto = (DTO) objectInputStream.readObject();
            System.out.println(dto.toString());

            Integer monitor = fromNeighbour.getMonitor(dto.getId());
            if(monitor != null) {
                synchronized (monitor) {
                    monitor.notifyAll();
                }
            }
            switch (dto.getHeader()) {
                case CONNECT:
                    fromNeighbour.setName(dto.getSenderName());
                    sendResponseToNeighbour(MessageHeader.RESPONSE_TO_CONNECT, name, dto.getId(), fromNeighbour);
                    break;
                case RESPONSE_TO_CONNECT:
                    fromNeighbour.setName(dto.getMessage());
                    break;
                case MESSAGE:
                    sendResponseToNeighbour(MessageHeader.CONFIRM, dto.getId().toString(), dto.getId(), fromNeighbour);                         // send confirmation of receiving message with particular id
                    sendMessageForwarding(dto, fromNeighbour);
                    break;
                case CONFIRM:
                    UUID whichMessageConfirmed = UUID.nameUUIDFromBytes(dto.getMessage().getBytes());
                    fromNeighbour.addSuccessfulSent(whichMessageConfirmed);
                    break;
                case NEW_DELEGATE:
                    if(dto instanceof NewDelegateDTO) {
                        fromNeighbour.setDelegate(((NewDelegateDTO) dto).getDelegate());
                        sendResponseToNeighbour(MessageHeader.CONFIRM, name, dto.getId(), fromNeighbour);
                    }
                    break;
                default:
                    System.out.println("unknown header received!");
            }
            if(delegate.getId().equals(UUID.nameUUIDFromBytes((recvSocket.getLocalAddress().toString() + this.port).getBytes())) || delegate == null) {
                chooseDelegate();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private void sendResponseToNeighbour(MessageHeader header, String data, UUID id, Neighbour neighbour) {
        DTO dto = new DTO(header, data, name, MessageType.RESPONSE);
        dto.setId(id);
        new Thread(new Transmitter(dto, neighbour)).start();
    }

    private void sendConnectionRequest(String data, Neighbour neighbour) {
        DTO dto = new DTO(MessageHeader.CONNECT, data, name, MessageType.REQUEST);
        new Thread(new Transmitter(dto, neighbour)).start();
    }

    private void sendMessageForwarding(DTO dto, Neighbour neighbour) {
        dto.setSenderName(name);
        for (Neighbour  receiver: neighbours.values()) {
            if(receiver != neighbour) {
                new Thread(new Transmitter(dto, receiver)).start();
            }
        }
    }

    private void sendMessageToAll(String data) {
        DTO dto = new DTO(MessageHeader.MESSAGE, data, name, MessageType.REQUEST);
        for (Neighbour  receiver: neighbours.values()) {
            new Thread(new Transmitter(dto, receiver)).start();
        }
    }

    private void sendMessageToAll(String data, Neighbour neighbour) {
        DTO dto = new NewDelegateDTO(data, name, neighbour);
        for (Neighbour  receiver: neighbours.values()) {
            new Thread(new Transmitter(dto, receiver)).start();
        }
    }

    private void chooseDelegate() {
        if(neighbours.values().iterator().hasNext()) {
            delegate = neighbours.values().iterator().next();
        }
        sendMessageToAll("", delegate);
    }

    private class Transmitter implements Runnable {

        Neighbour receiver;
        DTO dto;

        Transmitter(DTO dto, Neighbour receiver) {
            this.dto = dto;
            this.receiver = receiver;
        }

        @Override
        public void run() {
            sendMessageToNode(dto, receiver);
        }

        private boolean packetLoss() {
            return ThreadLocalRandom.current().nextInt(0,101) < packetLoss;
        }

        private void sendMessageToNode(DTO dto, Neighbour receiver) {
            if(receiver.checkSuccessfulSent(dto.getId())) return;
            receiver.addMessage(dto.getId());
            byte[] sendBuffer;
            try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ObjectOutputStream oo = new ObjectOutputStream(outputStream)) {
                oo.writeObject(dto);
                sendBuffer = outputStream.toByteArray();
                DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, receiver.getIp(), receiver.getPort());
                boolean disconnect = false;
                long startWaitingTime = System.currentTimeMillis();
                do {
                    Long sendingTime = System.currentTimeMillis();
                    if(!packetLoss()) {
                        System.out.println("sending");
                        recvSocket.send(packet);
                    }
                    if(dto.getMessageType().equals(MessageType.RESPONSE) || waitingResponse(sendingTime, dto.getId())) { //got resp
                        break;
                    } else if (System.currentTimeMillis() -  startWaitingTime > timeoutToDisconnect) {  // to much
                        disconnect = true;
                        break;
                    }
                } while(true);
                if(disconnect) {
                    disconnectNeighbour(receiver);
                } else {
                    receiver.removeMessage(dto.getId());
                }
            } catch (IOException e) {
                System.out.println(e.getLocalizedMessage());
            }
        }

        private void disconnectNeighbour(Neighbour neighbour) {
            neighbours.remove(neighbour.getId());
            if(neighbour == delegate) {
                delegate = null;
                chooseDelegate();
            }
            System.out.println("didn't receive responses for " + 1.0 * timeoutToDisconnect / 1000 + " seconds. (" + neighbour.getName()+ ": " + neighbour.getName() + " disconnected)");

        }

        private boolean waitingResponse(Long sendingTime, UUID id) {         // false if waiting too long
            Integer monitor = receiver.getMonitor(id);
            synchronized (monitor) {
                int timeout = 1500;
                try {
                    monitor.wait(timeout);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return System.currentTimeMillis() - sendingTime < timeout;
            }
        }
    }





    static class Neighbour implements Serializable {
        private UUID id;
        private int port;
        private InetAddress ip;
        private String name;
        private Map<UUID, Integer> messageMonitors = new HashMap<>();// list of sent messages to that neighbour with their synchronization monitors
        private Set<UUID> successfulSentMessages = new HashSet<>();
        private Neighbour delegate;
        Neighbour(InetAddress ip, int port) {
            this.ip = ip;
            this.port = port;
            this.name = "Unknown";
            id = UUID.nameUUIDFromBytes((this.ip.toString() + this.port).getBytes());
        }

        Neighbour getDelegate() {
            return delegate;
        }

        void setDelegate(Neighbour delegate) {
            this.delegate = delegate;
        }

        UUID getId() {
            return id;
        }

        void addSuccessfulSent(UUID id) {
            successfulSentMessages.add(id);
        }

        boolean checkSuccessfulSent(UUID id) {
            return successfulSentMessages.contains(id);
        }

        void addMessage(UUID id) {
            messageMonitors.put(id, 1);
        }

        void removeMessage(UUID id) {
            messageMonitors.remove(id);
        }

        Integer getMonitor(UUID id) {
            return messageMonitors.get(id);
        }

        String getName() {
            return name;
        }
        InetAddress getIp() {
            return ip;
        }
        int getPort() {
            return port;
        }

        void setName(String name) {
            this.name = name;
        }

    }

}
