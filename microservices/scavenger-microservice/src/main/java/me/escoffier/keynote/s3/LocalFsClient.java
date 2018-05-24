package me.escoffier.keynote.s3;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class LocalFsClient extends AbstractS3Client {
    /**
     * Directory use to store picture in local mode (no S3).
     */
    private static final String ROOT = "pictures";

    private static final Logger LOGGER = LogManager.getLogger("S3-Local-FS-Client");

    public LocalFsClient(Vertx vertx, JsonObject config) {
        super(vertx, config);
    }


    public Completable save(String playerId, String transactionId, Buffer bytes) {
        LOGGER.info("Saving picture for {}, transactionId: {}", playerId, transactionId);
        String path = "/" + transactionId + ".jpg";
        String picturePath = ROOT + path;
        return vertx.fileSystem()
            .rxMkdirs(ROOT)
            .andThen(vertx.fileSystem().rxWriteFile(picturePath, bytes));
    }
}
