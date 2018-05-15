package me.escoffier.keynote;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.ext.mail.MailClient;
import me.escoffier.keynote.jdg.AsyncCache;
import me.escoffier.keynote.jdg.CacheService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static me.escoffier.keynote.Constants.*;

/**
 * Waits for the game-over event and collect the top 20 players. It sends an email with the final ranking.
 */
public class FinalRankVerticle extends AbstractVerticle {


    private static final Logger LOGGER = LogManager.getLogger("FinalRankVerticle");
    /**
     * Map playerId -> Player
     */
    private AsyncCache<String, Player> players;

    /**
     * Map String -> String representing the states.
     */
    private AsyncCache<String, String> admin;

    @Override
    public void start(Future<Void> future) {
        Completable initPlayersMap = CacheService.get(vertx, config()).flatMap(cs -> cs.
            <String, Player>getIndexedCache(CACHE_PLAYERS))
            .doOnSuccess(ac -> this.players = ac)
            .toCompletable();

        Completable initAdminCache = CacheService.get(vertx, config()).flatMap(cs -> cs.
            <String, String>getIndexedCache(CACHE_ADMIN))
            .doOnSuccess(ac -> this.admin = ac)
            .toCompletable();

        MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(ADDRESS_ADMIN_CHANGES);
        consumer.handler(msg ->
            Single.just(msg)
                .map(Message::body)
                .flatMapMaybe(j -> admin.get("game"))
                .doOnSuccess(s -> LOGGER.info("From admin: " + s))
                .filter(state -> state.equalsIgnoreCase("game-over"))
                .flatMapSingle(x -> rank())
                .map(this::content)
                .flatMap(this::save)
                .doOnSuccess(list -> {
                    System.out.println("--------------------------------------------------");
                    System.out.println(list);
                    System.out.println("--------------------------------------------------");
                })
                .subscribe(
                    res -> LOGGER.info("Ranking generated"),
                    err -> LOGGER.error("Unable to send the mail", err)
                ));

        initPlayersMap
            .andThen(initAdminCache)
            .andThen(consumer.rxCompletionHandler())
            .subscribe(CompletableHelper.toObserver(future));
    }

    private Single<String> save(String content) {
        LOGGER.info("Saving file.");
        return vertx.fileSystem()
            .rxWriteFile("ranks.txt", Buffer.buffer(content))
            .doOnComplete(() -> LOGGER.info("'ranks.txt' file written"))
            .toSingleDefault(content);
    }

    private Single<List<Player>> rank() {
        return players.all().map(Map::values)
            .map(ArrayList::new)
            .map(collection -> {
                collection.sort(new ScoreComparator());
                return collection;
            });
    }

    private String content(List<Player> ranks) {
        StringBuilder content = new StringBuilder();
        content.append("Ranking of ").append(ranks.size()).append(" players\n");
        for (int i = 0; i < ranks.size(); i++) {
            int rank = i + 1;
            Player player = ranks.get(i);
            content
                .append(rank).append("\t")
                .append(player.getPlayerId()).append("\t")
                .append(player.getPlayerName()).append("\t")
                .append(player.getScore()).append("\t")
                .append(Player.decode(player.getEmail())).append("\n");
        }
        return content.toString();
    }

    private class ScoreComparator implements Comparator<Player> {

        @Override
        public int compare(Player p1, Player p2) {
            return Integer.compare(p2.getScore(), p1.getScore());
        }
    }
}
