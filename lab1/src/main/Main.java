package main;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("group ip expected");
            return;
        }

        long timeout = 3000L;
        long pingFrequency = 1000L;
        int receivingTimeout = 1000;
        long lastPingTime = 0L;

        try (MulticastSocket socket = new MulticastSocket(1337)) {

            InetAddress hostInetAddress = InetAddress.getLocalHost();
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(InetAddress.getByName("192.168.0.100"));
            SocketAddress group = new InetSocketAddress(args[0], 1337);

            socket.joinGroup(group, networkInterface);
            socket.setLoopbackMode(false);
            socket.setSoTimeout(receivingTimeout);

            byte[] recvBuf = new byte[256];
            byte[] sendBuf;
            HashMap<String, Long> lastReceivedMessageTime = new HashMap<>();

            while(true) {
                Long currentTime = System.currentTimeMillis();
                if(currentTime - lastPingTime > pingFrequency) {
                    sendBuf = hostInetAddress.toString().getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, group);
                    socket.send(sendPacket);
                    lastPingTime = currentTime;
                }

                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                try {
                    socket.receive(recvPacket);
                    String received = new String(recvPacket.getData(), 0, recvPacket.getLength());
                    lastReceivedMessageTime.put(received, System.currentTimeMillis());
                } catch(IOException ex) {
                    System.out.println("no packets received");
                }

                //print list of connected hosts
                System.out.println("----------------------------------------");
                for (String ip: lastReceivedMessageTime.keySet()) {
                    System.out.println(ip);
                }

                ///remove inactive hosts
                lastReceivedMessageTime.entrySet().removeIf(e -> currentTime - e.getValue() > timeout);
            }


        } catch(IOException ex) {
            System.out.println(ex.getLocalizedMessage());
        }

    }
}
