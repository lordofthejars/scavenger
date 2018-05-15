package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;

import static me.escoffier.keynote.messages.Frame.TYPE.PING;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class PingFrame extends Frame {

    public static final JsonObject INSTANCE = new JsonObject().put("type", PING.getName());

    public PingFrame() {
        super(PING);
    }

    @Override
    public JsonObject toJson() {
        return INSTANCE;
    }
}
