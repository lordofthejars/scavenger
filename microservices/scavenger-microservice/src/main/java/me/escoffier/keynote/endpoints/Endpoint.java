package me.escoffier.keynote.endpoints;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;

/**
 * Base endpoint class.
 */
public class Endpoint {

    protected final JsonObject config;
    protected final Vertx vertx;

    public Endpoint(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public Completable init() {
        return Completable.complete();
    }



}
