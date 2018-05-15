package me.escoffier.keynote.messages;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import me.escoffier.keynote.Player;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ConfigurationFrame extends Frame {
    private final Player player;

    private final String dcName;
    private final JsonObject tasks;

    public ConfigurationFrame(Player player, String dcName, JsonObject tasks) {
        super(TYPE.CONFIGURATION);
        this.player = player;
        this.dcName = dcName;
        this.tasks = tasks;
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        return type.inject(json)
            .put("playerId", player.getPlayerId())
            .put("username", player.name())
            .put("data-center", dcName)
            .put("tasks", tasks)
            .put("score", player.getScore())
            .put("achievements", Json.encodeToBuffer(player.achievements()).toJsonArray());
    }
}
