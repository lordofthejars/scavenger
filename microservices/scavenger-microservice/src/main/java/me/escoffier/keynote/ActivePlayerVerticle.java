package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import me.escoffier.keynote.messages.PlayerEventMessage;
import org.apache.logging.log4j.LogManager;

import static me.escoffier.keynote.Constants.ADDRESS_ACTIVE;
import static me.escoffier.keynote.Constants.CACHE_ACTIVE_PLAYERS;

/**
 * Tracks active player.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class ActivePlayerVerticle extends AbstractVerticle {

    private AsyncCache<String, String> active;
    private String cloud;

    @Override
    public void start(Future<Void> done) {
        cloud = config().getString("data-center");
        // Get cache
        Completable initActivePlayerMap = CacheService.get(vertx, config()).flatMap(cs -> cs.
            <String, String>getCache(CACHE_ACTIVE_PLAYERS))
            .doOnSuccess(ac -> this.active = ac)
            .toCompletable();

        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(ADDRESS_ACTIVE);
        consumer.handler(json -> {
            PlayerEventMessage message = PlayerEventMessage.fromJson(json.body());
            switch (message.event()) {
                case ARRIVAL:
                    onArrival(message.player());
                    break;
                case DEPARTURE:
                    onDeparture(message.player());
                    break;
            }
        });

        Completable initConsumer = consumer.rxCompletionHandler();

        initActivePlayerMap
            .andThen(initConsumer)
            .subscribe(CompletableHelper.toObserver(done));
    }

    private void onArrival(String player) {
        active.put(player, cloud).subscribe(
            () -> {
                // Do nothing.
            },
            err -> LogManager.getLogger("Active-Player-Verticle")
                .error("Unable to write in the active cache (arrival)", err)
        );
    }

    private void onDeparture(String player) {
        active.remove(player).subscribe(
            () -> {
                // Do nothing.
            },
            err -> LogManager.getLogger("Active-Player-Verticle")
                .error("Unable to write in the active cache (departure)", err)
        );
    }
}
