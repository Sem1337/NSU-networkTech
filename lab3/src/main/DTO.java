package main;

import java.io.Serializable;
import java.util.UUID;

public class DTO implements Serializable {

    private MessageType messageType;
    private MessageHeader header;
    private UUID id = UUID.randomUUID();
    private String data;
    private String senderName;


    DTO(MessageHeader header, String data, String senderName, MessageType messageType) {
        this.header = header;
        this.data = data;
        this.senderName = senderName;
        this.messageType = messageType;
    }

    public void setId(UUID id) {
        this.id = id;
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

    UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return id.toString() + ": (header:" + header + ", from: " + senderName + "): " + data;
    }

}
