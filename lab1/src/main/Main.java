package main;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
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

        try (MulticastSocket recvSocket = new MulticastSocket(1337); MulticastSocket sendSocket = new MulticastSocket(1338)) {
            InetAddress group = InetAddress.getByName(args[0]);
            recvSocket.joinGroup(group);
            recvSocket.setLoopbackMode(false);
            recvSocket.setSoTimeout(receivingTimeout);
            sendSocket.joinGroup(group);
            sendSocket.setLoopbackMode(false);
            InetAddress hostInetAddress = InetAddress.getLocalHost();
            byte[] recvBuf = new byte[256];
            byte[] sendBuf = new byte[256];
            HashMap<String, Long> lastMessageTime = new HashMap<>();

            while(true) {
                Long currentTime = System.currentTimeMillis();
                if(currentTime - lastPingTime > pingFrequency) {
                    sendBuf = hostInetAddress.toString().getBytes();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuf, sendBuf.length, group, 1337);
                    sendSocket.send(sendPacket);
                    lastPingTime = currentTime;
                }

                DatagramPacket recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                try {
                    recvSocket.receive(recvPacket);
                    String received = new String(recvPacket.getData(), 0, recvPacket.getLength());
                    lastMessageTime.put(received, System.currentTimeMillis());
                } catch(IOException ex) {
                    System.out.println("no packets received");
                }
                System.out.println("----------------------------------------");
                for (String ip: lastMessageTime.keySet()) {
                    System.out.println(ip);
                }
                lastMessageTime.entrySet().removeIf(e -> currentTime - e.getValue() > timeout);
            }


        } catch(IOException ex) {
            System.out.println(ex.getLocalizedMessage());
        }

    }
}
