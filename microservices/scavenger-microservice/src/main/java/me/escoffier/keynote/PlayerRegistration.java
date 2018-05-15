package me.escoffier.keynote;

import io.vertx.reactivex.core.eventbus.MessageConsumer;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class PlayerRegistration {

    private final String playerId;
    private final MessageConsumer consumer;

    public PlayerRegistration(String playerId, MessageConsumer consumer) {
        this.playerId = playerId;
        this.consumer = consumer;
    }

    public String playerId() {
        return playerId;
    }

    public MessageConsumer consumer() {
        return consumer;
    }

}
