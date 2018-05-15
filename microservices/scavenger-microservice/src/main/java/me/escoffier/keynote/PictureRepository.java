package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;

public class PictureRepository {

    private final GlusterClient client;

    PictureRepository(Vertx vertx, JsonObject config) {
        client = new GlusterClient(vertx, config);
    }


    public Completable save(String playerId, String transactionId, byte[] picture) {
        return client.save(playerId, transactionId, new Buffer(io.vertx.core.buffer.Buffer.buffer(picture)));
    }

}
