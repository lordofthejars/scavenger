package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.ServerWebSocket;
import io.vertx.reactivex.ext.web.RoutingContext;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import me.escoffier.keynote.messages.PingFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

import java.util.Map;
import java.util.Set;

import static me.escoffier.keynote.Constants.CACHE_ADMIN;
import static me.escoffier.keynote.WebSocketHelper.closeQuietly;
import static me.escoffier.keynote.WebSocketHelper.toJson;

/**
 * Implement the game mode logic.
 */
public class AdminEndpoint extends Endpoint {

    private static final Logger LOGGER = LogManager.getLogger("Admin-Endpoint");
    private final String token;

    private AsyncCache<String, String> cache;

    public AdminEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
        this.token = config.getString("admin-socket-token");
    }

    @Override
    public Completable init() {
        return
            CacheService.get(vertx, config)
                .flatMap(cache -> cache.<String, String>getCache(CACHE_ADMIN))
                .flatMap(c ->
                    // Initialization
                    c.put("party", "true").andThen(c.put("game", "lobby")).toSingleDefault(c)
                )
                .doOnSuccess(s -> cache = s)
                .toCompletable();
    }

    private void setPartyMode(String newState) {
        LOGGER.info("Toggle party island to {}", newState);
        cache.put("party", newState)
            .subscribe(
                () -> LOGGER.info("Party mode set to {}", newState),
                err -> LOGGER.error("Unable to set the party mode", err)
            );
    }

    private void setGameMode(String newState) {
        LOGGER.info("Game mode to {}", newState);
        cache.put("game", newState)
            .subscribe(
                () -> LOGGER.info("Game mode set to {}", newState),
                err -> LOGGER.error("Unable to set the game mode", err)
            );
    }

    public static Single<JsonObject> extend(AsyncCache<String, String> cache, JsonObject config) {
        Single<String> party = cache.get("party").toSingle("true");
        Single<String> game = cache.get("game").toSingle("lobby");

        return party.zipWith(game,
            (p, g) -> config.put("party", p).put("game", g)
        );
    }

    public void onAdminConnection(ServerWebSocket socket) {
        String encode = PingFrame.INSTANCE.encode();
        long timer = vertx.setPeriodic(20000, l -> socket.writeFinalTextFrame(encode));
        socket
            .closeHandler(
                v -> vertx.cancelTimer(timer)
            )
            .exceptionHandler(t -> {
                vertx.cancelTimer(timer);
                closeQuietly(socket);
            })
            .frameHandler(frame -> {
                JsonObject json = toJson(frame);
                if (json == null) {
                    LOGGER.info("Receiving an empty frame instead of a connection request, closing");
                    closeQuietly(socket);
                    return;
                }
                String token = json.getString("token");
                if (token == null || !token.equals(this.token)) {
                    LOGGER.info("Bad token, closing");
                    closeQuietly(socket);
                    return;
                }

                LOGGER.info("Handling admin frame: {}", json.encode());
                String type = json.getString("type");
                switch (type) {
                    case "party-island":
                        // true | false
                        setPartyMode(json.getString("enabled"));
                        break;
                    case "game":
                        // game state: lobby, play, pause, game-over
                        setGameMode(json.getString("state"));
                        break;
                    case "ping":
                        // Ignore ping
                        break;
                    default:
                        LOGGER.info("No type, closing quietly");
                        closeQuietly(socket);
                        break;
                }
            });

    }

    public void getStates(RoutingContext rc) {
        cache.all()
            .map(map -> {
                JsonObject result = new JsonObject();
                map.forEach(result::put);
                return result;
            }).subscribe(
            json -> rc.response().end(json.encodePrettily()),
            rc::fail
        );
    }

    public void check(Future<Status> future) {
        long begin = System.currentTimeMillis();
        cache.all()
            .subscribe(
                map -> {
                    long duration = System.currentTimeMillis() - begin;
                    // states cannot be null or empty
                    Set<Map.Entry<String, String>> set = map.entrySet();
                    for (Map.Entry<String, String> entry : set) {
                        String k = entry.getKey();
                        String v = entry.getValue();
                        if (Strings.isBlank(k)) {
                            future.tryComplete(Status.KO(new JsonObject()
                                .put("failure", "invalid result from the server, '" + k + "' is blank")));
                            return;
                        }
                        if (Strings.isBlank(v)) {
                            future.tryComplete(Status.KO(new JsonObject()
                                .put("failure", "invalid result from the server, value for '" + k + "' is blank")));
                            return;
                        }
                    }
                    future.tryComplete(Status.OK().setData(new JsonObject().put("duration-in-ms", duration)));
                },
                err -> future.tryComplete(Status.KO(new JsonObject()
                    .put("failure", "unable to access the cache server")
                    .put("error", err.getMessage())))
            );
    }
}
