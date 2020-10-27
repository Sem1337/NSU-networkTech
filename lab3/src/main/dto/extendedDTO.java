package main.dto;

import main.ChatNode;
import main.MessageHeader;
import main.MessageType;

import java.io.Serializable;
import java.util.UUID;

public class extendedDTO extends DTO implements Serializable {

    private ChatNode.Neighbour delegate;

    public extendedDTO(MessageHeader header, String senderName, UUID senderID, ChatNode.Neighbour delegate, String data, String receiverName) {
        super(header, MessageType.REQUEST, senderName, senderID, data, receiverName);
        this.delegate = delegate;
    }

    public ChatNode.Neighbour getDelegate() {
        return delegate;
    }

}
