package me.escoffier.keynote.endpoints;

import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static me.escoffier.keynote.messages.Frame.TYPE.SCORE;

/**
 * Endpoint receiving scores from the function C.
 */
public class ScoreEndpoint {

    private final Vertx vertx;

    private static final Logger LOGGER = LogManager.getLogger("Score-Endpoint");


    public ScoreEndpoint(Vertx vertx) {
        this.vertx = vertx;
    }

    public void addScore(RoutingContext rc) {
        LOGGER.info("Receiving score: {}", rc.getBodyAsJson().encodePrettily());
        JsonObject json = rc.getBodyAsJson();
        String playerId = json.getString("playerId");
        String transactionId = json.getString("transactionId");
        String taskId = json.getString("taskId");
        int score = json.getInteger("score", -1);
        if (playerId == null || transactionId == null || score == -1 || taskId == null
            || json.getString("url") == null) {
            rc.response().setStatusCode(400)
                .end("Bad request, playerId, transactionId, taskId and score are required");
            return;
        }

        vertx.eventBus().send(playerId + "/message", json.put("type", SCORE.getName()));

        LOGGER.info("Score sent to player {}", playerId);

        Integer time = json.getInteger("function-b-duration-time-ms", -1);
        if (time != -1) {
            MetricEndpoint.report(vertx, "function-b", time.longValue());
        }
        Integer yolo = json.getInteger("model-duration-time-ms", -1);
        if (yolo != -1) {
            MetricEndpoint.report(vertx, "yolo", yolo.longValue());
        }
        Integer fc_time = json.getInteger("function-c-duration-ms", -1);
        if (fc_time != -1) {
            MetricEndpoint.report(vertx, "function-c", fc_time.longValue());
        }

        MetricEndpoint.reportInflight(vertx, "transactions", transactionId);


        rc.response().setStatusCode(204).end();
    }
}
