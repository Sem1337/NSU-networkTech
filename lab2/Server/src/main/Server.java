package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Server {

    private static ExecutorService executeIt = Executors.newFixedThreadPool(5);
    private int port = 1337;

    Server(int port) {
        this.port = port;
    }

    void runServer() {


        int nextId = 1;
        try (ServerSocket server = new ServerSocket(port, 5, InetAddress.getByName("192.168.0.100"));
             BufferedReader br = new BufferedReader(new InputStreamReader(System.in)))
        {
            System.out.println("Server socket created");
            while (!server.isClosed()) {
                executeIt.execute(new ClientHandler(server.accept(), nextId++));
                System.out.println("Connection accepted");
            }
            executeIt.shutdown();
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

}
