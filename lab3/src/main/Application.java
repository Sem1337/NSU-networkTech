package main;

import java.net.DatagramSocket;
import java.net.InetAddress;

public class Application {

    public static void main(String[] args) {

        try(final DatagramSocket socket = new DatagramSocket()){
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            System.out.println(socket.getLocalAddress().getHostAddress());
        } catch(Exception e) {
            System.out.println(e.getLocalizedMessage());
        }

        if(args.length == 3) {
            new ChatNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2])).startCommunication();
        } else if(args.length == 5) {
            new ChatNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3], Integer.parseInt(args[4])).startCommunication();
        } else {
            System.out.println("expected: name, packet loss, port,  optional(ip to connect, port to connect)");
        }

    }

}
