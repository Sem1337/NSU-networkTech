package main;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

class ChatNode {

    private String name;
    private int packetLoss;
    private int timeoutToDisconnect = 5000;
    private Map<UUID,Neighbour> neighbours = new HashMap<>();
    private DatagramSocket recvSocket;
    //private Set<UUID> receivedMessages = new HashSet<>();


    static class Neighbour {

        private UUID id;                                         // uid of neighbour for that node
        private int port;
        private InetAddress ip;
        private String name;
        Map<UUID, Integer> messageMonitors = new HashMap<>();    // list of sent messages to that neighbour
        Set<UUID> successfulSentMessages = new HashSet<>();
        //DatagramSocket sendSocket;

        Neighbour(InetAddress ip, int port) {
            this.ip = ip;
            this.port = port;
           /* try {
                //System.out.println(this.ip);
                //sendSocket = new DatagramSocket(port);
                //System.out.println(socket.getPort());
            } catch (SocketException e) {
                System.out.println(e.getLocalizedMessage());
            }*/
            this.name = "Unknown";
            id = UUID.nameUUIDFromBytes((this.ip.toString() + this.port).getBytes());
        }

        /*DatagramSocket getSocket() {
            return sendSocket;
        }*/

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
            Neighbour neighbour = new Neighbour(InetAddress.getByName(neighbourIP), neighbourPort);
            neighbours.put(neighbour.getId(), neighbour);
            sendMessageToOne("connect", name, Type.REQUEST, neighbour);
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
            sendMessageToAll(message, Type.REQUEST);
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
        try(ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData());
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {
            DTO dto = (DTO) objectInputStream.readObject();


            Integer monitor = fromNeighbour.getMonitor(dto.getId());
            if(monitor != null) {
                synchronized (monitor) {
                    monitor.notifyAll();
                }
            }
            System.out.println(dto.toString());
            switch (dto.getHeader()) {
                case "connect":
                    fromNeighbour.setName(dto.getMessage());
                    sendMessageToOne("responseToConnect", name, Type.RESPONSE, fromNeighbour);
                    break;
                case "responseToConnect":
                    fromNeighbour.setName(dto.getMessage());
                    break;
                case "message":
                    sendMessageToOne("received", dto.getId().toString(), Type.RESPONSE, fromNeighbour);                         // send confirmation of receiving message with particular id
                    sendMessageForwarding(dto, fromNeighbour);
                    break;
                case "received":
                    UUID whichMessageConfirmed = UUID.nameUUIDFromBytes(dto.getMessage().getBytes());
                    fromNeighbour.addSuccessfulSent(whichMessageConfirmed);
                    break;
                default:
                    System.out.println("unknown header received!");
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getLocalizedMessage());
        }

    }

    private void sendMessageToOne(String header, String data, Type type, Neighbour neighbour) {
        DTO dto = new DTO(header, data, name, type);
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

    private void sendMessageToAll(String data, Type type) {
        DTO dto = new DTO("message", data, name, type);
        for (Neighbour  receiver: neighbours.values()) {
            new Thread(new Transmitter(dto, receiver)).start();
        }
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
                System.out.println("sending to: " + packet.getAddress());
                boolean disconnect = false;
                long startWaitingTime = System.currentTimeMillis();
                do {
                    Long sendingTime = System.currentTimeMillis();
                    //if(!packetLoss()) {
                    recvSocket.send(packet);
                    //}
                    if(dto.getType().equals(Type.RESPONSE) || waitingResponse(sendingTime, dto.getId())) { //got resp
                        System.out.println("don't waiting for response or got response");
                        break;
                    } else if (System.currentTimeMillis() -  startWaitingTime > timeoutToDisconnect) {  // to much
                        System.out.println("waiting to much!!");
                        disconnect = true;
                        break;
                    }
                } while(true);
                if(disconnect) {
                    System.out.println("disconnect");
                    disconnectNeighbour(receiver);
                } else {
                    System.out.println("confirmed");
                    receiver.removeMessage(dto.getId());
                }


            } catch (IOException e) {
                System.out.println(e.getLocalizedMessage());
            }
        }

        private boolean waitingResponse(Long sendingTime, UUID id) {         // false if waiting too long
            System.out.println("waiting");
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


    private void disconnectNeighbour(Neighbour neighbour) {
        neighbours.remove(neighbour.getId());
        System.out.println("didn't receive responses for " + 1.0 * timeoutToDisconnect / 1000 + " seconds. (" + neighbour.getName()+ ": " + neighbour.getName() + " disconnected)");
    }


}
