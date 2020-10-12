package main;

import java.io.Serializable;
import java.util.UUID;

enum Type {
    RESPONSE,
    REQUEST
}

public class DTO implements Serializable {

    private Type type;
    private String header;
    private UUID id = UUID.randomUUID();
    private String data;
    private String senderName;

    DTO(String header, String data, String senderName, Type type) {
        this.header = header;
        this.data = data;
        this.senderName = senderName;
        this.type = type;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public Type getType() {
        return type;
    }

    public String getMessage() {
        return data;
    }

    private String getSenderName() {
        return senderName;
    }


    public void setSenderName(String name) {
        senderName = name;
    }

    public String getHeader() {
        return header;
    }

    UUID getId() {
        return id;
    }

    @Override
    public String toString() {
        return id.toString() + ": (" + header + " from " + senderName + "): " + data;
    }

}
