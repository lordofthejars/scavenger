package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class GoneFrame extends Frame {

    public static GoneFrame GONE = new GoneFrame();
    private JsonObject json = new JsonObject().put("type", TYPE.GONE.getName());

    private GoneFrame() {
        super(TYPE.GONE);
    }

    @Override
    public JsonObject toJson() {
        return json;
    }
}
