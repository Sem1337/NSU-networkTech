package main.proxy;

import javafx.util.Pair;
import main.socks.MessageUtils;
import org.xbill.DNS.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class Proxy {

    private final ByteBuffer buffer = ByteBuffer.allocate(262144);
    private Selector selector;
    private int port;
    private Map<SocketChannel, Integer> connections = new HashMap<>();
    private Map<SocketChannel, SocketChannel> forwards = new HashMap<>();
    private Map<Integer, Pair<SocketChannel, Integer>> dnsQueries = new HashMap<>();
    private DatagramChannel dnsSocket;
    private List<Pair<SocketChannel, ByteBuffer>> pending = new LinkedList<>();

    public Proxy(int port) {
        this.port = port;
        try {
            dnsSocket = DatagramChannel.open();
            List<InetSocketAddress> dnsServers = ResolverConfig.getCurrentConfig().servers();
            dnsSocket.connect(dnsServers.get(0));

        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }

    }


    public void start() throws IOException {
        selector = Selector.open();
        dnsSocket.configureBlocking(false);
        dnsSocket.register(selector, SelectionKey.OP_READ);
        try (ServerSocketChannel serverSocket = ServerSocketChannel.open()) {

            dnsSocket.configureBlocking(false);

            serverSocket.bind(new InetSocketAddress("localhost", port));
            serverSocket.configureBlocking(false);
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {

                    SelectionKey key = iter.next();
                    //System.out.println(key.channel());
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            register(selector, serverSocket);
                        }
                        if (key.isWritable()) {
                            //System.out.println("WRITING");
                            //System.out.println(key.channel());

                            SocketChannel channel = (SocketChannel) key.channel();
                            Integer stage = connections.get(channel);
                            if (stage != null && stage == 4) {
                                writeForwarding(channel);
                            }
                        }
                        if (key.isReadable()) {
                            if (key.channel() instanceof DatagramChannel) {
                                handleDnsResponse();
                            } else {
                                SocketChannel client = (SocketChannel) key.channel();
                                Integer stage = connections.get(client);
                                if (stage == null) {
                                    closeConnection(client);
                                } else {
                                    switch (connections.get(client)) {
                                        case 1:
                                            handleGreeting(client);
                                            break;
                                        case 2:
                                            handleTarget(client);
                                            break;
                                        case 4:
                                            readForwarding(client);
                                            break;
                                        default:
                                            break;
                                    }
                                }
                            }
                        }
                    }

                    iter.remove();
                }
            }
        }
    }

    private void handleDnsResponse() {
        System.out.println("DNS RESPONSE");
        ByteBuffer dnsBuf;
        Message msg;
        try {
            dnsBuf = ByteBuffer.allocate(1024);
            int len = dnsSocket.read(dnsBuf);
            if (len <= 0) return;
            msg = new Message(dnsBuf.array());
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
            return;
        }

        List<Record> recs = msg.getSection(1);
        for (Record rec : recs) {
            if (rec instanceof ARecord) {
                ARecord aRecord = (ARecord) rec;
                InetAddress adr = aRecord.getAddress();
                System.out.println("ADDR = " + adr);
                int id = msg.getHeader().getID();
                Pair<SocketChannel, Integer> myConnection = dnsQueries.get(id);
                int targetPort = myConnection.getValue();
                SocketChannel channel = myConnection.getKey();
                try {
                    if (!establishConnection(channel, new InetSocketAddress(adr, targetPort))) {
                        closeConnection(channel);
                    }
                } catch (IOException e) {
                    System.out.println(e.getLocalizedMessage());
                    closeConnection(channel);
                }
                dnsQueries.remove(id);
                break;
            }
        }

    }

    private void closeConnection(SocketChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
        connections.remove(channel);
        forwards.remove(channel);
    }

    private void writeForwarding(SocketChannel channel) {
        ByteBuffer byteBuffer;
        //System.out.println("writing to  " + channel);
        //System.out.println(pending);
        //System.out.println("finding " + channel);
        for (Pair<SocketChannel, ByteBuffer> p : pending) {
            if (p.getKey().equals(channel)) {
                byteBuffer = p.getValue();
                try {

                    channel.write(byteBuffer);
                    if (!byteBuffer.hasRemaining()) {
                        pending.remove(p);
                    }
                } catch (IOException e) {
                    closeConnection(channel);
                }
                return;
            }
        }
        //System.out.println("not found");
    }

    private void readForwarding(SocketChannel channel) {
        SocketChannel channelTo = forwards.get(channel);
        if (channelTo == null) return;
        //System.out.println(channelTo.isConnected());
        if (channelTo.isConnected()) {
            int amount;
            try {
                buffer.clear();
                amount = channel.read(buffer);

                if (amount == -1) {
                    closeConnection(channel);
                } else {
                    byte[] saveBuffer = new byte[amount];

                    buffer.flip();

                    buffer.get(saveBuffer);
                    ByteBuffer saveBuffer2 = ByteBuffer.wrap(saveBuffer);
                    pending.add(new Pair<>(channelTo, saveBuffer2));

                    //channelTo.configureBlocking(true);
                    //channelTo.write(ByteBuffer.wrap(buffer.array(), 0, amount));
                    //channelTo.configureBlocking(false);
                    //if (amount != sent) {
                    //    System.out.println("===================================================================" + sent + " " + amount);
                    //}
                }
            } catch (IOException e) {
                closeConnection(channel);
            }
        } else {
            closeConnection(channel);
        }
        buffer.clear();
    }

    private boolean establishConnection(SocketChannel channel, InetSocketAddress serverAddress) throws IOException {
        SocketChannel serverChannel = SocketChannel.open(serverAddress);
        if (!serverChannel.isConnected()) {
            return false;
        }
        try {
            MessageUtils.sendSecondConfirmationMessage(channel, serverAddress.getPort(), serverChannel.isConnected());
        } catch (IOException e) {
            return false;
        }
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_CONNECT | SelectionKey.OP_WRITE);
        channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        System.out.println("connect from " + channel + "  to " + serverChannel);
        forwards.put(channel, serverChannel);
        forwards.put(serverChannel, channel);
        System.out.println(channel.isConnected());
        System.out.println(serverChannel.isConnected());
        connections.put(channel, 4);
        connections.put(serverChannel, 4);
        return serverChannel.isConnected();
    }

    private void handleTarget(SocketChannel client) {
        System.out.println("second stage");
        MessageUtils.SecondMessage secondMessage = MessageUtils.getSecondMessage(client);
        try {
            if (secondMessage != null) {
                System.out.println(secondMessage.getPort());
                System.out.println(secondMessage.getTarget());
                if (secondMessage.getAddressType() == 0x03) {
                    System.out.println("connection stage 2 -> stage 3, resolving DNS: " + client);
                    Name name = Name.fromString(secondMessage.getTarget(), Name.root);
                    Record rec = Record.newRecord(name, Type.A, DClass.IN);
                    Message dns = Message.newQuery(rec);
                    dnsSocket.write(ByteBuffer.wrap(dns.toWire()));
                    dnsQueries.put(dns.getHeader().getID(), new Pair<>(client, secondMessage.getPort()));
                    connections.put(client, 3);
                } else {
                    establishConnection(client, new InetSocketAddress(secondMessage.getTarget(), secondMessage.getPort()));
                }

            } else {
                connections.remove(client);
            }
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
            connections.remove(client);
        }
    }

    private void handleGreeting(SocketChannel client) throws IOException {
        if (MessageUtils.getFirstMessage(client)) {
            MessageUtils.sendFirstConfirmation(client, true);
            connections.put(client, 2);
        } else {
            MessageUtils.sendFirstConfirmation(client, false);
            connections.remove(client);
        }
    }

    private void register(Selector selector, ServerSocketChannel serverSocket)
            throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        connections.put(client, 1);
        System.out.println("accepted");
    }

}
