package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import me.escoffier.keynote.s3.AbstractS3Client;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PictureRepository {

    private final AbstractS3Client client;


    PictureRepository(Vertx vertx, JsonObject config) throws Exception {
        JsonObject c = Constants.getLocationAwareConfig(config);

        client = (AbstractS3Client) this.getClass().getClassLoader().loadClass(c.getString("s3-client-class"))
            .getConstructor(Vertx.class, JsonObject.class).newInstance(vertx, config);
    }

    public Completable save(String playerId, String transactionId, byte[] picture) {
        return client.save(playerId, transactionId, new Buffer(io.vertx.core.buffer.Buffer.buffer(picture)));
    }
}
