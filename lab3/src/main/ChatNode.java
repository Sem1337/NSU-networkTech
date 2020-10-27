package main;


import main.dto.DTO;
import main.dto.extendedDTO;
import main.util.PendingMessageRecord;
import main.util.MessageUniqueRecord;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class ChatNode implements Serializable {

    private String name;
    private int packetLoss;
    private static int timeoutToDisconnect = 30000;
    private UUID id = UUID.randomUUID();
    private final Map<UUID, Neighbour> neighbours = new ConcurrentHashMap<>();
    private DatagramSocket recvSocket;
    private Neighbour delegate = null;
    private final Map<MessageUniqueRecord, PendingMessageRecord> receivedMessages = new ConcurrentHashMap<>();
    private Neighbour targetToConnect;
    private final Map<MessageUniqueRecord, PendingMessageRecord> pendingRequests = new ConcurrentHashMap<>();
    private Queue<PendingMessageRecord> pendingResponses = new ConcurrentLinkedQueue<>();


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
        new Thread(this::managePendingRequests).start();
        new Thread(this::managePendingResponses).start();
        new Thread(this::receivedMessagesCleaner).start();
        new Thread(this::checkConnections).start();
        new Thread(this::pingNeighbours).start();
        Scanner scanner = new Scanner(System.in);
        String message;
        while (!Thread.currentThread().isInterrupted()) {
            message = scanner.nextLine();
            sendMessageToAll(message);
        }
    }

    private void pingNeighbours() {
        while(true) {
            synchronized (this) {
                try {
                    this.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            neighbours.forEach((key, neighbour) -> {
                putMessageToPendingResponses(new DTO(MessageHeader.PING, MessageType.RESPONSE, name, id, "", neighbour.getName()), neighbour);
            });
        }
    }

    private void checkConnections() {
        while(true) {
            synchronized (this) {
                try {
                    this.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            long currTime = System.currentTimeMillis();
            neighbours.forEach((key, neighbour) -> {
                if (neighbour.getLastPingTime() != -1 && currTime - neighbour.getLastPingTime() > timeoutToDisconnect) disconnectNeighbour(neighbour);
            });
        }
    }

    private void receivedMessagesCleaner() {
        while(true) {
            synchronized (this) {
                try {
                    this.wait(timeoutToDisconnect);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (receivedMessages) {
                    receivedMessages.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().getFirstTimeStamp() > timeoutToDisconnect);
                }
            }
        }
    }

    private void managePendingRequests() {
        long timeout = 80L;
        while(true) {
            synchronized (this) {
                try {
                    this.wait(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            Long currTime = System.currentTimeMillis();
            synchronized (pendingRequests) {
                for (Map.Entry<MessageUniqueRecord, PendingMessageRecord> mes : pendingRequests.entrySet()) {
                    DatagramPacket packet = mes.getValue().getPacket();
                    Long lastTimeStamp = mes.getValue().getLastTimeStamp();
                    Long firstTimeStamp = mes.getValue().getFirstTimeStamp();
                    if (currTime - firstTimeStamp > timeoutToDisconnect) {
                        synchronized (neighbours) {
                            if(neighbours.containsKey(mes.getKey().getReceiverID())) {
                                disconnectNeighbour(neighbours.get(mes.getKey().getReceiverID()));
                            }
                        }
                    } else if (currTime - lastTimeStamp > timeout || lastTimeStamp.equals(firstTimeStamp)) {
                        sendMessageToNode(mes.getKey(), packet);
                        mes.getValue().setLastTimeStamp(currTime);
                    }
                }
                pendingRequests.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().getFirstTimeStamp() > timeoutToDisconnect);
            }
        }
    }

    private void managePendingResponses() {
        while(true) {
            synchronized (this) {
                try {
                    this.wait(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            PendingMessageRecord record;
            while((record = pendingResponses.poll()) != null) {
                sendMessageToNode(record.getPacket());
            }

        }
    }

    private void startReceiving() {
        int maxDatagramSize = 32768;
        byte[] recvBuffer = new byte[maxDatagramSize];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(recvBuffer, recvBuffer.length);
                recvSocket.receive(packet);
                if(!generatePacketLoss()) {
                    handleReceivedPacket(packet);
                }
            }
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private boolean generatePacketLoss() {
        return ThreadLocalRandom.current().nextInt(0,100) < packetLoss;
    }

    private synchronized void handleReceivedPacket(DatagramPacket packet) {

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(packet.getData());
             ObjectInputStream objectInputStream = new ObjectInputStream(inputStream)) {

            DTO dto = (DTO) objectInputStream.readObject();
            if (!neighbours.containsKey(dto.getSenderID())
                    && !dto.getHeader().equals(MessageHeader.RESPONSE_TO_CONNECT)
                    && !dto.getHeader().equals(MessageHeader.CONNECT)) {
                return;
            }

            if(dto.getHeader().equals(MessageHeader.MESSAGE) && !receivedMessages.containsKey(new MessageUniqueRecord(dto.getId(),dto.getSenderID()))) {
                System.out.println(dto.getMessage());
                receivedMessages.put(new MessageUniqueRecord(dto.getId(),dto.getSenderID()), new PendingMessageRecord(System.currentTimeMillis(),packet));
            }
            Neighbour sender = neighbours.get(dto.getSenderID());

            if (sender == null) {
                sender = new Neighbour(packet.getAddress(), packet.getPort());
            }
            sender.setLastPingTime(System.currentTimeMillis());
            switch (dto.getHeader()) {
                case CONNECT:
                    sender.setName(dto.getSenderName());
                    sender.setId(dto.getSenderID());
                    neighbours.put(sender.getId(), sender);
                    sendResponse(MessageHeader.RESPONSE_TO_CONNECT, name, dto.getId(), sender);
                    if(delegate!=null) {
                        sendDelegateToOne(sender);
                    }
                    break;
                case RESPONSE_TO_CONNECT:
                    popMessageFromPendingQueue(dto, sender);
                    sender.setName(dto.getSenderName());
                    sender.setId(dto.getSenderID());
                    neighbours.put(dto.getSenderID(), sender);
                    break;
                case MESSAGE:
                    sendResponse(MessageHeader.CONFIRM, dto.getId().toString(), dto.getId(), sender);
                    forwardMessage(dto, sender);
                    break;
                case CONFIRM:
                    sender.addSuccessfulDispatch(new MessageUniqueRecord(dto.getId(), sender.getId()), new PendingMessageRecord(System.currentTimeMillis()));
                    popMessageFromPendingQueue(dto, sender);
                    break;
                case NEW_DELEGATE:
                    if (dto instanceof extendedDTO) {
                        sender.setDelegate(((extendedDTO) dto).getDelegate());
                        sendResponse(MessageHeader.CONFIRM, name, dto.getId(), sender);
                    }
                    break;
                case PING:
                    sender.setLastPingTime(System.currentTimeMillis());
                    break;
                default:
                    System.out.println("unknown header received!");
            }
            //receivedMessages.add(new MessageUniqueRecord(dto.getId(), sender.getId()));
            if (delegate == null || delegate.getId() == this.id) {
                chooseDelegate();
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private void putMessageToPendingResponses(DTO dto, Neighbour receiver) {
        DatagramPacket packet = dtoToPacket(dto, receiver);
        pendingResponses.offer(new PendingMessageRecord(packet));
    }

    private void sendResponse(MessageHeader header, String data, UUID id, Neighbour neighbour) {
        DTO dto = new DTO(header, MessageType.RESPONSE, name, this.id, data, neighbour.getName());
        dto.setId(id);
        putMessageToPendingResponses(dto, neighbour);
    }

    private void sendConnectionRequest(String data) {
        DTO dto = new extendedDTO(MessageHeader.CONNECT, name, this.id, delegate, data, targetToConnect.getName());
        putMessageToPendingRequests(dto, targetToConnect);
    }

    private void forwardMessage(DTO dto, Neighbour neighbour) {
        for (Neighbour receiver : neighbours.values()) {
            if (!receiver.getId().equals(neighbour.getId())) {
                DTO dtoToForward = new DTO(dto.getHeader(), dto.getMessageType(), name, id, dto.getMessage(), receiver.getName());
                dtoToForward.setId(dto.getId());
                putMessageToPendingRequests(dtoToForward, receiver);
            }
        }
    }

    private void sendMessageToAll(String data) {
        UUID dtoID = UUID.randomUUID();
        for (Neighbour receiver : neighbours.values()) {
            DTO dto = new DTO(MessageHeader.MESSAGE, MessageType.REQUEST, name, this.id, data, receiver.getName());
            dto.setId(dtoID);
            putMessageToPendingRequests(dto, receiver);
        }
    }

    private void sendDelegateToOne(Neighbour receiver) {
        DTO dto = new extendedDTO(MessageHeader.NEW_DELEGATE, name, this.id, delegate, "", receiver.getName());
        dto.setId(delegate.getId());
        putMessageToPendingRequests(dto, receiver);
    }

    private void sendNotificationAboutNewDelegate(Neighbour newDelegate) {
        UUID dtoID = newDelegate.getId();
        for (Neighbour receiver : neighbours.values()) {
            DTO dto = new extendedDTO(MessageHeader.NEW_DELEGATE, name, this.id, newDelegate, "", receiver.getName());
            dto.setId(dtoID);
            putMessageToPendingRequests(dto, receiver);
        }
    }

    private void chooseDelegate() {
        if (neighbours.values().iterator().hasNext()) {
            delegate = neighbours.values().iterator().next();
        }
        if(delegate == null) return;
        sendNotificationAboutNewDelegate(delegate);
    }

    private void popMessageFromPendingQueue(DTO dto, Neighbour receiver) {
        synchronized (pendingRequests) {
            pendingRequests.remove(new MessageUniqueRecord(dto.getId(), receiver.getId()));
        }
    }

    private DatagramPacket dtoToPacket(DTO dto, Neighbour receiver) {
        byte[] sendBuffer;
        DatagramPacket packet = null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ObjectOutputStream oo = new ObjectOutputStream(outputStream)) {
            oo.writeObject(dto);
            sendBuffer = outputStream.toByteArray();
            packet = new DatagramPacket(sendBuffer, sendBuffer.length, receiver.getIp(), receiver.getPort());
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
        return packet;
    }

    private void putMessageToPendingRequests(DTO dto, Neighbour receiver) {
        synchronized (pendingRequests) {
            if (pendingRequests.containsKey(new MessageUniqueRecord(dto.getId(), receiver.getId()))) return;
            DatagramPacket packet = dtoToPacket(dto, receiver);
            MessageUniqueRecord messageUniqueRecord = new MessageUniqueRecord(dto.getId(), receiver.getId());
            if(receiver.checkSuccessfulDispatch(messageUniqueRecord))return;
            pendingRequests.put(messageUniqueRecord, new PendingMessageRecord(System.currentTimeMillis(), packet));
        }
    }


    private void sendMessageToNode(DatagramPacket packet) {
        try {
            recvSocket.send(packet);
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private synchronized void sendMessageToNode(MessageUniqueRecord record, DatagramPacket packet) {
        try {
            recvSocket.send(packet);
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    private synchronized void disconnectNeighbour(Neighbour neighbour) {
        if (neighbour==null || !neighbours.containsKey(neighbour.getId())) return;
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

    public static class Neighbour implements Serializable {
        private UUID id;
        private int port;
        private InetAddress ip;
        private String name;
        private transient long lastPingTime;
        private transient Map<MessageUniqueRecord, PendingMessageRecord> successfulSentMessages = new HashMap<>();
        private transient Neighbour delegate;

        Neighbour(InetAddress ip, int port) {
            this.ip = ip;
            this.port = port;
            this.name = "Unknown";
            this.id = null;
            lastPingTime = -1;
            startCleaner();
        }

        long getLastPingTime() {
            return lastPingTime;
        }

        void setLastPingTime(long time) {
            lastPingTime = time;
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

        synchronized void  addSuccessfulDispatch(MessageUniqueRecord messageUniqueRecord, PendingMessageRecord pendingMessageRecord) {
            successfulSentMessages.put(messageUniqueRecord, pendingMessageRecord);
        }

        synchronized boolean checkSuccessfulDispatch(MessageUniqueRecord messageUniqueRecord) {
            return successfulSentMessages.containsKey(messageUniqueRecord);
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

        private void startCleaner() {
            new Thread(() -> {
                while(true) {
                    synchronized (this) {
                        try {
                            this.wait(timeoutToDisconnect);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        successfulSentMessages.entrySet().removeIf(e-> System.currentTimeMillis() - e.getValue().getFirstTimeStamp() > timeoutToDisconnect);
                    }
                }
            }).start();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            successfulSentMessages = new ConcurrentHashMap<>();
            lastPingTime = -1;
            startCleaner();
        }

    }

}
