package me.escoffier.keynote;

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public interface Constants {

    String CACHE_PLAYERS = "players";
    String CACHE_ACTIVE_PLAYERS = "active";
    String CACHE_TASKS = "tasks";
    String CACHE_TRANSACTIONS = "txs";
    String CACHE_ADMIN = "admin";
    
    String ADDRESS_PLAYERS = "players";
    String ADDRESS_ACTIVE = "active-players";
    String ADDRESS_TASKS = "tasks";
    String ADDRESS_ADMIN_CHANGES = "admin-changes";



    static JsonObject getLocationAwareConfig(JsonObject config) {
        String cn = config.getString("data-center", "localhost");
        return config.getJsonObject("locations").getJsonObject(cn, new JsonObject());
    }
}
