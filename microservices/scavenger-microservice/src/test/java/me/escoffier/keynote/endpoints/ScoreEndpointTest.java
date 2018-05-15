package me.escoffier.keynote.endpoints;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpClient;
import me.escoffier.keynote.TestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static me.escoffier.keynote.Restafari.get;
import static me.escoffier.keynote.Restafari.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Checks the score endpoint.
 */
public class ScoreEndpointTest extends TestBase {


    private HttpClientOptions httpClientOptions = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080);
    private HttpClient client;
    private String playerId;
    private List<String> taskIds;

    @Before
    public void setUp() {
        client = vertx.createHttpClient(httpClientOptions);
        JsonObject frame = connect();
        playerId = frame.getString("playerId");
        JsonObject tasks = frame.getJsonObject("tasks");
        taskIds = new ArrayList<>(tasks.fieldNames());
        Collections.shuffle(taskIds);
    }

    @After
    public void tearDown() {
        client.close();
    }

    @Test
    public void testThatAchievementsAreStored() {
        String task = taskIds.get(0);

        JsonObject payload = new JsonObject()
            .put("transactionId", UUID.randomUUID().toString())
            .put("taskId", task)
            .put("playerId", playerId)
            .put("url", "http://example.com")
            .put("score", 5);

        given().body(payload.encode()).post("/score").then().statusCode(204);

        await().until(() -> new JsonObject(get("/admin/players/" + playerId).asString()).getInteger("score") == 5);

        JsonObject json = new JsonObject(get("/admin/players/" + playerId).asString());
        assertThat(json.getInteger("score")).isEqualTo(5);
        assertThat(json.getString("playerId")).isEqualTo(playerId);
        JsonArray achievements = json.getJsonArray("achievements");
        assertThat(achievements).hasSize(1);
        JsonObject object = achievements.getJsonObject(0);
        assertThat(object.getString("taskId")).isEqualTo(task);
        assertThat(object.getInteger("point")).isEqualTo(5);
    }

    @Test
    public void testThatScoreAreAccumulated() {
        String task1 = taskIds.get(0);
        String task2 = taskIds.get(1);

        JsonObject payload1 = new JsonObject()
            .put("transactionId", UUID.randomUUID().toString())
            .put("taskId", task1)
            .put("playerId", playerId)
            .put("url", "http://example.com")
            .put("score", 5);

        given().body(payload1.encode()).post("/score").then().statusCode(204);

        await().until(() -> new JsonObject(get("/admin/players/" + playerId).asString()).getInteger("score") == 5);

        JsonObject json = new JsonObject(get("/admin/players/" + playerId).asString());
        assertThat(json.getInteger("score")).isEqualTo(5);
        assertThat(json.getString("playerId")).isEqualTo(playerId);
        JsonArray achievements = json.getJsonArray("achievements");
        assertThat(achievements).hasSize(1);
        JsonObject object = achievements.getJsonObject(0);
        assertThat(object.getString("taskId")).isEqualTo(task1);
        assertThat(object.getInteger("point")).isEqualTo(5);

        JsonObject payload2 = new JsonObject()
            .put("transactionId", UUID.randomUUID().toString())
            .put("taskId", task2)
            .put("playerId", playerId)
            .put("url", "http://example.com")
            .put("score", 10);
        given().body(payload2.encode()).post("/score").then().statusCode(204);

        await().until(() -> new JsonObject(get("/admin/players/" + playerId).asString()).getInteger("score") == 15);

        json = new JsonObject(get("/admin/players/" + playerId).asString());
        assertThat(json.getInteger("score")).isEqualTo(10 + 5);
        assertThat(json.getString("playerId")).isEqualTo(playerId);
        achievements = json.getJsonArray("achievements");
        assertThat(achievements).hasSize(2);
        object = achievements.getJsonObject(0);
        assertThat(object.getString("taskId")).isEqualTo(task1);
        assertThat(object.getInteger("point")).isEqualTo(5);
        object = achievements.getJsonObject(1);
        assertThat(object.getString("taskId")).isEqualTo(task2);
        assertThat(object.getInteger("point")).isEqualTo(10);
    }

    @Test
    @Ignore("ignore it until it's fixed in the game")
    public void testThatAchievementsCannotBeRedone() {
        String task1 = taskIds.get(0);

        JsonObject payload1 = new JsonObject()
            .put("transactionId", UUID.randomUUID().toString())
            .put("taskId", task1)
            .put("playerId", playerId)
            .put("url", "http://example.com")
            .put("score", 5);

        given().body(payload1.encode()).post("/score").then().statusCode(204);

        await().until(() -> new JsonObject(get("/admin/players/" + playerId).asString()).getInteger("score") == 5);

        JsonObject json = new JsonObject(get("/admin/players/" + playerId).asString());
        assertThat(json.getInteger("score")).isEqualTo(5);
        assertThat(json.getString("playerId")).isEqualTo(playerId);
        JsonArray achievements = json.getJsonArray("achievements");
        assertThat(achievements).hasSize(1);
        JsonObject object = achievements.getJsonObject(0);
        assertThat(object.getString("taskId")).isEqualTo(task1);
        assertThat(object.getInteger("point")).isEqualTo(5);

        given().body(payload1.encode()).post("/score").then().statusCode(204);

        json = new JsonObject(get("/admin/players/" + playerId).asString());
        assertThat(json.getInteger("score")).isEqualTo(5);
        assertThat(json.getString("playerId")).isEqualTo(playerId);
        achievements = json.getJsonArray("achievements");
        assertThat(achievements).hasSize(1);
        object = achievements.getJsonObject(0);
        assertThat(object.getString("taskId")).isEqualTo(task1);
        assertThat(object.getInteger("point")).isEqualTo(5);
    }

    private JsonObject connect() {
        List<JsonObject> frames = new ArrayList<>();
        client.websocket("/game", socket -> {
            socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
            socket.writeFinalTextFrame(new JsonObject().put("type", "connection").encode());
        });

        await().until(() -> frames.size() >= 1);
        return frames.get(0);
    }

}