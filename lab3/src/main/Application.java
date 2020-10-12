package main;

public class Application {

    public static void main(String[] args) {
        if(args.length == 3) {
            new ChatNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2])).startCommunication();
        } else if(args.length == 5) {
            new ChatNode(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), args[3], Integer.parseInt(args[4])).startCommunication();
        } else {
            System.out.println("expected: name, packet loss, port,  optional(ip to connect, port to connect)");
        }

    }

}
