package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static me.escoffier.keynote.Constants.CACHE_TRANSACTIONS;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MetadataRepository {

    private final Vertx vertx;
    private final JsonObject config;
    private final String dc;
    private AsyncCache<String, String> cache;

    private static final Logger LOGGER = LogManager.getLogger("Metadata-Repository");


    public MetadataRepository(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
        this.dc = config.getString("data-center", "localhost");
    }

    public Completable init() {
        return CacheService.get(vertx, config)
            .flatMap(cache -> cache.<String, String>getCache(CACHE_TRANSACTIONS))
            .doOnSuccess(c -> cache = c)
            .doOnSuccess(c -> LOGGER.info("Cache '{}' retrieved", c.name()))
            .toCompletable();
    }

    public Completable save(String playerId, String transactionId, String taskId, JsonObject metadata) {
        metadata.put("playerId", playerId);
        metadata.put("transactionId", transactionId);
        metadata.put("taskId", taskId);
        metadata.put("data-center", dc);
        return cache.put(transactionId, metadata.encode());
    }
}
