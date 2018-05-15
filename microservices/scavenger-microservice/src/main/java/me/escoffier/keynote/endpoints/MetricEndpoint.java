package me.escoffier.keynote.endpoints;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.eventbus.MessageConsumer;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MetricEndpoint extends Endpoint {

    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    public MetricEndpoint(Vertx vertx, JsonObject config) {
        super(vertx, config);
    }

    @Override
    public Completable init() {
        MessageConsumer<JsonObject> times = vertx.eventBus().consumer("metrics/times");
        MessageConsumer<JsonObject> responses = vertx.eventBus().consumer("metrics/responses");

        times.handler(msg -> {
            JsonObject body = msg.body();
            String name = body.getString("name");
            long duration = body.getLong("duration", -1L);

            if (name != null && duration > 0) {
                timer(name, duration);
            }
        });

        responses.handler(msg -> {
            JsonObject body = msg.body();
            String name = body.getString("name");
            int status = body.getInteger("status", -1);

            if (name != null && status > 0) {
                addResponse(name, status);
            }
        });

        return times.rxCompletionHandler()
            .andThen(responses.rxCompletionHandler());
    }

    private void timer(String name, long time) {
        Timer timer = registry.find(name).timer();
        if (timer == null) {
            timer = Timer.builder(name)
                .percentilePrecision(1)
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .distributionStatisticExpiry(Duration.ofHours(1)).register(registry);
        }
        timer.record(time, TimeUnit.MILLISECONDS);
    }

    private void addResponse(String name, int status) {
        registry.counter(name + "-" + status).increment();
    }

    public void clear(RoutingContext rc) {
        LogManager.getLogger("Metrics").info("Clearing metrics");
        registry.close();
        registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        rc.response().end("Metrics cleared");
    }

    public void get(RoutingContext rc) {
        JsonObject times = new JsonObject();
        List<Meter> meters = registry.getMeters();

        meters.stream().filter(meter -> meter instanceof Timer)
            .map(m -> (Timer) m)
            .forEach(m -> {
                JsonObject x = new JsonObject();
                x.put("count", m.count());
                x.put("mean", m.mean(TimeUnit.MILLISECONDS));
                x.put("max", m.max(TimeUnit.MILLISECONDS));
                x.put("total", m.totalTime(TimeUnit.MILLISECONDS));
                x.put("average", m.totalTime(TimeUnit.MILLISECONDS) / m.count());
                ValueAtPercentile[] percentiles = m.takeSnapshot().percentileValues();
                x.put("percentiles", Arrays.toString(percentiles));
                times.put(m.getId().getName(), x);
            });

        JsonObject results = new JsonObject();
        meters.stream().filter(meter -> meter instanceof Counter)
            .map(m -> (Counter) m)
            .forEach(c -> results.put(c.getId().getName(), c.count()));

        rc.response().end(new JsonObject().put("durations", times).put("requests", results).encodePrettily());
    }

    public static void report(Vertx vertx, String name, long duration) {
        vertx.eventBus().send("metrics/times", new JsonObject().put("name", name).put("duration", duration));
    }

    public static void report(Vertx vertx, String name, int status) {
        vertx.eventBus().send("metrics/responses", new JsonObject().put("name", name).put("status", status));
    }

    private static List<Inflight> inflights = new CopyOnWriteArrayList<>();

    public static void beginInFlight(Vertx vertx, String name, String id, long maxTime) {
        inflights.add(new Inflight(id));
        vertx.setTimer(maxTime, x -> {
            if (removeInflight(id)) {
                vertx.eventBus().send("metrics/responses", new JsonObject().put("name", name).put("status", 408));
            }
        });
    }

    public static void reportInflight(Vertx vertx, String name, String id) {
        Inflight inflight = inflights.stream().filter(i -> i.tx.equals(id)).findFirst().orElse(null);
        if (removeInflight(id)) {
            vertx.eventBus().send("metrics/responses", new JsonObject().put("name", name).put("status", 200));
            if (inflight != null) {
                vertx.eventBus().send("metrics/times",
                    new JsonObject().put("name", "serverless").put("duration", System.currentTimeMillis() - inflight.begin));
            }
        }
    }

    private static boolean removeInflight(String tx) {
        return inflights.stream().filter(i -> i.tx.equals(tx)).findAny().map(i -> inflights.remove(i)).orElse(false);
    }

    public void prometheus(RoutingContext rc) {
        // I'm not sure it's blocking.
        vertx.<String>rxExecuteBlocking(future ->
            future.complete(registry.scrape())
        ).subscribe(
            s -> rc.response().end(s),
            rc::fail
        );
    }

    private static class Inflight {
        final String tx;
        final long begin;

        Inflight(String tx) {
            this.tx = tx;
            this.begin = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Inflight && ((Inflight) obj).tx.equals(tx);
        }
    }
}
