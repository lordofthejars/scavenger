package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import me.escoffier.keynote.Constants;
import me.escoffier.keynote.Player;
import me.escoffier.keynote.UserNameGenerator;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class LeaderBoardEndpoint extends Endpoint {


    private final WebClient client;
    private AsyncCache<String, Player> players;

    private static final Logger LOGGER = LogManager.getLogger("LeaderBoardEndpoint");
    private JsonArray last;
    private final String url;


    public LeaderBoardEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);

        JsonObject conf = Constants.getLocationAwareConfig(config);

        url = conf.getString("leader-board-url", "http://localhost:8080/fake/rank");
        client = WebClient.create(vertx);
    }

    public Completable init() {
        return CacheService.get(vertx, config)
            .flatMap(c -> c.<String, Player>getIndexedCache(Constants.CACHE_PLAYERS))
            .doOnSuccess(c -> players = c)
            .toCompletable();
    }

    private static final Player STUB = new Player("", "");

    /**
     * Should not be used in production - deliver fake data
     * @param rc the context
     */
    public void getFakeLeaderBoard(RoutingContext rc) {
        String playerId = rc.queryParams().get("playerId");
        JsonObject json = new JsonObject();
        Random random = new Random();
        if (last == null) {
            last = new JsonArray();
            int score = 100;
            for (int i = 0; i < 10; i++) {
                last.add(new JsonObject()
                    .put("name", UserNameGenerator.generate())
                    .put("score", score)
                    .put("playerId", UUID.randomUUID().toString()));
                score = score - 5;
            }
        } else {
            // Simulate some changes.
            for (int i = 0; i < 3; i++) {
                int selected = random.nextInt(10);
                last.getJsonObject(selected).put("name", UserNameGenerator.generate());
            }
        }

        json.put("top10", last)
            .put("currentPlayers", random.nextInt(1000));

        if (playerId != null) {
            json.put("rank", random.nextInt(1000));
        }

        rc.response().end(json.encode());
    }

    public void getLeaderBoard(RoutingContext rc) {
        String playerId = rc.queryParams().get("playerId");

        Single<Optional<Player>> maybe;
        if (playerId != null) {
            maybe = players.get(playerId)
                .toSingle(STUB)
                .map(p -> p == STUB ? Optional.empty() : Optional.of(p));
        } else {
            maybe = Single.just(Optional.empty());
        }

        HttpServerResponse response = rc.response();
        maybe.flatMap(maybePlayer -> {
            String q = maybePlayer.map(p -> "/" + p.id()).orElse("/not-an-id");
            String uri = url + q;
            LOGGER.info("Retrieving ranks from " + uri);
            return client.getAbs(uri)
                .rxSend()
                .map(HttpResponse::bodyAsJsonObject)
                .map(json -> {
                    JsonObject result = new JsonObject();
                    if (maybePlayer.isPresent()) {
                        result.put("rank", json.getInteger("rank", -1));
                        Player player = maybePlayer.get();
                        result.put("playerId", player.id());
                        result.put("playerName", player.name());
                        result.put("score", player.score());
                    }
                    result.put("scores", json.getJsonArray("top10"));
                    result.put("active-players", json.getLong("currentPlayers", 1L));

                    return result;
                });
        })
            .subscribe(json -> response.end(json.encode()),
                err -> {
                    LOGGER.error("Unable to call the leaderboard service", err);
                    response.setStatusCode(500).end("Unable to call the leaderboard service: " + err.getMessage());
                });
    }


}
