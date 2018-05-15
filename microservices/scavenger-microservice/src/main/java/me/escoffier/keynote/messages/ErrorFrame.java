package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;

/**
 * Frames sent to the game when an error is detected.
 */
public class ErrorFrame extends Frame {

    private final int status;
    private final String message;
    private final String transactionId;

    public ErrorFrame(int status, String message, String transactionId) {
        super(TYPE.ERROR);
        this.status = status;
        this.message = message;
        this.transactionId = transactionId;
    }

    public ErrorFrame(int status, String message) {
        this(status, message, null);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject().put("type", TYPE.ERROR.getName()).put("code", status).put("message", message);
        if (transactionId != null) {
            json.put("transactionId", transactionId);
        }
        return json;
    }
}
