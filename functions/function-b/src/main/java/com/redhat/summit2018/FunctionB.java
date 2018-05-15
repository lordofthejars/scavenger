package com.redhat.summit2018;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.redhat.summit2018.services.Env;

import java.util.Map;

import static com.redhat.summit2018.services.Cache.*;
import static com.redhat.summit2018.services.Encoder.encode;
import static com.redhat.summit2018.services.Yolo.yolo;

/**
 * Triggered on Gluster upload
 * Retrieve transaction from the txs cache
 * Invoke yolo
 * Merge result
 * Save the content into a cache
 */
public class FunctionB {

    public static JsonObject main(JsonObject request) {
        Env.init(request);
        long begin = System.currentTimeMillis();

        JsonObject obj = request.getAsJsonObject("swiftObj");
        String fileName = obj.get("object").getAsString();
        String tx = fileName.endsWith(".jpeg") ?
            fileName.substring(0, fileName.length() - 5) : fileName;
        String url = obj.get("url").getAsString() + "/" + obj.get("container").getAsString() + "/" + fileName;
        String picture = encode(url);

        JsonObject transaction = getTransaction(tx).orElseThrow(
            () -> new RuntimeException("Function invocation failed - no transaction (" + tx + ")"));

        long yolo_begin = System.currentTimeMillis();
        Map<String, Double> objects;
        try {
            objects = yolo(picture, 0.1);
        } catch (Exception e) {
            // YOLO failure
            transaction.addProperty("yolo-failure", e.getMessage());
            long yolo_end = System.currentTimeMillis();
            transaction.addProperty("model-duration-time-ms", (yolo_end - yolo_begin));
            transaction.addProperty("function-b-entry-time", begin);
            transaction.addProperty("function-b-in-error", true);
            transaction.addProperty("function-b-entry-time", yolo_end);
            transaction.addProperty("function-b-duration-time-ms",
                yolo_end - begin);
            System.out.println("Failing with: " + transaction.toString());
            return transaction;
        }
        long yolo_end = System.currentTimeMillis();

        JsonObject task = getTask(transaction.get("taskId").getAsString())
            .orElseThrow(() -> new RuntimeException("Function invocation failed - no task"));

        String toBeFound = task.get("object").getAsString();
        int point = task.get("point").getAsInt();

        Double ratio = objects.get(toBeFound);
        if (ratio != null) {
            transaction.addProperty("ratio", ratio);
            transaction.addProperty("score", Math.round(point * ratio));
        } else {
            transaction.addProperty("score", 0);
        }

        // Cleanup and extension
        transaction.remove("exifData");
        transaction.addProperty("url", url);
        JsonArray o = new JsonArray();
        objects.keySet().forEach(o::add);
        transaction.add("objects", o);

        transaction.addProperty("taskName", task.get("description").getAsString());
        transaction.addProperty("taskObject", task.get("object").getAsString());

        transaction.addProperty("function-b-entry-time", begin);
        transaction.addProperty("model-duration-time-ms", (yolo_end - yolo_begin));

        save(tx, transaction);

        return transaction;
    }


}
