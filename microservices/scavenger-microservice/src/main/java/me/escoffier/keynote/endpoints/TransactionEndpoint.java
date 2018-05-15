package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.RoutingContext;
import me.escoffier.keynote.Constants;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class TransactionEndpoint extends Endpoint {
    private AsyncCache<String, String> cache;

    public TransactionEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
    }

    public Completable init() {
        return CacheService.get(vertx, config)
            .flatMap(c -> c.<String, String>getCache(Constants.CACHE_TRANSACTIONS))
            .doOnSuccess(c -> cache = c)
            .toCompletable();
    }

    public void dump(RoutingContext rc) {
        cache.all()
            .map(map -> {
                JsonObject json = new JsonObject();
                map.forEach(json::put);
                return json;
            })
            .map(Json::encodePrettily)
            .subscribe(content -> rc.response().end(content));
    }
}
