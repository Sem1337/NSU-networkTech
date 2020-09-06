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

        int port = 1337;
        long timeout = 3000L;
        long pingFrequency = 1000L;
        int receivingTimeout = 1500;
        long lastPingTime = 0L;

        try (MulticastSocket socket = new MulticastSocket(port)) {

            InetAddress hostInetAddress = InetAddress.getLocalHost();
            InetAddress group = InetAddress.getByName(args[0]);
            socket.joinGroup(group);
            socket.setLoopbackMode(false);
            socket.setSoTimeout(receivingTimeout);

            byte[] recvBuf = new byte[256];
            byte[] sendBuf;
            HashMap<String, Long> lastReceivedMessageTime = new HashMap<>();

            while(true) {
                Long currentTime = System.currentTimeMillis();
                if(currentTime - lastPingTime > pingFrequency) {
                    sendBuf = hostInetAddress.toString().getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, group, port);
                    socket.send(sendPacket);
                    lastPingTime = currentTime;
                }

                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                try {
                    socket.receive(recvPacket);
                    System.out.println(recvPacket.getSocketAddress());
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
