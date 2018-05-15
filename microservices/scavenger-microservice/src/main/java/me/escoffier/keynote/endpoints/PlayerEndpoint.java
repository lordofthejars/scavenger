package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.RoutingContext;
import me.escoffier.keynote.Player;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;

import java.util.HashMap;
import java.util.Map;

import static me.escoffier.keynote.Constants.CACHE_ACTIVE_PLAYERS;
import static me.escoffier.keynote.Constants.CACHE_PLAYERS;

/**
 * Implementation of the player endpoints
 */
public class PlayerEndpoint extends Endpoint {


    private AsyncCache<String, String> active;
    private AsyncCache<String, Player> players;

    public PlayerEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
    }

    @Override
    public Completable init() {
        return CacheService.get(vertx, config)
            .flatMap(cache -> {
                Single<AsyncCache<String, String>> s1 = cache.getCache(CACHE_ACTIVE_PLAYERS);
                Single<AsyncCache<String, Player>> s2 = cache.getIndexedCache(CACHE_PLAYERS);
                return Single.zip(s1, s2, (c1, c2) -> {
                    this.active = c1;
                    this.players = c2;
                    return c1;
                });
            })
            .toCompletable();
    }

    public void getActivePlayerCount(RoutingContext rc) {
        active.size()
            .subscribe(
                size -> rc.response().end(Json.encode(size)),
                err -> rc.response().setStatusCode(500).end(err.getMessage())
            );
    }

    public void getPlayer(RoutingContext rc) {
        String id = rc.pathParam("id");
        players.get(id)
            .switchIfEmpty(Single.error(new Exception("Player not found")))
            .map(Json::encodePrettily)
            .subscribe(
                json -> rc.response().end(json),
                rc::fail
            );
    }

    public void getPlayers(RoutingContext rc) {
        players.all()
            .map(Map::values)
            .flatMapPublisher(Flowable::fromIterable)
            .toList()
            .map(list -> {
                Map<String, Player> map = new HashMap<>();
                list.forEach(p -> map.put(p.id(), p));
                return map;
            })
            .subscribe(
                map -> rc.response().end(Json.encode(map)),
                err -> rc.response().setStatusCode(500).end(err.getMessage())
            );
    }
}
