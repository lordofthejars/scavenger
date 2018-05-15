package me.escoffier.keynote.scripts;

import io.reactivex.Observable;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;
import me.escoffier.keynote.Constants;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;

import java.util.Arrays;

/**
 * Clears all the caches
 */
public class ClearAllCaches extends AbstractVerticle {

    public static final String[] NAMES = new String[]{
        Constants.CACHE_ACTIVE_PLAYERS,
        // Do not reset tasks on purpose
//        Constants.CACHE_TASKS,
        Constants.CACHE_PLAYERS,
        Constants.CACHE_TRANSACTIONS
//        "objects"
    };

    @Override
    public void start() {
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
            .subscribe(
                () -> {
                    System.out.println("The cache " + Arrays.toString(NAMES) + " have been cleared");
                    System.exit(0);
                },
                err -> {
                    err.printStackTrace();
                    System.exit(-1);
                }
            );
    }
}
