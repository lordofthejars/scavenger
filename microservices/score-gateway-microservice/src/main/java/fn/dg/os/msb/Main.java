package fn.dg.os.msb;

import hu.akarnokd.rxjava2.interop.CompletableInterop;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.disposables.Disposable;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.bridge.BridgeEventType;
import io.vertx.ext.bridge.PermittedOptions;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.core.http.ServerWebSocket;
import io.vertx.reactivex.ext.healthchecks.HealthCheckHandler;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.sockjs.SockJSHandler;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.Search;
import org.infinispan.client.hotrod.annotation.ClientCacheEntryCreated;
import org.infinispan.client.hotrod.annotation.ClientListener;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.event.ClientCacheEntryCustomEvent;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.commons.util.KeyValueWithPrevious;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.annotations.ProtoSchemaBuilder;
import org.infinispan.query.dsl.Query;
import org.infinispan.query.dsl.QueryFactory;
import org.infinispan.query.dsl.SortOrder;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.infinispan.query.dsl.Expression.count;

public class Main extends AbstractVerticle implements Handler<ServerWebSocket> {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private RemoteCacheManager playerRemote; // protostream marshaller
    private RemoteCacheManager scoreRemote; // normal marshaller
    private RemoteCache<String, Player> playerCache;
    private RemoteCache<String, Player> testPlayerCache;
    private RemoteCache<String, String> activeCache;

    // Use String values to avoid:
    // java.lang.ClassNotFoundException: fn.dg.os.vertx.player.Score from [Module "org.infinispan.server.hotrod"
    private RemoteCache<String, String> scoreCache;
    private RemoteCache<String, String> testScoreCache;

    private long playerTimer;
    private long scoreTimer;

    private AtomicInteger playerId = new AtomicInteger(1);

    private ScoreListener listener = new ScoreListener("image-scores");
    private ScoreListener testListener = new ScoreListener("test-image-scores");


    @Override
    public void start(io.vertx.core.Future<Void> future) {
        Router router = Router.router(vertx);

        HealthCheckHandler hc = HealthCheckHandler.create(vertx)
            .register("infinispan", this::checkCommunicationWithInfinispan)
            .register("http", f -> f.complete(Status.OK()));
        router.get("/health").handler(hc);

        router.get("/test/inject").handler(this::inject);
        router.get("/test/inject/stop").handler(this::injectStop);

        router.get("/test/leaderboard").handler(this.getTestLeaderboard());
        router.get("/leaderboard").handler(this.getLeaderboard());

        router.get("/scores/*").handler(sockJSHandler(vertx));
        router.get("/rank/:name").handler(this::getRank);


        vertx
            .rxExecuteBlocking(this::remoteCacheManager)
            .flatMap(x -> vertx.rxExecuteBlocking(playerCache()))
            .flatMap(x -> vertx.rxExecuteBlocking(testPlayerCache()))
            .flatMap(x -> vertx.rxExecuteBlocking(scoreCache()))
            .flatMap(x -> vertx.rxExecuteBlocking(testScoreCache()))
            .flatMap(x -> vertx.rxExecuteBlocking(activeCache()))
            .flatMap(x ->
                vertx
                    .createHttpServer()
                    .websocketHandler(this)
                    .requestHandler(router::accept)
                    .rxListen(8080))
            .subscribe(
                server -> {
                    LOGGER.info("Caches retrieved and HTTP server started");
                    future.complete();
                }
                , future::fail
            );
    }

    private void checkCommunicationWithInfinispan(Future<Status> future) {
        Single<Integer> s1 = vertx.rxExecuteBlocking(
            f -> f.complete(playerCache.size())
        );

        Single<Integer> s2 = vertx.rxExecuteBlocking(
            f -> f.complete(activeCache.size())
        );

        s1.zipWith(s2, (i1, i2) ->
            Status.OK().setData(new JsonObject()
                .put("players", i1)
                .put("active", i2)
            ))
            .onErrorReturn(err -> Status.KO().setData(new JsonObject()
                .put("failure", "Unable to retrieve data form cache")
                .put("err", err.getMessage())
            ))
            .subscribe(
                status -> future.complete(status),
                err -> future.complete(Status.KO().setData(
                    new JsonObject().put("error", err.getMessage()))
                )
            );


    }

    @Override
    public void stop(io.vertx.core.Future<Void> future) {
        vertx.cancelTimer(playerTimer);
        vertx.cancelTimer(scoreTimer);

        vertx
            .rxExecuteBlocking(this::removeScoreListener)
            .flatMap(x -> vertx.rxExecuteBlocking(stopRemote(playerRemote)))
            .flatMap(x -> vertx.rxExecuteBlocking(stopRemote(scoreRemote)))
            .subscribe(
                server -> {
                    LOGGER.info("Removed listener and stopped remotes");
                    future.complete();
                }
                , future::fail
            );
    }

    private void inject(RoutingContext rc) {
        clearTestCaches()
            .subscribe(
                () -> {
                    Random r = new Random();

                    playerTimer = vertx.setPeriodic(2000, id -> {
                        final int playerId = this.playerId.getAndIncrement();
                        final String name = "player" + playerId;
                        final Player player = new Player(String.valueOf(playerId), name);

                        int score = r.nextInt(1000) + 100; // 3 digit number
                        player.setScore(score);

                        player.addAchievement(
                            new Achievement("aaa", "bbb", 1));

                        LOGGER.info(String.format("test put(value=%s)", player));
                        testPlayerCache.putAsync(name, player);
                    });

                    DecimalFormat df = new DecimalFormat("#.#");
                    scoreTimer = vertx.setPeriodic(1000, id -> {
                        JsonObject scores = new JsonObject();
                        final String url = UUID.randomUUID().toString();
                        scores.put("url", url);

                        int score = (r.nextInt(10) + 1) * 10;
                        scores.put("score", score);

                        final Task task = Task.values()[r.nextInt(Task.values().length)];
                        scores.put("taskName", task.name().toLowerCase());

                        LOGGER.info(String.format("test put(value=%s)", scores));
                        testScoreCache.putAsync(url, scores.toString());
                    });

                    rc.response().end("Injector started");
                }
                , failure ->
                    handleFailure(rc, failure)
            );
    }

    private Handler<RoutingContext> getLeaderboard() {
        return rc -> handleLeaderboard(playerCache, rc, activeCache);
    }

    private Handler<RoutingContext> getTestLeaderboard() {
        return rc -> handleLeaderboard(testPlayerCache, rc, testPlayerCache);
    }

    private Disposable handleLeaderboard(
        RemoteCache<String, Player> playerCache
        , RoutingContext rc
        , RemoteCache<String, ?> activeCache
    ) {
        return vertx
            .rxExecuteBlocking(leaderboard(playerCache, activeCache))
            .subscribe(
                json ->
                    rc.response().end(json.encodePrettily())
                , failure ->
                    handleFailure(rc, failure)
            );
    }

    private void handleFailure(RoutingContext rc, Throwable failure) {
        failure.printStackTrace();
        rc.response().end("Failed: " + failure);
    }

    private void getRank(RoutingContext rc) {
        String playerName = rc.request().getParam("name");

        vertx
            .rxExecuteBlocking(rank(playerName))
            .subscribe(
                json ->
                    rc.response().end(json.encodePrettily())
                , failure ->
                    handleFailure(rc, failure)
            );
    }

    private void injectStop(RoutingContext rc) {
        final boolean cancelledPlayerTimer = vertx.cancelTimer(playerTimer);
        final boolean cancelledScoreTimer = vertx.cancelTimer(scoreTimer);

        if (cancelledPlayerTimer && cancelledScoreTimer) {
            clearTestCaches().subscribe(
                () -> rc.response().end(
                    "Test player and score injectors and cleared stopped"
                )
                , t -> rc.response().end(
                    "Test player and score injectors stopped but failed to clear"
                )
            );
        } else {
            rc.response().end("Player and score injectors not started");
        }
    }

    private Completable clearTestCaches() {
        return CompletableInterop
            .fromFuture(
                testPlayerCache
                    .clearAsync()
                    .thenCompose(x -> testScoreCache.clearAsync())
            );
    }

    private Handler<Future<JsonObject>> leaderboard(
        RemoteCache<String, Player> playerCache
        , RemoteCache<String, ?> activeCache
    ) {
        return f -> f.complete(queryLeaderboard(playerCache, activeCache));
    }

    private Handler<Future<JsonObject>> rank(String playerName) {
        return f -> {
            JsonObject leaders = queryLeaderboard(playerCache, activeCache);
            JsonObject leadersAndRank = queryRank(playerName, leaders, playerCache);
            f.complete(leadersAndRank);
        };
    }

    private static JsonObject queryLeaderboard(
        RemoteCache<String, Player> playerCache
        , RemoteCache<String, ?> activeCache
    ) {
        LOGGER.info("Query leaderboard: ");
        QueryFactory qf = Search.getQueryFactory(playerCache);
        Query query = qf.from(Player.class)
            .orderBy("score", SortOrder.DESC)
            .maxResults(10)
            .build();
        List<Player> list = query.list();

        final JsonObject json = new JsonObject();

        JsonArray top10 = new JsonArray();
        list.forEach(player -> {
            LOGGER.info("Player: " + player);
            top10.add(new JsonObject()
                .put("playerId", player.getPlayerId())
                .put("name", player.getPlayerName())
                .put("score", player.getScore())
                .put("achievements", new JsonArray(player.achievements())));
        });

        json.put("top10", top10);
        // TODO: Current players to be calculated using some other method
        json.put("currentPlayers", activeCache.size());
        CloseableIterator<Map.Entry<Object, Object>> iterator = activeCache.retrieveEntries(null, 1000);
        Map<String, Integer> map = new LinkedHashMap<>();
        iterator.forEachRemaining(entry ->
            map.compute(entry.getValue().toString(), (cloud, count) -> count == null ? 1 : count + 1)
        );

        map.forEach(json::put);

        LOGGER.info("Leaderboard is: " + json);

        return json;
    }

    private static JsonObject queryRank(String playerName, JsonObject json, RemoteCache<String, Player> remoteCache) {
        LOGGER.info("Query rank for player: " + playerName);

        final Player player = remoteCache.get(playerName);

        if (player == null) {
            LOGGER.warning("Unknown player: " + playerName);
            return json;
        }
        
        final int score = player.getScore();
        LOGGER.info("Score for player is: " + score);

        QueryFactory qf = Search.getQueryFactory(remoteCache);
        Query query = qf.from(Player.class)
            .select(count("score"))
            .orderBy("score", SortOrder.DESC)
            .having("score").gt(score)
            .groupBy("score") // TODO: Why needed?
            .build();

        List<Object[]> list = query.list();

        LOGGER.info("Query result: " +
            list.stream()
                .map(Arrays::toString)
                .collect(Collectors.joining(", "))
        );

        final long rank = list.size() + 1;

        LOGGER.info("Rank is: " + rank);

        json.put("rank", rank);
        return json;
    }

    private static Handler<RoutingContext> sockJSHandler(Vertx vertx) {
        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        PermittedOptions outPermit = new PermittedOptions().setAddress("image-scores");
        BridgeOptions options = new BridgeOptions().addOutboundPermitted(outPermit);
        sockJSHandler.bridge(options, be -> {
            if (be.type() == BridgeEventType.REGISTER)
                LOGGER.info("SockJs: client connected");

            be.complete(true);
        });
        return sockJSHandler;
    }

    private void remoteCacheManager(Future<Void> f) {
        this.playerRemote = new RemoteCacheManager(
            new ConfigurationBuilder()
                .addServer()
                //.host("jdg-app-hotrod")
                //.host("infinispan-app-hotrod")
                .host("jdg-app-hotrod.datagrid-stage.svc")
                .port(11222)
                .marshaller(ProtoStreamMarshaller.class)
                .build()
        );

        this.scoreRemote = new RemoteCacheManager(
            new ConfigurationBuilder()
                .addServer()
                //.host("jdg-app-hotrod")
                //.host("infinispan-app-hotrod")
                .host("jdg-app-hotrod.datagrid-stage.svc")
                .port(11222)
                .build()
        );

        SerializationContext serialCtx =
            ProtoStreamMarshaller.getSerializationContext(playerRemote);

        ProtoSchemaBuilder protoSchemaBuilder = new ProtoSchemaBuilder();
        try {
            String playerSchemaFile = protoSchemaBuilder.fileName("player.proto")
                .addClass(Player.class)
                .build(serialCtx);

            RemoteCache<String, String> metadataCache = playerRemote
                .getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);

            metadataCache.put("player.proto", playerSchemaFile);

            f.complete(null);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to auto-generate player.proto", e);
            f.fail(e);
        }
    }

    private Handler<Future<RemoteCache<String, Player>>> playerCache() {
        return f -> {
            final RemoteCache<String, Player> cache = playerRemote.getCache("players");
            this.playerCache = cache;
            f.complete(cache);
        };
    }

    private Handler<Future<RemoteCache<String, Player>>> testPlayerCache() {
        return f -> {
            final RemoteCache<String, Player> cache = playerRemote.getCache("indexed4");
            this.testPlayerCache = cache;
            f.complete(cache);
        };
    }

    private Handler<Future<RemoteCache<String, String>>> scoreCache() {
        return f -> {
            // TODO Rename to results
            final RemoteCache<String, String> cache = scoreRemote.getCache("objects");
            this.scoreCache = cache;
            cache.addClientListener(listener);
            f.complete(cache);
        };
    }

    private Handler<Future<RemoteCache<String, String>>> testScoreCache() {
        return f -> {
            // TODO Rename to results
            final RemoteCache<String, String> cache = scoreRemote.getCache("default4");
            this.testScoreCache = cache;
            cache.addClientListener(testListener);
            f.complete(cache);
        };
    }

    private Handler<Future<RemoteCache<String, String>>> activeCache() {
        return f -> {
            final RemoteCache<String, String> cache = scoreRemote.getCache("active");
            this.activeCache = cache;
            f.complete(cache);
        };
    }

    private Handler<Future<Void>> stopRemote(RemoteCacheManager remote) {
        return f -> {
            remote.stop();
            f.complete(null);
        };
    }

    private void removeScoreListener(Future<Void> f) {
        scoreCache.removeClientListener(listener);
        testScoreCache.removeClientListener(testListener);
        f.complete(null);
    }

    @Override
    public void handle(ServerWebSocket socket) {
        final String path = socket.path();

        LOGGER.info("Socket connection...");

        switch (path) {
            case "/dashboard":
                scoresWebSocket("image-scores", socket);
                break;
            case "/test/dashboard":
                scoresWebSocket("test-image-scores", socket);
                break;
            default:
                socket.reject(400);
                break;
        }
    }

    private void scoresWebSocket(String address, ServerWebSocket socket) {
        MessageConsumer<String> consumer = vertx.eventBus().consumer(address);

        socket
            .exceptionHandler(t -> {
                LOGGER.log(Level.SEVERE, "Error detected when dealing with a web socket - closing", t);
                cleanup(socket, consumer);
            })
            .closeHandler(v -> cleanup(socket, consumer));

        consumer.handler(message -> {
            System.out.println("Write text frame with body: " + message.body());
            socket.writeFinalTextFrame(message.body());
        });
    }

    private void cleanup(ServerWebSocket socket, MessageConsumer<?> consumer) {
        consumer.unregister();
        try {
            socket.close();
        } catch (Exception e) {
            // The socket may have already been closed.
            // Ignore me.
        }
    }


    @ClientListener(converterFactoryName = "key-value-with-previous-converter-factory")
    private final class ScoreListener {

        final String address;

        private ScoreListener(String address) {
            this.address = address;
        }

        @ClientCacheEntryCreated
        @SuppressWarnings("unused")
        public void handleCacheEntryEvent(
            ClientCacheEntryCustomEvent<KeyValueWithPrevious<String, String>> e) {
            System.out.println(e);
            vertx.eventBus().publish(address, toJson(e));
        }

        private String toJson(ClientCacheEntryCustomEvent<KeyValueWithPrevious<String, String>> e) {
            KeyValueWithPrevious<String, String> pair = e.getEventData();
            final JsonObject json = new JsonObject();
            final JsonObject valueAsJson = new JsonObject(pair.getValue());
            json.put("imageURL", valueAsJson.getValue("url"));
            json.put("score", valueAsJson.getValue("score"));
            json.put("taskName", valueAsJson.getValue("taskName"));
            final String encoded = json.encodePrettily();
            System.out.println("Encoded score: " + encoded);
            return encoded;
        }

    }

}
