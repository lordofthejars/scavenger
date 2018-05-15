package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.core.http.ServerWebSocket;
import io.vertx.reactivex.ext.healthchecks.HealthCheckHandler;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import me.escoffier.keynote.endpoints.*;
import me.escoffier.keynote.messages.Frame;
import me.escoffier.keynote.messages.GoneFrame;
import me.escoffier.keynote.messages.PingFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static me.escoffier.keynote.Constants.ADDRESS_PLAYERS;
import static me.escoffier.keynote.WebSocketHelper.closeQuietly;
import static me.escoffier.keynote.WebSocketHelper.toJson;


public class GameServer extends AbstractVerticle implements Handler<ServerWebSocket> {

    private static final Logger LOGGER = LogManager.getLogger("GameServer");

    private Map<String, ServerWebSocket> sockets = new HashMap<>();

    private AdminEndpoint admin;

    @Override
    public void start(Future<Void> future) {
        String cn = config().getString("data-center", "localhost");

        TaskEndpoint tm = new TaskEndpoint(vertx, config());
        PlayerEndpoint pe = new PlayerEndpoint(vertx, config());
        ScoreEndpoint se = new ScoreEndpoint(vertx);
        MetricEndpoint me = new MetricEndpoint(vertx, config());
        LeaderBoardEndpoint lbe = new LeaderBoardEndpoint(vertx, config());
        TransactionEndpoint te = new TransactionEndpoint(vertx, config());
        admin = new AdminEndpoint(vertx, config());
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        router.route().handler(CorsHandler.create("*"));

        router.get("/health").handler(HealthCheckHandler.create(vertx)
            .register("http-server-started", f -> f.complete(Status.OK()))
            .register("infinispan-connection", admin::check)
        );

        router.get("/admin/players").handler(pe::getPlayers);
        router.get("/admin/players/active").handler(pe::getActivePlayerCount);
        router.get("/admin/players/:id").handler(pe::getPlayer);

        router.post("/score").handler(se::addScore);

        router.get("/admin/tasks").handler(tm::getTasks);
        router.post("/admin/tasks").handler(tm::addTask);
        router.put("/admin/tasks/:taskId").handler(tm::editTask);
        router.delete("/admin/tasks/:taskId").handler(tm::deleteTask);

        ResetEndpoint resetEndpoint = new ResetEndpoint(vertx, config());
        CheckEndpoint checkEndpoint = new CheckEndpoint(vertx, config());
        router.get("/admin/metrics").handler(me::get);
        router.get("/admin/checks").handler(checkEndpoint::check);
        router.get("/admin/metrics/prometheus").handler(me::prometheus);
        router.get("/admin/metrics/clear").handler(me::clear);
        router.get("/admin/reset").handler(resetEndpoint::reset);
        router.get("/admin/play").handler(resetEndpoint::play);
        router.get("/admin/states").handler(admin::getStates);

        router.get("/fake/rank").handler(lbe::getFakeLeaderBoard);
        router.get("/leaderboard").handler(lbe::getLeaderBoard);


        if (!cn.equals("localhost")) {
            LOGGER.info("Configuring static handler to use built bundle");
            router.get("/*")
                .handler(StaticHandler.create("webroot/build/es5-bundled")
                    .setCachingEnabled(true)
                    .setFilesReadOnly(true));
        } else {
            LOGGER.info("Configuring static handler to use dev mode");
            router.get("/*").handler(StaticHandler.create().setCachingEnabled(false));
        }

        HttpServerOptions options = new HttpServerOptions();
        options
          .setMaxWebsocketFrameSize(10048576)
          .setCompressionSupported(true);

        Completable server = vertx
            .createHttpServer(options)
            .requestHandler(router::accept)
            .websocketHandler(this)
            .rxListen(8080)
            .doOnSuccess(s -> LOGGER.info("Server started on port " + s.actualPort()))
            .toCompletable();

        vertx.setPeriodic(20000, l -> sendPing());

        tm.init()
            .andThen(admin.init())
            .andThen(me.init())
            .andThen(pe.init())
            .andThen(lbe.init())
            .andThen(te.init())
            .andThen(server)
            .subscribe(CompletableHelper.toObserver(future));
    }

    private void sendPing() {
        publish(PingFrame.INSTANCE);
    }

    public void publish(JsonObject json) {
        String encoded = json.encode();
        Collection<ServerWebSocket> values = sockets.values();
        values.forEach(socket -> {
            try {
                socket.writeFinalTextFrame(encoded);
            } catch (Exception e) {
                // May have been closed, ignore.
            }
        });
    }

    @Override
    public void handle(ServerWebSocket socket) {
        if (socket.path().equals("/game")) {
            LOGGER.info("Player connection received");
            onPlayerConnection(socket);
            return;
        }
        if (socket.path().equals("/admin")) {
            LOGGER.info("Admin connection received");
            admin.onAdminConnection(socket);
            return;
        }

        socket.reject(400);
    }

    private void onPlayerConnection(ServerWebSocket socket) {
        manageWebSocket(ADDRESS_PLAYERS, socket);
    }

    private void manageWebSocket(String address, ServerWebSocket socket) {
        socket
            .exceptionHandler(t -> LOGGER.error("An exception has been caught in the web socket " +
                "during the connection sequence: {}", t.getMessage()))
            .frameHandler(frame -> {
                // This frame handler is only there for the initial message (registration),
                // then another frame handler will replace it.

                // Registration...
                JsonObject json = toJson(frame);
                if (json == null) {
                    LOGGER.info("Receiving an empty frame instead of a connection request, closing");
                    closeQuietly(socket);
                    return;
                }

                if (!Frame.TYPE.CONNECTION.getName().equalsIgnoreCase(json.getString("type"))) {
                    LOGGER.info("Receiving an invalid connection frame, closing");
                    closeQuietly(socket);
                    return;
                }

                // Connection frame validated
                // Initializing communication protocol

                vertx.eventBus().<JsonObject>rxSend(address, json,
                    new DeliveryOptions().setSendTimeout(30000))
                    .doOnError(err -> {
                        LOGGER.error("Rejecting web socket connection", err);
                        socket.close((short) 400, "Bad socket " + err.getMessage());
                        closeQuietly(socket);
                    })
                    .doOnSuccess(msg -> {
                        JsonObject configuration = msg.body();
                        String id = configuration.getString("playerId");
                        String playerAddress = id + "/message";
                        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(id);

                        socket
                            .frameHandler(innerframe -> {
                                JsonObject message = toJson(innerframe);
                                if (message == null) {
                                    return;
                                }
                                if (Frame.TYPE.PING.getName().equalsIgnoreCase(message.getString("type"))) {
                                    // Ignoring ping frame
                                    return;
                                }
                                vertx.eventBus().send(playerAddress, message);
                            })
                            .exceptionHandler(t -> {
                                LOGGER.error("An exception has been caught in the web socket " +
                                    "for player {}, closing ({})", id, t.getMessage());
                                closeQuietly(socket);
                                cleanup(id, playerAddress, consumer);
                            })
                            .closeHandler(v -> cleanup(id, playerAddress, consumer));

                        // Register the consumer receiving message from the game verticle to write to the socket
                        consumer.handler(message -> {
                            // Message sent from the game verticle to be transferred to the web socket
                            try {
                                socket.writeFinalTextFrame(message.body().encode());
                            } catch (Exception e) {
                                // Socket closed.
                                LOGGER.error("Socket closed?", e);
                                cleanup(id, playerAddress, consumer);
                                closeQuietly(socket);
                            }
                        }).completionHandler(x -> {
                            sockets.put(configuration.getString("playerId"), socket);
                            // Everything is setup, send the config to the user.
                            socket.writeFinalTextFrame(configuration.encode());
                        });
                    })
                    .subscribe(
                        msg -> {},
                        err -> LOGGER.error("Error during GameServer <-> PlayerVerticle communication", err)
                    );
            });
    }


    private void cleanup(String playerId, String address, MessageConsumer<JsonObject> consumer) {
        sockets.remove(playerId);
        consumer.unregister();
        vertx.eventBus().send(address, GoneFrame.GONE.toJson());
    }

}
