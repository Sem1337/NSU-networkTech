package main;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class ClientHandler implements Runnable {

    private Socket clientDialog;
    private byte[] buffer;
    private int speedCheckFrequencyMs = 3000;
    private int id;
    ClientHandler(Socket client, int id) {
        this.id = id;
        clientDialog = client;
        int bufferSize = 8192;
        buffer = new byte[bufferSize];
    }

    @Override
    public void run() {
        FileOutputStream fileOutputStream = null;
        try (DataInputStream in = new DataInputStream(clientDialog.getInputStream());
             DataOutputStream out = new DataOutputStream(clientDialog.getOutputStream()))
        {

            String fileName = in.readUTF();
            long fileSizeBytes = in.readLong();

            Path path = Paths.get("uploads");
            if(!Files.exists(path)) {
                new File(path.toString()).mkdir();
            }
            while(new File("uploads/" + fileName).exists())fileName = "1".concat(fileName);
            fileOutputStream = new FileOutputStream("uploads/" + fileName);

            int bytesRead = 0;
            long totalBytesReceived = 0;


            class SpeedChecker implements Runnable {
                private long totalBytesReceived = 0;
                private long previousBytesReceivedValue = 0;
                private void setTotalBytesReceived(long value) {
                    this.totalBytesReceived = value;
                }
                private boolean status = true;
                private void finish() {
                    System.out.println("stopped");
                    status = false;
                }
                @Override
                public void run() {
                    long transferStartTime = System.currentTimeMillis();
                    long currentTime;
                    long previousTime = transferStartTime;
                    try {
                        synchronized (this) {
                            while (!Thread.currentThread().isInterrupted() && status) {
                                this.wait(speedCheckFrequencyMs);
                                currentTime = System.currentTimeMillis();
                                double secondsSinceStart = ((double)currentTime - transferStartTime) / 1000.0;
                                double totalMBytesReceived = (double)totalBytesReceived / (1024*1024);
                                double recentMBytesReceived = (double)(totalBytesReceived - previousBytesReceivedValue) / (1024*1024);
                                System.out.println(id + " client : average " + totalMBytesReceived / secondsSinceStart + " MB/sec");
                                double secondSinceLastSpeedCheck = (double)(currentTime - previousTime) / 1000;
                                System.out.println(id + " client : current " + recentMBytesReceived / secondSinceLastSpeedCheck + " MB/sec");
                                previousBytesReceivedValue = totalBytesReceived;
                                previousTime = currentTime;
                            }
                            System.out.println(id + " client : completed");
                        }
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            SpeedChecker speedChecker = new SpeedChecker();
            Thread speedCheckThread = new Thread(speedChecker);
            speedCheckThread.start();

            while (totalBytesReceived < fileSizeBytes && (bytesRead = in.read(buffer)) != -1) {
                totalBytesReceived += bytesRead;
                fileOutputStream.write(buffer, 0, bytesRead);
                speedChecker.setTotalBytesReceived(totalBytesReceived);
            }

            System.out.println("here");
            speedChecker.finish();

            synchronized (speedChecker) {
                speedChecker.notifyAll();
            }

            if (totalBytesReceived == fileSizeBytes) {
                out.writeUTF("successful transfer");
            } else {
                out.writeUTF("Error: " + totalBytesReceived + " bytes received, " + fileSizeBytes + " bytes expected");
            }


        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        } finally {
            try {
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
            }

            try {
                if (fileOutputStream != null) clientDialog.close();
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
            }

        }
    }
}
