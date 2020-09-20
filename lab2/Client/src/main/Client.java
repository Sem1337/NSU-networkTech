package main;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Paths;

class Client {

    private InetAddress serverAddr;
    private int port = 1337;

    Client(String addr, int port) throws IOException {
        this.serverAddr = InetAddress.getByName(addr);
        this.port = port;

    }

    void sendFile(String path) {
        try(Socket serverDialog = new Socket(serverAddr, port);
            DataInputStream in = new DataInputStream(serverDialog.getInputStream());
            DataOutputStream out = new DataOutputStream(serverDialog.getOutputStream());
            FileInputStream fileInputStream = new FileInputStream(path))
        {
            File file = Paths.get(path).toFile();
            out.writeUTF(file.getName());
            out.writeLong(file.length());
            int bufferSize = 8192;
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ( (bytesRead = fileInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }

            String transferStatus = in.readUTF();
            System.out.println(transferStatus);
        } catch (IOException ex) {
            System.out.println(ex.getLocalizedMessage());
        }
    }



}
