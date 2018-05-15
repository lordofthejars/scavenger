package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.ObservableHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.core.http.WebSocket;
import io.vertx.reactivex.core.impl.AsyncResultSingle;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class CheckEndpoint extends Endpoint {


    private final JsonObject clouds;
    private static final Logger LOGGER = LogManager.getLogger("Check-Endpoint");
    private static final JsonObject CONNECTION_FRAME = new JsonObject().put("type", "connection");

    public CheckEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
        clouds = config.getJsonObject("clouds", new JsonObject());
    }

    public void check(RoutingContext rc) {
        HttpServerResponse response = rc.response();
        response.setChunked(true);
        Set<String> names = clouds.fieldNames();
        List<Completable> list = names.stream()
            .map(n -> {
                String host = clouds.getString(n);
                return run(n, host, response);
            }).collect(Collectors.toList());

        AtomicInteger count = new AtomicInteger();
        AtomicBoolean error = new AtomicBoolean();
        LOGGER.info("Running " + list.size() + " checks");


        list.forEach(action ->
            action
                .doOnError(t -> error.set(true))
                .doFinally(() -> {
                    int i = count.incrementAndGet();
                    if (i == list.size()) {
                        if (error.get()) {
                            response.end("System unstable");
                        } else {
                            response.end("System stable");
                        }
                    }
                })
                .subscribe(
                    () -> LOGGER.info("1 check completed successfully"),
                    err -> LOGGER.warn("1 check didn't succeeded", err)
                )
        );
    }

    private Single<WebSocket> connect(HttpClient client) {
        return new AsyncResultSingle<>(handler ->
            client.websocket("/game", socket -> handler.handle(Future.succeededFuture(socket))));
    }

    private Single<JsonObject> sendAndWait(String name, WebSocket socket, JsonObject frame, String awaited, HttpServerResponse response) {
        Single<JsonObject> single = ObservableHelper.toObservable(socket.getDelegate())
            .map(Buffer::toJsonObject)
            .filter(j -> j.getString("type").equalsIgnoreCase(awaited))
            .timeout(1, TimeUnit.MINUTES)
            .firstOrError()
            .doOnSuccess(j -> LOGGER.info("[{}] Received {} frame", name, awaited))
            .doOnSuccess(j -> response.write(name + " - received frame " + awaited + "\n"))
            .doOnError(e -> response.write(name + " - error while waiting for " + awaited +
                " : " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()) + "\n"));
        response.write(name + " - sending frame " + frame.getString("type") + "\n");
        LOGGER.info("[{}] sending frame {}", name, frame.getString("type"));
        socket.writeFinalTextFrame(frame.encode());
        return single;
    }


    public Completable run(String name, String host, HttpServerResponse response) {
        LOGGER.info("Running for {} {}", name, host);
        HttpClientOptions options = new HttpClientOptions()
            .setSsl(true).setTrustAll(true).setDefaultHost(host).setDefaultPort(443);
        HttpClient client = vertx.createHttpClient(options);
        Completable callHC = callHealthChecks(name, response, options);
        Completable callLeaderboard = callLeaderboards(name, response, options);

        return callHC
            .andThen(callLeaderboard)
            .andThen(
                connect(client)
                    .flatMap(socket ->
                        sendAndWait(name, socket, CONNECTION_FRAME, "configuration", response)
                            .map(config -> {
                                String taskId = config.getJsonObject("tasks").fieldNames().iterator().next();
                                String transactionId = "check-" + name + "-" + UUID.randomUUID().toString();
                                return new JsonObject()
                                    .put("type", "picture")
                                    .put("playerId", config.getString("playerId"))
                                    .put("picture", encoded())
                                    .put("taskId", taskId)
                                    .put("transactionId", transactionId)
                                    .put("metadata", new JsonObject().put("format", "jpg"));
                            })
                            .flatMap(json -> sendAndWait(name, socket, json, "score", response))
                            .doOnSuccess(json -> LOGGER.info("[{}] Received score frame", name)))
                    .toCompletable()
            )
            .doFinally(client::close);
    }

    private Completable callHealthChecks(String name, HttpServerResponse response, HttpClientOptions options) {
        WebClient health = WebClient.create(vertx, new WebClientOptions(options));

        return health.get("/health").rxSend().map(resp -> {
            if (resp.statusCode() != 200) {
                LOGGER.warn("[{}] Unhealthy: {}", name, resp.bodyAsString());
                response.write("[" + name + "] Unhealthy " + resp.bodyAsString() + "\n");
                throw new RuntimeException("Unhealthy: " + name);
            } else {
                LOGGER.info("[{}] Healthy: {}", name, resp.bodyAsString());
                response.write("[" + name + "] Healthy " + resp.bodyAsString() + "\n");
            }
            return resp;
        })
            .doFinally(health::close)
            .toCompletable();
    }

    private Completable callLeaderboards(String name, HttpServerResponse response, HttpClientOptions options) {
        WebClient health = WebClient.create(vertx, new WebClientOptions(options));

        return health.get("/leaderboard").rxSend().map(resp -> {
            if (resp.statusCode() != 200) {
                LOGGER.warn("[{}] Leaderboard is unhealthy: {}", name, resp.bodyAsString());
                response.write("[" + name + "] Leaderboard is unhealthy\n");
                throw new RuntimeException("Leaderboard unhealthy: " + name);
            } else {
                LOGGER.info("[{}] Leaderboard is healthy", name);
                response.write("[" + name + "] Leaderboard healthy\n");
            }
            return resp;
        })
            .doFinally(health::close)
            .toCompletable();
    }

    private String encoded() {
        io.vertx.reactivex.core.buffer.Buffer buffer = vertx.fileSystem().readFileBlocking("I love coffee.jpg");
        return Base64.getEncoder().encodeToString(buffer.getDelegate().getBytes());
    }

}
