package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;

import java.util.Objects;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class PictureFrame extends Frame {


    private final String playerId;
    private final String taskId;
    private final byte[] picture;
    private final JsonObject metadata;
    private final String transactionId;

    public PictureFrame(String playerId, String taskId, String transactionId, byte[] picture, JsonObject metadata) {
        super(TYPE.PICTURE);
        this.playerId = playerId;
        this.transactionId = transactionId;
        this.taskId = taskId;
        this.picture = picture;
        this.metadata = metadata.copy();
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        return type.inject(json)
            .put("playerId", playerId)
            .put("taskId", taskId)
            .put("picture", picture)
            .put("transactionId", transactionId)
            .put("metadata", metadata);
    }

    public String playerId() {
        return playerId;
    }

    public String taskId() {
        return taskId;
    }

    public String transactionId() {
        return transactionId;
    }

    public byte[] picture() {
        return picture;
    }

    public JsonObject metadata() {
        return metadata;
    }

    public static PictureFrame fromJson(JsonObject json) {
        String playerId = Objects.requireNonNull(json.getString("playerId"), "PlayerId must be set");
        String taskId = Objects.requireNonNull(json.getString("taskId"), "TaskId must be set");
        String transactionId = Objects.requireNonNull(json.getString("transactionId"), "TransactionId must be set");
        byte[] picture = Objects.requireNonNull(json.getBinary("picture"), "Picture must be set");
        JsonObject metadata = Objects.requireNonNull(json.getJsonObject("metadata"), "Metadata must be set");
        return new PictureFrame(playerId, taskId, transactionId, picture, metadata);
    }
}
