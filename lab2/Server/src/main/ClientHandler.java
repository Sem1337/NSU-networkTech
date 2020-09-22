package main;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;


public class ClientHandler implements Runnable {

    private Socket clientDialog;
    private byte[] buffer;
    private int id;
    private long totalBytesReceived = 0;
    private long fileSizeBytes = 0;
    ClientHandler(Socket client, int id) {
        this.id = id;
        clientDialog = client;
        int bufferSize = 8192;
        buffer = new byte[bufferSize];
    }

    private String getTransferState() {
        return totalBytesReceived + "/" + fileSizeBytes + " bytes received";
    }

    class SpeedChecker implements Runnable {
        private long totalBytesReceived = 0;
        private long previousBytesReceivedValue = 0;
        private void setTotalBytesReceived(long value) {
            this.totalBytesReceived = value;
        }
        private boolean status = true;
        private void finish() {
            status = false;
            synchronized (this) {
                this.notifyAll();
            }
        }

        @Override
        public void run() {
            long transferStartTime = System.currentTimeMillis();
            long currentTime;
            long previousTime = transferStartTime;
            int speedCheckFrequencyMs = 3000;
            try {
                synchronized (this) {
                    while (!Thread.currentThread().isInterrupted() && status) {
                        this.wait(speedCheckFrequencyMs);
                        currentTime = System.currentTimeMillis();
                        double secondsSinceStart = ((double)currentTime - transferStartTime) / 1000.0;
                        double totalMBytesReceived = (double)totalBytesReceived / (1024*1024);
                        double recentMBytesReceived = (double)(totalBytesReceived - previousBytesReceivedValue) / (1024*1024);
                        DecimalFormat decimalFormat = new DecimalFormat("####.#");
                        System.out.println(id + " client : average " + decimalFormat.format(totalMBytesReceived / secondsSinceStart) + " MB/sec");
                        double secondSinceLastSpeedCheck = (double)(currentTime - previousTime) / 1000;
                        System.out.println(id + " client : current " + decimalFormat.format(recentMBytesReceived / secondSinceLastSpeedCheck) + " MB/sec");
                        previousBytesReceivedValue = totalBytesReceived;
                        previousTime = currentTime;
                    }
                }
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        FileOutputStream fileOutputStream = null;
        int bytesRead = 0;

        SpeedChecker speedChecker = new SpeedChecker();
        Thread speedCheckThread = new Thread(speedChecker);

        try (DataInputStream in = new DataInputStream(clientDialog.getInputStream());
             DataOutputStream out = new DataOutputStream(clientDialog.getOutputStream()))
        {
            String fileName = in.readUTF();
            fileSizeBytes = in.readLong();
            Path path = Paths.get("uploads");
            if(!Files.exists(path)) {
                new File(path.toString()).mkdir();
            }
            while(new File("uploads/" + fileName).exists())fileName = "1".concat(fileName);
            fileOutputStream = new FileOutputStream("uploads/" + fileName);

            speedCheckThread.start();
            while (totalBytesReceived < fileSizeBytes && (bytesRead = in.read(buffer)) != -1) {
                totalBytesReceived += bytesRead;
                fileOutputStream.write(buffer, 0, bytesRead);
                speedChecker.setTotalBytesReceived(totalBytesReceived);
            }
            speedChecker.finish();
            speedCheckThread.join();
            System.out.println(getTransferState());
            if (totalBytesReceived == fileSizeBytes) {
                out.writeUTF("successful transfer " + getTransferState());
            } else {
                out.writeUTF("Error: " + getTransferState());
            }

        } catch (Exception e) {
            speedChecker.finish();
            System.out.println(e.getLocalizedMessage());
            System.out.println(getTransferState());
        } finally {

            try {
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
            }

            try {
                if (clientDialog != null) clientDialog.close();
            } catch (IOException ex) {
                System.out.println(ex.getLocalizedMessage());
            }
            System.out.println(id + " client disconnected");
        }

    }
}
