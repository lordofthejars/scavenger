package me.escoffier.keynote;

import io.reactivex.Completable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;

/**
 * The main verticle.
 */
public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> future) {
        ConfigRetriever retriever = ConfigRetriever.create(vertx);

        retriever.rxGetConfig()
            .flatMapCompletable(json -> {
                DeploymentOptions options = new DeploymentOptions().setConfig(json);
                Completable deployRankReporter = Completable.complete();
                if (Constants.getLocationAwareConfig(json).getBoolean("leaderboard-report-enable", false)) {
                    System.out.println("Deploying the final rank reporter");
                    deployRankReporter = vertx.rxDeployVerticle(FinalRankVerticle.class.getName(), options).toCompletable();
                }
                return
                    vertx.rxDeployVerticle(PlayerVerticle.class.getName(), options).toCompletable()
                        .andThen(vertx.rxDeployVerticle(GameServer.class.getName(), options).toCompletable())
                        .andThen(vertx.rxDeployVerticle(ActivePlayerVerticle.class.getName(), options).toCompletable())
                        .andThen(deployRankReporter);
            })
            .subscribe(CompletableHelper.toObserver(future));

    }
}
