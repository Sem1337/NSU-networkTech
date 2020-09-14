package main;

public class ApplicationServer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("expected 1 arg: 'port'");
            return;
        }
        System.out.println(Integer.parseInt(args[0]));
        Server server = new Server(Integer.parseInt(args[0]));
        server.runServer();
    }

}
