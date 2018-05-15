package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.RoutingContext;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

import static me.escoffier.keynote.Constants.CACHE_ADMIN;
import static me.escoffier.keynote.scripts.ClearAllCaches.NAMES;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ResetEndpoint extends Endpoint {

    private static final Logger LOGGER = LogManager.getLogger("Reset-Endpoint");

    public ResetEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
    }

    public void play(RoutingContext rc) {
        CacheService.get(vertx, config)
            .flatMap(cache -> cache.<String, String>getCache(CACHE_ADMIN))
            .flatMapCompletable(c -> c.put("game", "play"))
            .subscribe(
                () -> rc.response().end("Play!"),
                rc::fail
            );
    }

    public void reset(RoutingContext rc) {
        LOGGER.info("Resetting...");
        
        ConfigRetriever retriever = ConfigRetriever.create(vertx);

        retriever
            .rxGetConfig()
            .flatMap(json -> CacheService.get(vertx, json))
            .flatMapCompletable(caches ->
                Observable.fromArray(NAMES)
                    .flatMapCompletable(name -> caches.getCache(name)
                        .doOnSuccess(c -> System.out.println("Clearing cache " + c.name()))
                        .flatMapCompletable(AsyncCache::clear)
                        .doOnComplete(() -> System.out.println("Cache " + name + " cleared"))))

            .andThen(resetGameState())
            .subscribe(
                () -> {
                    LOGGER.info("The cache {} have been cleared, game state set fo defaults", Arrays.toString(NAMES));
                    rc.response().end("OK - ready player one");
                },
                err -> {
                    LOGGER.error("Unable to reset the game: {}", err.getMessage(), err);
                    rc.fail(500);
                }
            );
    }

    private Completable resetGameState() {
        return CacheService.get(vertx, config)
            .flatMap(cache -> cache.<String, String>getCache(CACHE_ADMIN))
            .flatMap(c ->
                c.put("party", "true").andThen(c.put("game", "lobby")).toSingleDefault(c)
            )
            .toCompletable();
    }
}
