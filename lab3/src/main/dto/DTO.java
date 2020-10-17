package main.dto;

import main.MessageHeader;
import main.MessageType;

import java.io.Serializable;
import java.util.UUID;

public class DTO implements Serializable {

    private MessageType messageType;
    private MessageHeader header;
    private UUID id = UUID.randomUUID();
    private String data;
    private String senderName;
    private String receiverName;
    private UUID senderID;


    public DTO(MessageHeader header, MessageType messageType, String senderName, UUID senderID, String data, String receiverName) {
        this.header = header;
        this.data = data;
        this.senderName = senderName;
        this.messageType = messageType;
        this.senderID = senderID;
        this.receiverName = receiverName;
    }


    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSenderID() {
        return senderID;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public void setSenderID(UUID senderID) {
        this.senderID = senderID;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return data;
    }

    public String getSenderName() {
        return senderName;
    }


    public void setSenderName(String name) {
        senderName = name;
    }

    public MessageHeader getHeader() {
        return header;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return id.toString() + ": (header:" + header + ", from: " + senderName + " to: " + receiverName + "): " + data;
    }

}
