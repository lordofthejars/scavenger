package me.escoffier.keynote;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.WebSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static me.escoffier.keynote.Restafari.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Check the connection sequence.
 */
public class ConnectionTest extends TestBase {


    private HttpClientOptions httpClientOptions = new HttpClientOptions().setDefaultHost("localhost").setDefaultPort(8080);
    private HttpClient client;

    @Before
    public void setUp() {
        client = vertx.createHttpClient(httpClientOptions);
    }

    @After
    public void tearDown() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            // Ignore me, client already closed.
        }
    }

    @Test
    public void testConnection() {
        List<JsonObject> frames = new ArrayList<>();
        client.websocket("/game", socket -> {
            socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
            socket.writeFinalTextFrame(new JsonObject().put("type", "connection").encode());
        });

        await().until(() -> frames.size() >= 1);

        assertThat(frames.get(0).getString("playerId")).isNotBlank();
        assertThat(frames.get(0).getString("data-center")).isNotBlank();
        assertThat(frames.get(0).getString("type")).isEqualTo("configuration");
        assertThat(frames.get(0).getJsonObject("tasks")).isNotEmpty();
        assertThat(frames.get(0).getString("party")).isEqualTo("true");
        assertThat(frames.get(0).getString("game")).isEqualTo("lobby");
    }

    @Test
    public void testReconnection() {
        List<JsonObject> frames = new ArrayList<>();
        AtomicReference<WebSocket> ref = new AtomicReference<>();
        client.websocket("/game", socket -> {
            ref.set(socket);
            socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
            socket.writeFinalTextFrame(new JsonObject().put("type", "connection").encode());
        });

        await().until(() -> frames.size() >= 1);

        String playerId = frames.get(0).getString("playerId");
        String playerName = frames.get(0).getString("username");
        assertThat(playerId).isNotBlank();
        assertThat(playerName).isNotBlank();
        assertThat(frames.get(0).getString("party")).isEqualTo("true");
        assertThat(frames.get(0).getString("game")).isEqualTo("lobby");

        ref.get().close();
        frames.clear();

        client.websocket("/game", socket -> {
            socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
            socket.writeFinalTextFrame(new JsonObject().put("type", "connection")
                .put("playerId", playerId).encode());
        });

        await().until(() -> frames.size() >= 1);

        assertThat(frames.get(0).getString("playerId")).isEqualTo(playerId);
        assertThat(frames.get(0).getString("username")).isEqualTo(playerName);
    }

    @Test
    public void testMultipleConnections() {
        List<ConnectingPlayer> players = new ArrayList<>();
        int number = 200;
        for (int i = 0; i < number; i++) {
            players.add(new ConnectingPlayer());
        }

        players.forEach(ConnectingPlayer::connect);

        await()
            .atMost(1, TimeUnit.MINUTES)
            .until(() -> {
                for (ConnectingPlayer player : players) {
                    if (!player.isDone()) {
                        return false;
                    }
                }
                return true;
            });

        JsonObject json = new JsonObject(get("/admin/players").asString());
        assertThat(json.size()).isEqualTo(number);

        String active = get("/admin/players/active").asString();
        assertThat(Integer.valueOf(active)).isEqualTo(number);

        players.forEach(ConnectingPlayer::close);

        players.forEach(ConnectingPlayer::check);

        await().until(() -> get("/admin/players/active").asString().equalsIgnoreCase("0"));
    }

    private class ConnectingPlayer {

        HttpClient client;
        WebSocket socket;
        private List<JsonObject> frames = new ArrayList<>();

        ConnectingPlayer() {
            client = vertx.createHttpClient(httpClientOptions);
        }

        void connect() {
            client.websocket("/game", socket -> {
                this.socket = socket;
                socket.textMessageHandler(frame -> frames.add(new JsonObject(frame)));
                socket.writeFinalTextFrame(new JsonObject().put("type", "connection").encode());
            });
        }

        void check() {
            assertThat(frames.get(0).getString("playerId")).isNotBlank();
            assertThat(frames.get(0).getString("username")).isNotBlank();
            assertThat(frames.get(0).getString("data-center")).isNotBlank();
            assertThat(frames.get(0).getJsonArray("achievements")).hasSize(0);
            assertThat(frames.get(0).getString("type")).isEqualTo("configuration");
            assertThat(frames.get(0).getJsonObject("tasks")).isNotEmpty();
        }

        boolean isDone() {
            return frames.size() >= 1;
        }

        void close() {
            if (socket != null) {
                socket.close();
            }
            client.close();
        }

    }

}
