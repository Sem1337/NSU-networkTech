package main;

import java.io.*;

public class ApplicationClient {

    public static void main(String[] args) {
        if(args.length != 3) {
            System.out.println("expected 3 arguments: 'path', 'server ip', 'port'");
            return;
        }

        try {
            new Client(args[1], Integer.parseInt(args[2])).sendFile(args[0]);
        } catch (IOException ex) {
            System.out.println(ex.getLocalizedMessage());
        }

    }
}
