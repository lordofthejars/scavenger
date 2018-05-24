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
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import uk.co.lucasweb.aws.v4.signer.Signer;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class AwsS3Client extends AbstractS3Client {
    private final String accessKey;
    private final String secretAccessKey;
    private final WebClient client;
    private final String s3Url;

    // private static final String sha256 = "STREAMING-AWS4-HMAC-SHA256-PAYLOAD";
    private static final DateTimeFormatter AMZ_DATE_FORMAT = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZoneUTC().withLocale(Locale.US);

    private static final Logger LOGGER = LogManager.getLogger("S3-Aws-Signed-Client");
    private final RoundRobin<String> selector;

    public AwsS3Client(Vertx vertx, JsonObject config) {
        super(vertx, config);
        JsonObject c = Constants.getLocationAwareConfig(config);

        s3Url = c.getString("s3-url");
        JsonArray bucketArray = c.getJsonArray("s3-buckets");

        List<String> buckets;
        if (bucketArray == null) {
            buckets = Collections.emptyList();
        } else {
            buckets = new ArrayList<>();
            bucketArray.forEach(o -> buckets.add((String) o));
            Collections.shuffle(buckets);
        }

        selector = new RoundRobin<>(buckets);

        accessKey = c.getString("s3-auth-access-key");
        secretAccessKey = c.getString("s3-auth-secret-access-key");
        client = WebClient.create(vertx, new WebClientOptions().setMaxPoolSize(100));
    }

    @Override
    public Completable save(String playerId, String transactionId, Buffer bytes) {
        LOGGER.info("Saving picture for {}, transactionId: {}", playerId, transactionId);

        String pickedBucket = selector.get();
        String objectPath = "/" + pickedBucket + "/" + transactionId + ".jpeg";
        String fullPath = s3Url + objectPath;
        URL url;

        try {
            url = new URL(s3Url);
        } catch (Throwable t) {
            LOGGER.error(t.getMessage(), t);
            t.printStackTrace();
            throw new RuntimeException(t);
        }

        LOGGER.info("Uploading picture to S3: {}", fullPath);
        long begin = System.currentTimeMillis();

        DateTime date = new DateTime();
        String strDate = date.toString(AMZ_DATE_FORMAT);
        AwsCredentials creds = new AwsCredentials(accessKey, secretAccessKey);

        String port = url.getPort() == -1 ? "" : (":" + url.getPort());
        String host = url.getHost() + port;
        String contentLength = String.valueOf(bytes.length());
        String hash = computeHash(bytes.getDelegate().getBytes());

        uk.co.lucasweb.aws.v4.signer.HttpRequest sreq = new uk.co.lucasweb.aws.v4.signer.HttpRequest("PUT", objectPath);
        String signature = Signer.builder()
            .awsCredentials(creds)
            .header("host", host)
            .header("x-amz-date", strDate)
            .header("x-amz-content-sha256", hash)
            .header("x-amz-decoded-content-length", contentLength)
            .buildS3(sreq, hash)
            .getSignature();

        HttpRequest<Buffer> request = client.putAbs(fullPath);
        request
            .putHeader("host", host)
            .putHeader("x-amz-date", strDate)
            .putHeader("x-amz-content-sha256", hash)
            .putHeader("x-amz-decoded-content-length", contentLength)
            .putHeader("Authorization", signature);


        // Buffer buf1 = Buffer.buffer(signature.split("=")[3] + "\n");
        // buf1.appendBuffer(bytes);
        // Buffer buf1 = Buffer.buffer();
        return request.rxSendBuffer(bytes).map(resp -> {
            long end = System.currentTimeMillis();
            MetricEndpoint.report(vertx, "PUT " + s3Url + "/" + pickedBucket, resp.statusCode());
            MetricEndpoint.report(vertx, "PUT " + s3Url + "/" + pickedBucket, end - begin);
            MetricEndpoint.beginInFlight(vertx, "transactions", transactionId, 180000);
            if (resp.statusCode() != 200) {
                LOGGER.error("Unable to upload the picture to Gluster ({}), got {} {}", fullPath, resp.statusCode(),
                    resp.statusMessage());
                throw new IllegalStateException("Unable to upload the picture to Gluster: " + resp.statusMessage());
            } else {
                LOGGER.info("Picture saved in S3 ({}), it took {} ms: {}", s3Url + "/" + pickedBucket, (end - begin),
                    resp.bodyAsString());
            }
            return fullPath;
        }).toCompletable();
    }


    private String computeHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return String.format( "%064x", new BigInteger( 1, digest ) );
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
