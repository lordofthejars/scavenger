package me.escoffier.keynote.messages;

import io.vertx.core.json.JsonObject;
import me.escoffier.keynote.ActivePlayerVerticle;

/**
 * Message used to notify the {@link ActivePlayerVerticle} when a player arrives or leaves the game.
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class PlayerEventMessage {

    private final String playerId;
    private final PlayerEvent event;

    public enum PlayerEvent {
        ARRIVAL,
        DEPARTURE
    }

    public PlayerEventMessage(String playerId, PlayerEvent event) {
        this.playerId = playerId;
        this.event = event;
    }

    public static PlayerEventMessage fromJson(JsonObject json) {
        return new PlayerEventMessage(
            json.getString("playerId"),
            PlayerEvent.valueOf(json.getString("event").toUpperCase())
        );
    }

    public JsonObject toJson() {
        return new JsonObject()
            .put("playerId", playerId)
            .put("event", event.name());
    }

    public String player() {
        return playerId;
    }

    public PlayerEvent event() {
        return event;
    }
}
