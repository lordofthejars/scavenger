package me.escoffier.keynote;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.keynote.jdg.CacheService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static me.escoffier.keynote.Restafari.get;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.Is.is;

@RunWith(VertxUnitRunner.class)
public class TestBase {

    protected static Vertx vertx;

    public String getConfiguration() {
        return "src/main/conf/my-dev-configuration.json";
    }


    static {
    }

    public void init() throws Exception {
        // Do nothing.
    }

    public void cleanup() throws Exception {
    }
    
    @Before
    public void startApplication() throws Exception {
        System.setProperty("vertx-config-path", getConfiguration());
        vertx = Vertx.vertx();
        init();
        String name = MainVerticle.class.getName();
        CountDownLatch latch = new CountDownLatch(1);

        vertx.deployVerticle(name, v -> {
            if (v.succeeded()) {
                latch.countDown();
            } else {
                v.cause().printStackTrace();
            }
        });
        latch.await(2, TimeUnit.MINUTES);
        
        await()
            .pollDelay(2, TimeUnit.SECONDS)
            .atMost(60, TimeUnit.SECONDS)
            .until(() -> {
                try {
                    return get("/").getStatusCode();
                } catch (Exception e) {
                    return -1;
                }
            }, is(200));
    }

    @After
    public void stopApplication() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        vertx.close(x -> latch.countDown());
        latch.await();
        CacheService.reset();
        cleanup();
        Restafari.reset();
        System.clearProperty("vertx-config-path");
    }

    @Test
    public void testNothing() {
        // Do nothing.
    }
}
