package main.util;

import java.util.Objects;
import java.util.UUID;

public class MessageUniqueRecord {
    private UUID dtoID;
    private UUID receiverID;
    public MessageUniqueRecord(UUID dtoID, UUID nodeID) {
        this.dtoID = dtoID;
        this.receiverID = nodeID;
    }
    public UUID getDtoID() {
        return dtoID;
    }
    public UUID getReceiverID() {
        return receiverID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageUniqueRecord that = (MessageUniqueRecord) o;
        return Objects.equals(dtoID, that.dtoID) &&
                Objects.equals(receiverID, that.receiverID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dtoID, receiverID);
    }
}
