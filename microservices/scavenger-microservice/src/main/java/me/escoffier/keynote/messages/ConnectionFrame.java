package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ConnectionFrame extends Frame {
    private final String id;
    private final String token;
    private final String name;
    private final String email;

    public ConnectionFrame(String id, String token, String name, String email) {
        super(TYPE.CONNECTION);
        this.id = id;
        this.token = token;
        this.name = name;
        this.email = email;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json = type.inject(json);
        if (id != null) {
            json.put("playerId", id);
        }
        if (token != null) {
            json.put("token", token);
        }
        if (name != null) {
            json.put("name", name);
        }
        if (email != null) {
            json.put("email", email);
        }
        return json;
    }

    public boolean hasId() {
        return id != null;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    public boolean hasEmail() {
        return email != null;
    }

    public String email() {
        return email;
    }

    public static ConnectionFrame fromJson(JsonObject json) {
        String id = json.getString("playerId");
        String token = json.getString("token");
        String name = json.getString("name");
        String email = json.getString("email");
        return new ConnectionFrame(id, token, name, email);
    }
}
