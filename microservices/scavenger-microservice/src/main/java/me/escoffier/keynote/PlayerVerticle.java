package me.escoffier.keynote;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.ext.web.client.WebClient;
import me.escoffier.keynote.endpoints.AdminEndpoint;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import me.escoffier.keynote.messages.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static me.escoffier.keynote.Constants.*;
import static me.escoffier.keynote.WebSocketHelper.closeQuietly;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class PlayerVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger("PlayerVerticle");
    /**
     * Map playerId -> Player
     */
    private AsyncCache<String, Player> players;

    /**
     * Map taskId -> object
     */
    private AsyncCache<String, String> tasks;

    /**
     * Map setting -> String
     */
    private AsyncCache<String, String> admin;

    private PictureRepository pictures;
    private MetadataRepository repository;

    private Map<String, PlayerRegistration> registrations = new HashMap<>();
    private Random random = new Random();
    private boolean fakeScoreEnabled;
    private String dcName;
    private JsonObject currentSetOfTasks;


    @Override
    public void start(Future<Void> future) {
        dcName = config().getString("data-center", "local-cluster");

        pictures = new PictureRepository(vertx, config());
        repository = new MetadataRepository(vertx, config());

        Completable metadataRepositoryReady = repository.init();

        Completable initPlayersMap = CacheService.get(vertx, config()).flatMap(cs -> cs.
            <String, Player>getIndexedCache(CACHE_PLAYERS))
            .doOnSuccess(ac -> this.players = ac)
            .toCompletable();

        Completable initAdminMap = CacheService.get(vertx, config()).flatMap(cs -> cs.
            <String, String>getCache(CACHE_ADMIN))
            .doOnSuccess(ac -> this.admin = ac)
            .doOnSuccess(c -> c.listen(ADDRESS_ADMIN_CHANGES))
            .toCompletable();

        Completable initTasksMap = CacheService.get(vertx, config()).flatMap(cs -> cs.
            <String, String>getCache(CACHE_TASKS))
            .doOnSuccess(ac -> this.tasks = ac)
            .doOnSuccess(ac -> ac.listen(ADDRESS_TASKS))
            .flatMap(AsyncCache::all)
            .doOnSuccess(map -> {
                currentSetOfTasks = new JsonObject();
                map.forEach((key, value) -> currentSetOfTasks.put(key, new JsonObject(value)));
            })
            .toCompletable();

        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(ADDRESS_PLAYERS);
        consumer.handler(event -> {
            JsonObject msg = event.body();
            LOGGER.info("Receiving a new player");
            // Initialize or reload player
            ConnectionFrame message = ConnectionFrame.fromJson(msg);

            Single<JsonObject> retrieveTasks = retrieveTasks();

            if (!message.hasId()) {
                // New player
                LOGGER.info("No id - creating a new player");
                retrieveTasks.flatMap(ts ->
                    initializePlayer(message)
                        .map(player -> new ConfigurationFrame(player, dcName, ts).toJson()))
                    .flatMap(f -> AdminEndpoint.extend(admin, f))
                    .subscribe(
                        event::reply,
                        err -> {
                            LOGGER.error("Player initialization failed", err);
                            event.fail(500, err.getMessage());
                        }
                    );
            } else {
                LOGGER.info("Id {} - reloading", message.id());
                retrieveTasks.flatMap(ts ->
                    reloadPlayer(message.id(), message.name(), message.email())
                        .map(player -> new ConfigurationFrame(player, dcName, ts).toJson()))
                    .flatMap(f -> AdminEndpoint.extend(admin, f))
                    .subscribe(
                        event::reply,
                        err -> {
                            LOGGER.error("Player reload failed", err);
                            event.fail(500, err.getMessage());
                        }
                    );
            }
        });

        MessageConsumer<JsonObject> taskConsumer = vertx.eventBus().consumer(ADDRESS_TASKS);
        MessageConsumer<JsonObject> adminConsumer = vertx.eventBus().consumer(ADDRESS_ADMIN_CHANGES);
        adminConsumer.handler(x -> this.dispatchNewConfiguration());
        taskConsumer.handler(x -> this.dispatchNewTasks());

        Completable initConsumer = consumer.rxCompletionHandler();
        Completable initTaskConsumer = taskConsumer.rxCompletionHandler();
        Completable initAdminConsumer = adminConsumer.rxCompletionHandler();

        String cn = config().getString("data-center", "localhost");
        JsonObject locations = config().getJsonObject("locations").getJsonObject(cn);
        fakeScoreEnabled = locations.getBoolean("fake-score", false);

        initPlayersMap
            .andThen(initPlayersMap)
            .andThen(initConsumer)
            .andThen(metadataRepositoryReady)
            .andThen(initTasksMap)
            .andThen(initAdminConsumer)
            .andThen(initTaskConsumer)
            .andThen(initAdminMap)
            .doOnComplete(() -> LOGGER.info("Player verticle ready"))
            .subscribe(CompletableHelper.toObserver(future));
    }

    private Single<JsonObject> retrieveTasks() {
        return tasks.all()
            .map(map -> {
                JsonObject json = new JsonObject();
                map.forEach((key, value) -> json.put(key, new JsonObject(value)));
                return json;
            });
    }

    private void dispatchNewTasks() {
        retrieveTasks()
            .subscribe(json -> {
                if (currentSetOfTasks == null || !json.encode().equalsIgnoreCase(currentSetOfTasks.encode())) {
                    currentSetOfTasks = json;
                    Set<String> ids = new HashSet<>(registrations.keySet());
                    ids.forEach(playerId ->
                        players.get(playerId)
                            .map(player -> new ConfigurationFrame(player, dcName, json))
                            .subscribe(
                                frame ->
                                    AdminEndpoint.extend(admin, frame.toJson())
                                        .subscribe(f -> vertx.eventBus().send(playerId, f)),
                                err -> {
                                    LOGGER.error("Error while retrieving player {} from cache", playerId, err);
                                    vertx.eventBus().send(playerId, new ErrorFrame(500, "Unknown player").toJson());
                                },
                                () -> {
                                    // unknown player
                                    PlayerRegistration removed = registrations.remove(playerId);
                                    if (removed != null) {
                                        removed.consumer().unregister();
                                    }
                                }
                            )
                    );
                }
            });
    }

    private void dispatchNewConfiguration() {
        Single<JsonObject> tasks = retrieveTasks();
        tasks.subscribe(
            json -> {
                Set<String> ids = new HashSet<>(registrations.keySet());
                ids.forEach(playerId ->
                    players.get(playerId)
                        .map(player -> new ConfigurationFrame(player, dcName, json))
                        .subscribe(
                            frame ->
                                AdminEndpoint.extend(admin, frame.toJson())
                                    .subscribe(f -> {
                                        vertx.eventBus().send(playerId, f);
                                        vertx.setTimer(1000, x -> vertx.eventBus().send(playerId, f));
                                        vertx.setTimer(2000, x -> vertx.eventBus().send(playerId, f));
                                        vertx.setTimer(3000, x -> vertx.eventBus().send(playerId, f));
                                        vertx.setTimer(4000, x -> vertx.eventBus().send(playerId, f));
                                    }),
                            err -> {
                                LOGGER.error("Error while retrieving player {} from cache", playerId, err);
                                vertx.eventBus().send(playerId, new ErrorFrame(500, "Unknown player").toJson());
                            },
                            () -> {
                                PlayerRegistration removed = registrations.remove(playerId);
                                if (removed != null) {
                                    removed.consumer().unregister();
                                }
                            }
                        )
                );
            });
    }

    @Override
    public void stop(Future<Void> done) {
        // Close all sockets, unregister users.
        Observable.fromIterable(registrations.values())
            .flatMapCompletable(reg -> {
                Completable unreg = reg.consumer().rxUnregister();
                vertx.eventBus().send("active-players", new PlayerEventMessage(reg.playerId(),
                    PlayerEventMessage.PlayerEvent.DEPARTURE).toJson());
                return unreg;
            })
            .doOnComplete(() -> LOGGER.info("Cleanup done... {} connection closed", registrations.size()))
            .doOnTerminate(() -> registrations.clear())
            .subscribe(CompletableHelper.toObserver(done));
    }

    private Single<Player> reloadPlayer(String id, String name, String email) {
        return players
            .get(id)
            .onErrorReturnItem(new Player()) // Error case
            .toSingle(new Player()) // Empty case
            .map(p -> pimpPlayer(name, email, p))
            .flatMap(p -> players.put(p.getPlayerId(), p).toSingleDefault(p))
            .flatMap(this::initCommunicationProtocol)
            .doOnSuccess(p -> vertx.eventBus().send(ADDRESS_ACTIVE,
                new PlayerEventMessage(p.getPlayerId(), PlayerEventMessage.PlayerEvent.ARRIVAL).toJson()));
    }

    private Player pimpPlayer(String name, String email, Player player) {
        if (name != null) {
            player.setPlayerName(name);
        }

        if (email != null) {
            player.setEmail(Player.encode(email));
            setStickyNames(player, email);
        }

        return player;
    }


    private Single<Player> initCommunicationProtocol(Player player) {
        MessageConsumer<JsonObject> consumer =
            vertx.eventBus().consumer(player.getPlayerId() + "/message");

        consumer.handler(msg -> {
            String event = msg.body().getString("type");
            if (event == null) {
                LOGGER.error("The type is missing in the frame " + msg.body().encode());
                return;
            }

            Frame.TYPE type = Frame.TYPE.getByName(event);
            switch (type) {
                case GONE:
                    onDeparture(consumer, player);
                    break;
                case PICTURE:
                    onPicture(PictureFrame.fromJson(msg.body()));
                    break;
                case SCORE:
                    onScore(player, ScoreFrame.fromJson(msg.body()));
                    break;
                default:
                    LOGGER.error("Unexpected frame type: " + msg.body().encode());
            }
        });

        return consumer.rxCompletionHandler()
            .doOnComplete(() -> registrations.put(player.id(), new PlayerRegistration(player.getPlayerId(), consumer)))
            .toSingleDefault(player);
    }

    private void onScore(Player player, ScoreFrame frame) {
        JsonObject json = frame.toJson();
        String taskId = json.getString("taskId");
        String transactionId = json.getString("transactionId");
        int point = frame.score();

        if (!json.getString("playerId").equalsIgnoreCase(player.id())) {
            LOGGER.error("Wrong player id ({} vs {}), there is a routing issue - aborting",
                json.getString("playerId"), player.id());
            return;
        }

        // If task already achieved and regular task - do not score -> matched: true, scored:false
        // If task already achieved and party task - do only score if better:
        // matched: true, scored: if better
        // The achievement list must be updated in the last case.

        Single<Boolean> scored;
        AtomicReference<Player> user = new AtomicReference<>();
        AtomicBoolean match = new AtomicBoolean(false);

        Single<JsonObject> getTask = tasks.get(taskId)
            .map(JsonObject::new)
            .switchIfEmpty(Single.error(new Exception("Unknown task: " + taskId)));

        if (point == 0) {
            scored = players.get(player.id())
                .defaultIfEmpty(player)
                .doOnSuccess(user::set)
                .flatMapSingle(x -> Single.just(false));
        } else {
            match.set(true);
            scored = getTask
                .flatMap(task -> {
                    boolean isParty = task.getString("type").equalsIgnoreCase("partyisland");
                    return players.get(player.id())
                        .defaultIfEmpty(player)
                        .doOnSuccess(user::set)
                        .flatMapSingle(p -> {
                            if (p.achieved(taskId, transactionId, point, isParty)) {
                                return players.put(player.id(), p).toSingleDefault(true);
                            } else {
                                return Single.just(false);
                            }
                        });
                });
        }

        scored.subscribe(
            hasScored -> {
                JsonObject updated = json
                    .put("total", user.get().score())
                    .put("achievements", Json.encodeToBuffer(user.get().achievements()).toJsonArray())
                    .put("matched", match.get())
                    .put("scored", hasScored);
                if (!hasScored) {
                    updated.put("score", 0);
                }
                vertx.eventBus().send(player.getPlayerId(), updated);
            }
        );
    }

    private void onPicture(PictureFrame message) {
        // We are not going to check that state for 1) because it's not convenient when running checks 2) performance
        repository
            .save(message.playerId(), message.transactionId(), message.taskId(), message.metadata())
            .doOnComplete(() -> LOGGER.info("Metadata for transactionId {} saved", message.transactionId()))
            .andThen(pictures.save(message.playerId(), message.transactionId(), message.picture()))
            .doOnComplete(() -> LOGGER.info("Picture for transactionId {} saved", message.transactionId()))
            .doOnComplete(() -> {
                if (fakeScoreEnabled) {
                    sendFakeScore(message);
                }
            })
            .subscribe(
                () -> LOGGER.info("Picture and metadata sent by {}, has been saved", message.playerId()),
                err -> {
                    LOGGER.error("Unable to save the picture or metadata for player {}", message.playerId(), err);
                    vertx.eventBus().send(message.playerId(),
                        new ErrorFrame(500, "Unable to save the picture or the metadata: " + err.getMessage(),
                            message.transactionId()).toJson());
                }
            );
    }

    private void sendFakeScore(PictureFrame message) {
        int delay = 2 + random.nextInt(5);
        boolean scored = random.nextInt(7) >= 2;
        vertx.setTimer(delay * 1000, x -> {
            JsonObject json = new JsonObject()
                .put("playerId", message.playerId())
                .put("taskId", message.taskId())
                .put("transactionId", message.transactionId())
                .put("url", "http://example.com");

            Single<JsonObject> payload;
            if (scored) {
                payload = tasks.get(message.taskId())
                    .map(JsonObject::new)
                    .map(j -> j.getInteger("point"))
                    .toSingle(10)
                    .map(i -> json.put("score", i));
            } else {
                payload = Single.just(json)
                    .map(j -> j.put("score", 0));
            }
            WebClient client = WebClient.create(vertx);

            payload.flatMap(p -> client.postAbs("http://localhost:8080/score").rxSendJsonObject(p))
                .map(resp -> {
                    if (resp.statusCode() != 204) {
                        throw new IllegalStateException("Invalid response: " + resp.statusCode() + ", "
                            + resp.bodyAsString());
                    } else {
                        return resp;
                    }
                })
                .doOnSuccess(resp -> LOGGER.info("Sent fake score for {} : {}", message.playerId(), scored))
                .doOnError(t -> LOGGER.error("Unable to call the score endpoint", t))
                .doAfterTerminate(() -> closeQuietly(client))
                .subscribe();
        });
    }

    private void onDeparture(MessageConsumer<JsonObject> consumer, Player player) {
        LOGGER.info("A player ({}) just left...", player.getPlayerId());
        PlayerRegistration registration = registrations.remove(player.id());
        Completable unregistration;
        if (registration == null) {
            // Should not happen but we never now...
            unregistration = consumer.rxUnregister();
        } else {
            unregistration = registration.consumer().rxUnregister();
        }
        unregistration
            .doOnTerminate(() -> vertx.eventBus().send(ADDRESS_ACTIVE,
                new PlayerEventMessage(player.getPlayerId(), PlayerEventMessage.PlayerEvent.DEPARTURE).toJson()))
            .subscribe(
                () -> LOGGER.info("Player {} ({}) successfully removed", player.getPlayerId(), player.name()),
                err -> LOGGER.info("Unable to remove the player {} ({}) successfully", player.getPlayerId(), player.name(), err)
            );
    }

    private Single<Player> initializePlayer(ConnectionFrame message) {
        System.out.println(message.toJson().encodePrettily());
        Player player = new Player();
        player = pimpPlayer(message.name(), message.email(), player);
        return players.put(player.getPlayerId(), player)
            .andThen(initCommunicationProtocol(player))
            .doOnSuccess(p -> vertx.eventBus().send(ADDRESS_ACTIVE,
                new PlayerEventMessage(p.getPlayerId(), PlayerEventMessage.PlayerEvent.ARRIVAL).toJson()));
    }
    
    private void setStickyNames(Player player, String email) {
        // Here you can configure the sticky names based on the email addressed used for login
    }
}
