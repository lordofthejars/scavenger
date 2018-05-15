package com.redhat.summit2018.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import java.util.Optional;

import static com.redhat.summit2018.services.Env.getOrDefault;

/**
 * Manage the caches.
 */
public class Cache {

    private static final Logger LOGGER = LogManager.getLogger("Cache-Service");
    private static final JsonParser PARSER = new JsonParser();

    private final static RemoteCacheManager manager = createCacheManager();

    private final static RemoteCache<String, String> TXS = manager.getCache(getOrDefault("jdg-transaction-cache", "txs"));

    private final static RemoteCache<String, String> TASKS = manager.getCache(getOrDefault("jdg-tasks-cache", "tasks"));

    private final static RemoteCache<String, String> OBJECTS = manager.getCache(getOrDefault("jdg-object-cache",
        "objects"));

    public static Optional<JsonObject> getTransaction(String tx) {
        String content = TXS.get(tx);
        if (content == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(PARSER.parse(content).getAsJsonObject());
        }
    }

    public static Optional<JsonObject> getTask(String id) {
        String content = TASKS.get(id);
        if (content == null) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(PARSER.parse(content).getAsJsonObject());
        }
    }

    private static RemoteCacheManager createCacheManager() {
        String url = getOrDefault("jdg-url", "localhost");
        int port = getOrDefault("jdg-port", 11222);
        ConfigurationBuilder cb = new ConfigurationBuilder();
        LOGGER.info("Infinispan location: {}:{}", url, port);
        cb.addServer()
            .host(url)
            .port(port);
        return new RemoteCacheManager(cb.build());
    }


    public static void save(String key, JsonObject json) {
        // TODO Rename to results when the cache is renamed.
        long time = System.currentTimeMillis();
        json.addProperty("function-b-exit-time", time);
        json.addProperty("function-b-duration-time-ms",
            time - json.get("function-b-entry-time").getAsLong());
        LOGGER.info("Saving result to cache at {}, {}", time, json.toString());
        OBJECTS.put(key, json.toString());
    }
}
