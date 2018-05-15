package me.escoffier.keynote.endpoints;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.WebSocket;
import me.escoffier.keynote.TestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static me.escoffier.keynote.UploadTest.createPictureFrame;
import static me.escoffier.keynote.UploadTest.getScoreFrameForTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Checks the Admin endpoint behavior.
 */
public class AdminEndpointTest extends TestBase {

    private HttpClientOptions httpClientOptions = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080);
    private HttpClient client;

    @Override
    public String getConfiguration() {
        return "src/test/resources/my-dev-configuration-without-infinispan.json";
    }

    @Before
    public void setUp() {
        client = vertx.createHttpClient(httpClientOptions);
    }

    @After
    public void tearDown() {
        client.close();
    }

    @Test
    public void testConfigurationFrameAfterUpdate() {
        List<JsonObject> frames = new ArrayList<>();
        connectPlayer(frames);

        await().until(() -> frames.size() >= 1);

        checkConfiguration(frames.get(0), "true", "lobby");

        setPartyMode(false);
        await().until(() -> frames.size() >= 2);
        checkConfiguration(frames.get(1), "false", "lobby");

        setGameMode("play");
        await().until(() -> frames.size() >= 3);
        checkConfiguration(frames.get(2), "false", "play");
    }

    private void setPartyMode(boolean enabled) {
        client.websocket("/admin", socket ->
            socket.writeFinalTextFrame(new JsonObject().put("type", "party-island")
                .put("token", "foo")
                .put("enabled", Boolean.toString(enabled)).encode()));
    }

    private void setGameMode(String mode) {
        client.websocket("/admin", socket ->
            socket.writeFinalTextFrame(new JsonObject().put("type", "game")
                .put("token", "foo")
                .put("state", mode).encode()));
    }

    @Test
    public void testTogglingPartyMode() {
        List<JsonObject> frames = new ArrayList<>();
        connectPlayer(frames);

        await().until(() -> frames.size() >= 1);
        checkConfiguration(frames.get(0), "true", "lobby");

        setPartyMode(false);
        await().until(() -> frames.size() >= 2);
        checkConfiguration(frames.get(1), "false", "lobby");

        setPartyMode(true);
        await().until(() -> frames.size() >= 3);
        checkConfiguration(frames.get(2), "true", "lobby");
    }

    private void checkConfiguration(JsonObject frame, String expectedPartyMode, String expectedGameMode) {
        assertThat(frame.getString("playerId")).isNotBlank();
        assertThat(frame.getString("data-center")).isNotBlank();
        assertThat(frame.getString("type")).isEqualTo("configuration");
        assertThat(frame.getJsonObject("tasks")).isNotEmpty();
        assertThat(frame.getString("party")).isEqualTo(expectedPartyMode);
        assertThat(frame.getString("game")).isEqualTo(expectedGameMode);
    }

    @Test
    public void testGameModeMode() {
        List<JsonObject> frames = new ArrayList<>();
        connectPlayer(frames);

        await().until(() -> frames.size() >= 1);
        checkConfiguration(frames.get(0), "true", "lobby");

        setGameMode("play");
        await().until(() -> frames.size() >= 2);
        checkConfiguration(frames.get(1), "true", "play");

        setGameMode("lobby");
        await().until(() -> frames.size() >= 3);
        checkConfiguration(frames.get(2), "true", "lobby");

    }

    private void connectPlayer(List<JsonObject> frames) {
        AtomicReference<WebSocket> reference = new AtomicReference<>();
        client.websocket("/game", socket -> {
            socket.textMessageHandler(frame -> {
                JsonObject object = new JsonObject(frame);
                if (!object.getString("type").equals("ping")) {
                    frames.add(object);
                }
            });
            reference.set(socket);
            socket.writeFinalTextFrame(new JsonObject().put("type", "connection").encode());
        });

        await().untilAtomic(reference, is(notNullValue()));
    }

    private void nap() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // Ignore me
        }
    }

}