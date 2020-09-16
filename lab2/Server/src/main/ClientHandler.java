package main;

import java.io.*;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class ClientHandler implements Runnable {

    private Socket clientDialog;
    private byte[] buffer;
    private int speedCheckFrequencyMs = 3000;
    ClientHandler(Socket client) {
        clientDialog = client;
        int bufferSize = 8192;
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {

        try {
            DataInputStream in = new DataInputStream(clientDialog.getInputStream());

            String fileName = in.readUTF();
            System.out.println("fileName = " + fileName);

            long bytesInFile = in.readLong();
            System.out.println("size = " + bytesInFile);

            while(new File("uploads/" + fileName).exists())fileName = "1".concat(fileName);
            FileOutputStream fileOutputStream = new FileOutputStream("uploads/" + fileName);

            int bytesRead;
            long lastCheckedBytes = 0;
            long totalBytes = 0;

            

            while ( (bytesRead = in.read(buffer)) != -1) {
                totalBytes += bytesRead;
                fileOutputStream.write(buffer, 0, bytesRead);

            }


            System.out.println("Client disconnected");
            in.close();
            clientDialog.close();
            System.out.println("Channels closed");
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }
}
