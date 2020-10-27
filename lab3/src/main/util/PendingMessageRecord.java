package main.util;

import java.net.DatagramPacket;

public class PendingMessageRecord {
    private Long firstTimeStamp;
    private Long lastTimeStamp;
    private DatagramPacket sendingBytes;
    public PendingMessageRecord(Long firstTimeStamp, DatagramPacket sendingBytes) {
        this.firstTimeStamp = firstTimeStamp;
        this.lastTimeStamp = firstTimeStamp;
        this.sendingBytes = sendingBytes;
    }

    public PendingMessageRecord(DatagramPacket sendingBytes) {
        this.sendingBytes = sendingBytes;
    }

    public PendingMessageRecord(Long firstTimeStamp) {
        this.firstTimeStamp = firstTimeStamp;
        this.lastTimeStamp = firstTimeStamp;
    }

    public void setLastTimeStamp(Long lastTimeStamp) {
        this.lastTimeStamp = lastTimeStamp;
    }
    public Long getFirstTimeStamp() {
        return firstTimeStamp;
    }
    public Long getLastTimeStamp() {
        return lastTimeStamp;
    }
    public DatagramPacket getPacket() {
        return sendingBytes;
    }
}
