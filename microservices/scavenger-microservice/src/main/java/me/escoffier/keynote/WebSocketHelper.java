package me.escoffier.keynote;

import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.ServerWebSocket;
import io.vertx.reactivex.core.http.WebSocketFrame;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A few method to handle web sockets.
 */
public class WebSocketHelper {

    private static final Logger LOGGER = LogManager.getLogger("WS-Helper");

    public static JsonObject toJson(WebSocketFrame frame) {
        if (frame.textData().length() > 0) {
            // We only expect text frame.
            try {
                return new JsonObject(frame.textData());
            } catch (Exception e) {
                // Invalid JSON
                // LOGGER.error("Invalid frame: " + frame.textData(), e);
                return null;
            }
        } else {
            return null;
        }
    }

    public static void closeQuietly(ServerWebSocket socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
            // On purpose
        }
    }

    public static void closeQuietly(WebClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore me, already closed
            }
        }
    }
}
