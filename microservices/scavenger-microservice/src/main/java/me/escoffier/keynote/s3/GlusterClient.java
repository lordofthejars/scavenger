package me.escoffier.keynote.s3;

import io.reactivex.Completable;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.WebClient;
import me.escoffier.keynote.Constants;
import me.escoffier.keynote.endpoints.MetricEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the interaction with Gluster using the S3 api
 */
public class GlusterClient extends AbstractS3Client {

    private final String auth;
    private final WebClient client;
    private final RoundRobin<String> selector;

    private static final Logger LOGGER = LogManager.getLogger("S3-Gluster-Client");

    public GlusterClient(Vertx vertx, JsonObject config) {
        super(vertx, config);
        JsonObject c = Constants.getLocationAwareConfig(config);

        JsonArray array = c.getJsonArray("s3-urls");
        if (array == null) {
            throw new RuntimeException("You have to provide at least one s3 url");
        } else {
            List<String> urls = new ArrayList<>();
            array.forEach(o -> urls.add((String) o));
            Collections.shuffle(urls);
            selector = new RoundRobin<>(urls);
        }

        auth = c.getString("s3-auth-token", null);
        client = WebClient.create(vertx, new WebClientOptions().setMaxPoolSize(100));
        LOGGER.info("Using S3 Token: {}", auth != null);
    }

    public Completable save(String playerId, String transactionId, Buffer bytes) {
        LOGGER.info("Saving picture for {}, transactionId: {}", playerId, transactionId);
        // S3
        String pickedURL = selector.get();
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
