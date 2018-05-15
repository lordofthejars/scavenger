package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public abstract class Frame {

    public Frame(TYPE type) {
        this.type = type;
    }

    public enum TYPE {
        CONNECTION("connection"),
        PICTURE("picture"),
        CONFIGURATION("configuration"),
        GONE("gone"),
        PING("ping"),
        SCORE("score"),
        ERROR("error");

        private final String name;

        TYPE(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public static TYPE getByName(String type) {
            return TYPE.valueOf(type.toUpperCase());
        }

        public JsonObject inject(JsonObject input) {
            return input.put("type", name);
        }
    }

    protected final TYPE type;

    public abstract JsonObject toJson();

}
