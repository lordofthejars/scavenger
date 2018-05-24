package me.escoffier.keynote.s3;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;

/**
 * S3 parent class
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public abstract class AbstractS3Client {

    protected final Vertx vertx;
    protected final JsonObject config;

    public AbstractS3Client(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public abstract Completable save(String playerId, String transactionId, Buffer bytes);

}
