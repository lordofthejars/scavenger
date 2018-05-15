package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.RoutingContext;
import me.escoffier.keynote.Constants;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of the REST API to handle tasks.
 */
public class TaskEndpoint extends Endpoint {
    private AsyncCache<String, String> cache;

    private static final Logger LOGGER = LogManager.getLogger("TaskManager");


    public TaskEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
    }

    public Completable init() {
        return CacheService.get(vertx, config)
            .flatMap(c -> c.<String, String>getCache(Constants.CACHE_TASKS))
            .doOnSuccess(c -> cache = c)
            .flatMapCompletable(cache -> {
                JsonObject jo = this.config.getJsonObject("tasks", new JsonObject());
                return Flowable.fromIterable(jo.fieldNames())
                    .flatMapCompletable(id -> {
                        JsonObject object = jo.getJsonObject(id);
                        return cache.put(id, object.encode());
                    });
            });
    }


    public void getTasks(RoutingContext rc) {
        cache.all()
            .subscribe(
                map -> {
                    Set<Map.Entry<String, String>> set = map.entrySet();
                    JsonObject json = new JsonObject();
                    set.forEach(entry -> json.put(entry.getKey(), new JsonObject(entry.getValue())));
                    rc.response().end(json.encode());
                },
                err -> {
                    LOGGER.error("Unable to retrieve tasks", err);
                    rc.response().setStatusCode(500).end("Unable to retrieve tasks: " + err.getMessage());
                });
    }

    public void addTask(RoutingContext rc) {
        JsonObject json = rc.getBodyAsJson();
        String id = UUID.randomUUID().toString();
        cache.put(id, json.encode())
            .subscribe(
                () -> rc.response().end(json.put("taskId", id).encode()),
                err -> {
                    LOGGER.error("Unable to add a task", err);
                    rc.response().setStatusCode(500).end("Unable to add a task: " + err.getMessage());
                });
    }

    public void deleteTask(RoutingContext rc) {
        String id = rc.pathParam("taskId");
        cache.remove(id)
            .subscribe(() -> rc.response().setStatusCode(204).end());
    }

    public void editTask(RoutingContext rc) {
        String id = rc.pathParam("taskId");
        JsonObject task = rc.getBodyAsJson();
        cache.put(id, task.encode())
            .subscribe(() -> rc.response().setStatusCode(200).end(task.put("taskId", id).encode()));
    }
}
