package main;

public class NewDelegateDTO extends DTO {

    private ChatNode.Neighbour delegate;

    NewDelegateDTO(String data, String senderName, ChatNode.Neighbour delegate) {
        super(MessageHeader.NEW_DELEGATE, data, senderName, MessageType.REQUEST);
        this.delegate = delegate;
    }

    ChatNode.Neighbour getDelegate() {
        return delegate;
    }

}
