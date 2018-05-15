package com.redhat.summit2018.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.redhat.summit2018.services.Env.getOrDefault;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Yolo {

    private static final Logger LOGGER = LogManager.getLogger("Yolo-Service");

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT;

    // Not really great, but as the location won't change, it should be fine.
    private static final String URL = getOrDefault("model-endpoint", "http://localhost:8080/v2/yolo");

    static {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().retryOnConnectionFailure(true)
            .connectTimeout(80, TimeUnit.SECONDS)
            .readTimeout(80, TimeUnit.SECONDS)
            .writeTimeout(80, TimeUnit.SECONDS);
        CLIENT = builder.build();
    }

    // Input: { "image": base64(img) } -- Image
    // Output: [ { "score": float, "voc": "[category]" }, ... ] -- Label[]

    public static Map<String, Double> yolo(String based64EncodedImage, double confidenceLevel) {
        LOGGER.info("Yolo service endpoint {}", URL);


        JsonObject body = new JsonObject();
        body.addProperty("image", based64EncodedImage);

        Request request = new Request.Builder()
            .url(URL)
            .post(RequestBody.create(JSON, body.toString()))
            .build();

        try {
            Response response = CLIENT.newCall(request).execute();
            LOGGER.info("Got a response: {}", response.code());

            if (!isOk(response.code())) {
                LOGGER.info("Unable to analyze the image: {}", body(response));
                throw new RuntimeException("Bad result from Yolo: " + response.code());
            }

            JsonArray array = new JsonParser().parse(body(response)).getAsJsonArray();
            Map<String, Double> objects = new LinkedHashMap<>();
            array.forEach(element -> {
                    float score = element.getAsJsonObject().get("score").getAsFloat();
                String voc = element.getAsJsonObject().get("voc").getAsString();
                if (score >= confidenceLevel) {
                        double ratioForObject = getRatioForObject(element.getAsJsonObject());
                        if (objects.containsKey(voc)) {
                            if (objects.get(voc) < ratioForObject) {
                                objects.put(voc, ratioForObject);
                            }
                        } else {
                            objects.put(voc, ratioForObject);
                        }
                    } else {
                        LOGGER.info("Objects found but level of confidence to low: {} {}",
                            voc,
                            element.getAsJsonObject().get("score").getAsFloat());
                    }
                }
            );
            LOGGER.info("Yolo objects: {}", objects);
            return objects;
        } catch (IOException e) {
            LOGGER.error("Unable to call the yolo endpoint: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to call the yolo endpoint: " + e.getMessage());
        }
    }

    // 120000
    // 31400
    // 
    private static final long IMAGE_SURFACE = 400 * 300;

    private static double getRatioForObject(JsonObject element) {
        double ratio = (element.get("br_x").getAsDouble() - element.get("tl_x").getAsDouble())
            * (element.get("br_y").getAsDouble() - element.get("tl_y").getAsDouble()) / IMAGE_SURFACE * 100;
        LOGGER.info("Ratio is {}, {} {}",
            ratio,
            element.get("br_x").getAsDouble() - element.get("tl_x").getAsDouble(),
            element.get("br_y").getAsDouble() - element.get("tl_y").getAsDouble()
        );
        if (ratio >= 50) {
            return 1;
        } else if (ratio >= 30) {
            return 0.66;
        } else if (ratio >= 20) {
            return 0.5;
        } else {
            return 0.33;
        }
    }

    private static String body(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) {
            return "no content";
        } else {
            return body.string();
        }
    }

    private static boolean isOk(int status) {
        return status == 200;
    }
}
