package me.escoffier.keynote;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.WebSocket;
import me.escoffier.keynote.basic.FakeUser;
import me.escoffier.keynote.messages.Frame;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test connection followed by an upload. It "waits" until it get the score frame.
 */
public class UploadTest extends TestBase {

    private HttpClientOptions httpClientOptions = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080);
    private HttpClient client;

    @Before
    public void setUp() {
        client = vertx.createHttpClient(httpClientOptions);
    }

    @After
    public void tearDown() {
        client.close();
    }

    @Test
    public void testSingleUpload() {
        List<JsonObject> frames = new ArrayList<>();
        AtomicReference<WebSocket> reference = new AtomicReference<>();
        client.websocket("/game", socket -> {
            reference.set(socket);
            socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
            socket.writeFinalTextFrame(new JsonObject().put("type", Frame.TYPE.CONNECTION.getName()).encode());
        });

        await().until(() -> frames.size() >= 1);

        assertThat(frames.get(0).getString("type")).isEqualTo(Frame.TYPE.CONFIGURATION.getName());
        assertThat(frames.get(0).getString("playerId")).isNotBlank();
        assertThat(frames.get(0).getString("data-center")).isNotBlank();
        assertThat(frames.get(0).getString("type")).isEqualTo("configuration");
        assertThat(frames.get(0).getJsonObject("tasks")).isNotEmpty();

        String playerId = frames.get(0).getString("playerId");
        JsonObject tasks = frames.get(0).getJsonObject("tasks");
        List<String> taskIds = new ArrayList<>(tasks.fieldNames());
        Collections.shuffle(taskIds);
        String task = taskIds.get(0);

        boolean success = false;
        JsonObject object = null;
        String tx = null;
        while (!success) {
            JsonObject frame = createPictureFrame(playerId, task);
            tx = frame.getString("transactionId");
            reference.get().writeFinalTextFrame(frame.toString());
            String refToTransaction = tx;
            object = await().until(() -> getScoreFrameForTransaction(frames, refToTransaction), is(notNullValue()));

            assertThat(object.getString("playerId")).isEqualTo(playerId);
            assertThat(object.getString("taskId")).isEqualTo(task);

            success = object.getBoolean("scored", false);
            if (! success) {
                assertThat(object.getInteger("score")).isEqualTo(0);
                assertThat(object.getBoolean("scored")).isFalse();
                assertThat(object.getInteger("total")).isEqualTo(0);
                assertThat(object.getJsonArray("achievements")).hasSize(0);
            }
        }
        Integer point = tasks.getJsonObject(task).getInteger("point");
        assertThat(object.getInteger("score")).isEqualTo(point);
        assertThat(object.getInteger("total")).isEqualTo(point);
        assertThat(object.getBoolean("scored")).isTrue();
        assertThat(object.getJsonArray("achievements")).hasSize(1);
        JsonObject achievement = object.getJsonArray("achievements").getJsonObject(0);
        assertThat(achievement.getString("taskId")).isEqualTo(task);
        assertThat(achievement.getString("transactionId")).isEqualTo(tx);
        assertThat(achievement.getInteger("point")).isEqualTo(point);
    }

    @Test
    public void testMultipleUpload() {
        List<UploadingPlayer> players = new ArrayList<>();
        int number = 100;
        for (int i = 0; i < number; i++) {
            players.add(new UploadingPlayer());
        }

        players.forEach(UploadingPlayer::play);

        await().atMost(30, TimeUnit.SECONDS).until(() -> {
            for (UploadingPlayer player : players) {
                if (!player.isDone()) {
                    return false;
                }
            }
            return true;
        });

        players.forEach(UploadingPlayer::check);
    }

    private class UploadingPlayer {

        HttpClient client;
        WebSocket socket;
        private List<JsonObject> frames = new ArrayList<>();
        private String playerId;
        private String task;
        private String tx;
        private boolean gotScore;
        private int score;
        Map<String, JsonObject> scored = new LinkedHashMap<>();
        private List<String> taskIds;

        UploadingPlayer() {
            client = vertx.createHttpClient(httpClientOptions);
        }

        void connect() {
            client.websocket("/game", socket -> {
                this.socket = socket;
                socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
                socket.writeFinalTextFrame(new JsonObject().put("type", Frame.TYPE.CONNECTION.toString()).encode());
            });
        }


        private void pickAnotherTask() {
            task = taskIds.get(1);
        }

        void awaitUntilConnected() {
            await().until(() -> frames.size() >= 1);
            assertThat(frames.get(0).getString("type")).isEqualTo(Frame.TYPE.CONFIGURATION.getName());
            assertThat(frames.get(0).getString("playerId")).isNotBlank();
            assertThat(frames.get(0).getString("data-center")).isNotBlank();
            assertThat(frames.get(0).getString("type")).isEqualTo("configuration");
            assertThat(frames.get(0).getJsonObject("tasks")).isNotEmpty();

            playerId = frames.get(0).getString("playerId");
            JsonObject tasks = frames.get(0).getJsonObject("tasks");
            taskIds = new ArrayList<>(tasks.fieldNames());
            Collections.shuffle(taskIds);
            task = taskIds.get(0);
        }

        void upload() {
            JsonObject frame = createPictureFrame(playerId, task);
            tx = frame.getString("transactionId");
            try {
                socket.writeFinalTextFrame(frame.encode());
            } catch (Exception e) {
                // socket closed, ignore.
            }
        }

        void awaitForScore() {
            JsonObject object = await().atMost(1, TimeUnit.MINUTES)
                .until(() -> getScoreFrameForTransaction(frames, tx), is(notNullValue()));
            if (object.getBoolean("scored")) {
                scored.put(task, object);
                score = object.getInteger("score");
            }
            gotScore = true;
        }

        private void check(JsonObject object, String task) {
            assertThat(object.getString("playerId")).isEqualTo(playerId);
            assertThat(object.getString("taskId")).isEqualTo(task);
            assertThat(object.getInteger("score")).isGreaterThan(0);
            assertThat(object.getInteger("total")).isGreaterThan(0);
            assertThat(object.getBoolean("scored")).isTrue();
            assertThat(object.getJsonArray("achievements").size()).isGreaterThan(0);
            JsonObject achievement =
                object.getJsonArray("achievements").stream()
                    .map(o -> (JsonObject) o)
                    .filter(o -> o.getString("taskId").equalsIgnoreCase(task))
                    .findAny()
                    .orElseThrow(() -> new IllegalStateException("Unable to find achievement for task " + task));
            assertThat(achievement.getString("taskId")).isEqualTo(task);
            assertThat(achievement.getString("transactionId")).isNotBlank();
            assertThat(achievement.getInteger("point")).isGreaterThan(0);
        }

        boolean isDone() {
            return gotScore;
        }

        void close() {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    // Ignore me, socket already closed.
                }
            }
            client.close();
        }

        void play() {
            new Thread(() -> {
                connect();
                awaitUntilConnected();
                while (score == 0) {
                    upload();
                    awaitForScore();
                }
                pickAnotherTask();
                int currentScore = score;
                while (score == currentScore) {
                    upload();
                    awaitForScore();
                }
                close();
            }).start();
        }

        void check() {
            scored.forEach((t, f) -> check(f, t));
        }
    }


    public static JsonObject getScoreFrameForTransaction(List<JsonObject> frames, String tx) {
        for (JsonObject json : frames) {
            if (Frame.TYPE.SCORE.getName().equalsIgnoreCase(json.getString("type"))
                && tx.equalsIgnoreCase(json.getString("transactionId"))) {
                return json;
            }
        }
        return null;
    }

    public static JsonObject createPictureFrame(String playerId, String task) {
        return new JsonObject()
            .put("type", Frame.TYPE.PICTURE.toString())
            .put("playerId", playerId)
            .put("taskId", task)
            .put("transactionId", UUID.randomUUID().toString())
            .put("metadata", new JsonObject().put("format", "jpg"))
            .put("picture", FakeUser.encode("src/test/resources/I love coffee.jpg"));
    }

}
