package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.WebClient;
import me.escoffier.keynote.endpoints.MetricEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the interaction with Gluster using the S3 api
 */
public class GlusterClient {
    /**
     * Directory use to store picture in local mode (no S3).
     */
    private static final String ROOT = "pictures";

    private final List<String> urls;
    private final boolean useS3;
    private final String auth;
    private final WebClient client;

    private static final Logger LOGGER = LogManager.getLogger("Gluster-Client");
    private final Vertx vertx;

    GlusterClient(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        JsonObject c = Constants.getLocationAwareConfig(config);

        JsonArray array = c.getJsonArray("s3-urls");
        if (array == null) {
            urls = Collections.emptyList();
        } else {
            urls = new ArrayList<>();
            array.forEach(o -> urls.add((String) o));
            Collections.shuffle(urls);
        }

        useS3 = c.getBoolean("s3-enable", !urls.isEmpty());
        auth = c.getString("s3-auth-token", null);
        if (useS3) {
            client = WebClient.create(vertx, new WebClientOptions().setMaxPoolSize(100));
            LOGGER.info("Using S3 Token: {}", auth != null);
        } else {
            client = null;
        }
    }

    private Completable saveFileLocally(String transactionId, Buffer bytes) {
        String path = "/" + transactionId + ".jpg";
        String picturePath = ROOT + path;
        return vertx.fileSystem()
            .rxMkdirs(ROOT)
            .andThen(vertx.fileSystem().rxWriteFile(picturePath, bytes));
    }

    public Completable save(String playerId, String transactionId, Buffer bytes) {
        LOGGER.info("Saving picture for {}, transactionId: {}", playerId, transactionId);
        if (!useS3) {
            return saveFileLocally(transactionId, bytes);
        } else {
            // S3
            String pickedURL = getUrl();
            String fullPath = pickedURL + "/" + transactionId + ".jpeg";
            LOGGER.info("Uploading picture to S3: {}", fullPath);
            long begin = System.currentTimeMillis();
            HttpRequest<Buffer> request = client.putAbs(fullPath);
            if (auth != null) {
                request.putHeader("X-Auth-Token", auth);
            }
            return request
                .rxSendBuffer(bytes)
                .map(resp -> {
                    long end = System.currentTimeMillis();
                    MetricEndpoint.report(vertx, "PUT " + pickedURL, resp.statusCode());
                    MetricEndpoint.report(vertx, "PUT " + pickedURL, end - begin);
                    MetricEndpoint.beginInFlight(vertx, "transactions", transactionId, 180000);
                    if (resp.statusCode() != 201) {
                        LOGGER.error("Unable to upload the picture to Gluster ({}), got {} {}",
                            fullPath, resp.statusCode(), resp.statusMessage());
                        throw new IllegalStateException("Unable to upload the picture to Gluster: "
                            + resp.statusMessage());
                    } else {
                        LOGGER.info("Picture saved in Gluster ({}), it took {} ms: {}", pickedURL,
                            (end - begin), resp.bodyAsString());
                    }
                    return fullPath;
                })

                .toCompletable();
        }
    }

    // Code implementing the round robin.

    private int current = 0;
    public String getUrl() {
        String url =  urls.get(current);
        current = current + 1;
        if (current >= urls.size()) {
            current = 0;
        }
        return url;
    }


}
