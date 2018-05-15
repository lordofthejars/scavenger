package me.escoffier.keynote.basic;

import io.vertx.core.json.JsonObject;
import okhttp3.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.UUID;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class FakeUser {

    private String userName;
    private String playerId;
    private OkHttpClient client;

    public WebSocket socket;

    private JsonObject configuration;
    private JsonObject tasks;


    public FakeUser() {
    }

    public FakeUser(String id) {
        this.playerId = id;
    }


    public void run(String root, String conf) throws IOException {
        tasks = getTasks(conf);
        this.client = OkHttpUtils.getUnsafeOkHttpClient();

        new Thread(() -> {
            Request request = new Request.Builder()
                .url(root + "/game")
                .build();

            client.newWebSocket(request, new WebSocketListener() {

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    socket = webSocket;
                    connect();
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    System.out.println("Received: " + text);
                    JsonObject json = new JsonObject(text);
                    String type = json.getString("type");

                    switch (type) {
                        case "configuration" : onConfiguration(json); break;
                        case "leaderboard" : onLeaderboard(json); break;
                        case "score" : onScore(json); break;
                        case "ping" : break;
                        default: System.out.println("Unknown type: " + type + ", " + json.encode());
                    }
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    System.out.println("Web Socket closed: " + code + ", " + reason);
                    socket = null;
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    System.out.println("Web Socket failure");
                    t.printStackTrace();
                    socket = null;
                }
            });
        }).start();
    }

    private JsonObject getTasks(String conf) throws IOException {
        File file = new File(conf);
        if (! file.isFile()) {
            throw new IllegalArgumentException("The configuration file does not exist: " + file.getAbsolutePath());
        }

        JsonObject object = new JsonObject(new String(Files.readAllBytes(file.toPath()), "UTF-8"));
        return object.getJsonObject("tasks");
    }

    private void onScore(JsonObject json) {
        
    }

    private void onLeaderboard(JsonObject json) {
        
    }

    private void onConfiguration( JsonObject json) {
        configuration = json;
        playerId = json.getString("playerId");
        userName = json.getString("username");

        // Post a picture
        JsonObject pf = new JsonObject()
            .put("type", "picture")
            .put("playerId", playerId)
            .put("taskId", tasks.fieldNames().iterator().next())
            .put("transactionId", UUID.randomUUID().toString())
            .put("picture", encode("src/test/resources/I love coffee.jpg"))
            .put("metadata", new JsonObject().put("format", "jpg"));

        socket.send(pf.encode());
    }

    private void connect() {
        JsonObject json = new JsonObject()
            .put("type", "connection");
        if (playerId != null) {
            json.put("playerId", playerId);
        }
        socket.send(json.encode());
    }

    public static String encode(String path) {
        File originalFile = new File(path);
        String encodedBase64 = null;
        try {
            FileInputStream fileInputStreamReader = new FileInputStream(originalFile);
            byte[] bytes = new byte[(int)originalFile.length()];
            fileInputStreamReader.read(bytes);
            encodedBase64 = Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedBase64;
    }
}
