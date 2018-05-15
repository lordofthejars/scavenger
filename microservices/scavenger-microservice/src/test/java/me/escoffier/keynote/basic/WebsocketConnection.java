package me.escoffier.keynote.basic;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.keynote.messages.PingFrame;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class WebsocketConnection {

    static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static long task;
    private static Vertx vertx;


    public static void main(String[] args) {
        vertx = Vertx.vertx();

        String host = "scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-aws.sysdeseng.com";
        int port = 443;
        

        HttpClientOptions options = new HttpClientOptions()
            .setDefaultHost(host)
            .setDefaultPort(port)
            .setSsl(true)
            .setTrustAll(true);
        vertx.createHttpClient(options).websocket("/game", socket -> {
            LocalDateTime now = LocalDateTime.now();
            System.out.println("Socket opened at " + dtf.format(now));

            socket.closeHandler(v -> closed(null));
            socket.exceptionHandler(WebsocketConnection::closed);
            socket.textMessageHandler(s -> {
                System.out.println("Received " + s + " at " + dtf.format(LocalDateTime.now()));
            });

            socket.writeFinalTextFrame("{\"type\":\"connection\"}");

            task = vertx.setPeriodic(20000, l -> {
                socket.writeFinalTextFrame(PingFrame.INSTANCE.encode());
                System.out.println("Socket still opened at " + dtf.format(LocalDateTime.now()));
            });
        });
    }

    private static void closed(Throwable e) {
        System.out.println("Socket closed at " + dtf.format(LocalDateTime.now()));
        if (e != null) {
            e.printStackTrace();
        }
        vertx.cancelTimer(task);
    }
}
