package main;

import main.dto.DTO;
import main.dto.extendedDTO;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChatNode implements Serializable {

    private String name;
    private int packetLoss;
    private static int timeoutToDisconnect = 10000;
    private UUID id = UUID.randomUUID();
    private Map<UUID, Neighbour> neighbours = new HashMap<>();
    private DatagramSocket recvSocket;
    private Neighbour delegate = null;
    private final Set<String> ReceivedMessages = new HashSet<>();
    private Neighbour targetToConnect;

    ChatNode(String name, int packetLoss, int port) {
        this.name = name;
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
            targetToConnect = new Neighbour(InetAddress.getByName(neighbourIP), neighbourPort);
            sendConnectionRequest(id.toString());
        } catch (UnknownHostException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    void startCommunication() {
        new Thread(this::startReceiving).start();
        Scanner scanner = new Scanner(System.in);
        String message;
        while (!Thread.currentThread().isInterrupted()) {
            message = scanner.nextLine();
            sendMessageToAll(message);
        }

    }

    private void startReceiving() {
        int maxDatagramSize = 8192;
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

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData());
             ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {

            DTO dto = (DTO) objectInputStream.readObject();
            //discard packet if haven't this node in neighbours and this is not a response_to_connect packet
            if(!neighbours.containsKey(dto.getSenderID())
                    && !dto.getHeader().equals(MessageHeader.RESPONSE_TO_CONNECT)
                    && !dto.getHeader().equals(MessageHeader.CONNECT)) {
                return;
            }

            new Thread(() -> {
                synchronized (ReceivedMessages) {
                    if (!ReceivedMessages.contains(dto.getId().toString() + dto.getSenderID().toString())) {
                        System.out.println("[received] " + dto );
                        ReceivedMessages.add(dto.getId().toString() + dto.getSenderID().toString());
                        try {
                            ReceivedMessages.wait(timeoutToDisconnect);
                            ReceivedMessages.remove(dto.getId().toString() + dto.getSenderID().toString());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }).start();


            Neighbour sender = neighbours.get(dto.getSenderID());

            if (sender == null) {
                sender = new Neighbour(packet.getAddress(), packet.getPort());
                sender.setId(dto.getSenderID());
                sender.setName(dto.getSenderName());
                neighbours.put(dto.getSenderID(), sender);
            }

            Integer monitor;
            switch (dto.getHeader()) {
                case CONNECT:
                    sendResponse(MessageHeader.RESPONSE_TO_CONNECT, name, dto.getId(), sender);
                    break;
                case RESPONSE_TO_CONNECT:
                    monitor = targetToConnect.getMonitor(dto.getId());
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                    sender.setName(dto.getSenderName());
                    break;
                case MESSAGE:
                    sendResponse(MessageHeader.CONFIRM, dto.getId().toString(), dto.getId(), sender);                         // send confirmation of receiving message with particular id
                    forwardMessage(dto, sender);
                    break;
                case CONFIRM:
                    monitor = sender.getMonitor(dto.getId());
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                    UUID whichMessageConfirmed = UUID.nameUUIDFromBytes(dto.getMessage().getBytes());
                    sender.addSuccessfulDispatch(whichMessageConfirmed);
                    break;
                case NEW_DELEGATE:
                    if (dto instanceof extendedDTO) {
                        sender.setDelegate(((extendedDTO) dto).getDelegate());
                        sendResponse(MessageHeader.CONFIRM, name, dto.getId(), sender);
                    }
                    break;
                default:
                    System.out.println("unknown header received!");
            }
            if (delegate == null || delegate.getId() == this.id) {
                chooseDelegate();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private void sendResponse(MessageHeader header, String data, UUID id, Neighbour neighbour) {
        DTO dto = new DTO(header, MessageType.RESPONSE, name, this.id, data, neighbour.getName());
        dto.setId(id);
        new Thread(new Transmitter(dto, neighbour)).start();
    }

    private void sendConnectionRequest(String data) {
        DTO dto = new extendedDTO(MessageHeader.CONNECT, name, this.id, delegate, data, targetToConnect.getName());
        new Thread(new Transmitter(dto, targetToConnect)).start();
    }

    private void forwardMessage(DTO dto, Neighbour neighbour) {
        for (Neighbour receiver : neighbours.values()) {
            if (!receiver.getId().equals(neighbour.getId())) {
                DTO dtoToForward = new DTO(dto.getHeader(), dto.getMessageType(), name, id, dto.getMessage(), receiver.getName());
                dtoToForward.setId(dto.getId());
                new Thread(new Transmitter(dtoToForward, receiver)).start();
            }
        }
    }

    private void sendMessageToAll(String data) {
        UUID dtoID = UUID.randomUUID();
        for (Neighbour receiver : neighbours.values()) {
            DTO dto = new DTO(MessageHeader.MESSAGE, MessageType.REQUEST, name, this.id, data, receiver.getName());
            dto.setId(dtoID);
            new Thread(new Transmitter(dto, receiver)).start();
        }
    }

    private void sendNotificationAboutNewDelegate(Neighbour neighbour) {
        UUID dtoID = UUID.randomUUID();
        for (Neighbour receiver : neighbours.values()) {
            DTO dto = new extendedDTO(MessageHeader.NEW_DELEGATE, name, this.id, neighbour, "", receiver.getName());
            dto.setId(dtoID);
            new Thread(new Transmitter(dto, receiver)).start();
        }
    }

    private void chooseDelegate() {
        if (neighbours.values().iterator().hasNext()) {
            delegate = neighbours.values().iterator().next();
        }
        sendNotificationAboutNewDelegate(delegate);
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
            return ThreadLocalRandom.current().nextInt(0, 101) < packetLoss;
        }

        private void sendMessageToNode(DTO dto, Neighbour receiver) {
            if (receiver.checkSuccessfulDispatch(dto.getId())) return;
            receiver.addMessage(dto.getId());
            byte[] sendBuffer;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ObjectOutputStream oo = new ObjectOutputStream(outputStream)) {
                oo.writeObject(dto);
                sendBuffer = outputStream.toByteArray();
                DatagramPacket packet = new DatagramPacket(sendBuffer, sendBuffer.length, receiver.getIp(), receiver.getPort());
                boolean disconnect = false;
                long startWaitingTime = System.currentTimeMillis();

                Integer monitor = receiver.getMonitor(dto.getId());
                synchronized (monitor) {
                    do {
                        Long sendingTime = System.currentTimeMillis();
                        if (!packetLoss()) {
                            System.out.println("[sent] " + dto);
                            recvSocket.send(packet);
                        }
                        if (dto.getMessageType().equals(MessageType.RESPONSE) || waitingResponse(sendingTime, monitor)) { //got resp
                            break;
                        } else if (System.currentTimeMillis() - startWaitingTime > timeoutToDisconnect) {  // to much
                            disconnect = true;
                            break;
                        }
                    } while (true);
                }
                if (disconnect) {
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
            Neighbour newNeighbour = neighbour.getDelegate();

            if (neighbour == delegate) {
                delegate = null;
                chooseDelegate();
            }

            if (newNeighbour != null && !ChatNode.this.id.equals(newNeighbour.getId())) {
                neighbours.put(newNeighbour.getId(), newNeighbour);
                targetToConnect = newNeighbour;
                sendConnectionRequest(id.toString());
            }

            System.out.println("didn't receive responses for " + 1.0 * timeoutToDisconnect / 1000 + " seconds. (" + neighbour.getName() + ": " + neighbour.getName() + " disconnected)");
        }

        private boolean waitingResponse(Long sendingTime, Integer monitor) {
            int timeout = 1500;
            try {
                monitor.wait(timeout);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return System.currentTimeMillis() - sendingTime < timeout;
        }
    }


    public static class Neighbour implements Serializable {
        private UUID id;
        private int port;
        private InetAddress ip;
        private String name;
        private transient Map<UUID, Integer> messageMonitors = new HashMap<>();// list of sent messages to that neighbour with their synchronization monitors
        private transient Set<UUID> successfulSentMessages = new HashSet<>();
        private transient Neighbour delegate;

        Neighbour(InetAddress ip, int port) {
            this.ip = ip;
            this.port = port;
            this.name = "Unknown";
            this.id = null;
        }

        Neighbour getDelegate() {
            return delegate;
        }

        void setDelegate(Neighbour delegate) {
            this.delegate = delegate;
        }

        void setId(UUID id) {
            this.id = id;
        }

        UUID getId() {
            return id;
        }

        void addSuccessfulDispatch(UUID id) {
            synchronized (successfulSentMessages) {
                successfulSentMessages.add(id);
            }
            new Thread(() -> {
                synchronized (successfulSentMessages) {
                    try {
                        successfulSentMessages.wait(timeoutToDisconnect);
                        successfulSentMessages.remove(id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                }
            }).start();
        }

        boolean checkSuccessfulDispatch(UUID id) {
            synchronized (successfulSentMessages) {
                return successfulSentMessages.contains(id);
            }
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

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            successfulSentMessages = new HashSet<>();
            messageMonitors = new HashMap<>();
        }

    }

}
