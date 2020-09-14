package main;

import java.io.*;
import java.net.Socket;
import java.util.Random;

public class ClientHandler implements Runnable {

    private Socket clientDialog;
    private byte[] buffer;
    ClientHandler(Socket client) {
        clientDialog = client;
        int bufferSize = 8192;
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {

        try (FileOutputStream fileOutputStream = new FileOutputStream(new File("uploads/file" + new Random(10).toString() + ".jpg"))) {
            DataOutputStream out = new DataOutputStream(clientDialog.getOutputStream());
            DataInputStream in = new DataInputStream(clientDialog.getInputStream());
            int bytesRead;
            while ( (bytesRead = in.read(buffer)) != -1) {
                System.out.println(bytesRead);
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            System.out.println("Client disconnected");
            in.close();
            out.close();
            clientDialog.close();
            System.out.println("Channels closed");
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }
}
