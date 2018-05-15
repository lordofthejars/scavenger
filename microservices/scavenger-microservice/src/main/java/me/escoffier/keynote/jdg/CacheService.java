package me.escoffier.keynote.jdg;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Vertx;
import me.escoffier.keynote.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.annotations.ProtoSchemaBuilder;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CacheService {

    private final Vertx vertx;
    private final String url;
    private final Integer port;
    private final boolean useJDG;

    private static final Logger LOGGER = LogManager.getLogger("Cache-Service");
    private static CacheService CACHE;
    private RemoteCacheManager caches;
    private RemoteCacheManager indexedCaches;

    private Map<String, AsyncCache> cachesByName = new ConcurrentHashMap<>();

    public static synchronized Single<CacheService> get(Vertx vertx, JsonObject config) {
        if (CACHE != null) {
            return Single.just(CACHE);
        } else {
            CacheService service = new CacheService(vertx, config);
            return service.init()
                .doOnComplete(() -> CACHE = service)
                .toSingleDefault(service);
        }
    }

    /**
     * For testing only
     */
    public static synchronized void reset() {
        if (CACHE != null) {
            try {
                if (CACHE.caches != null) {
                    CACHE.caches.close();
                }
            } catch (IOException e) {
                // Ignore me.
            }
            try {
                if (CACHE.indexedCaches != null) {
                    CACHE.indexedCaches.close();
                }
            } catch (IOException e) {
                // Ignore me.
            }
        }
        CACHE = null;
    }

    private CacheService(Vertx vertx, JsonObject config) {
        this.vertx = vertx;

        String cn = config.getString("data-center", "localhost");
        JsonObject locations = config.getJsonObject("locations").getJsonObject(cn);

        url = locations.getString("jdg-url");
        port = locations.getInteger("jdg-port", 11222);
        useJDG = config.getBoolean("jdg-enable", url != null);

        LOGGER.info("{} - Cache service using " + (useJDG ? "JDG" : "LocalMap"), cn);
    }

    private Completable init() {
        if (!useJDG) {
            return Completable.complete();
        }
        return vertx
            .rxExecuteBlocking(remoteCacheManager())
            .flatMap(x -> vertx.rxExecuteBlocking(indexedCacheManager()))
            .toCompletable();
    }

    private Handler<Future<Void>> remoteCacheManager() {
        return future -> {
            ConfigurationBuilder cb = new ConfigurationBuilder();
            LOGGER.info("JDG location: {}:{}", url, port);
            cb.addServer()
                .host(url)
                .port(port);
            RemoteCacheManager manager = new RemoteCacheManager(cb.build());
            caches = manager;
            future.complete(null);
        };
    }

    private Handler<Future<Void>> indexedCacheManager() {
        return future -> {
            ConfigurationBuilder cb = new ConfigurationBuilder();
            LOGGER.info("Indexed JDG location: {}:{}", url, port);
            cb
                .addServer()
                    .host(url)
                    .port(port)
                .marshaller(ProtoStreamMarshaller.class)
            ;
            indexedCaches = new RemoteCacheManager(cb.build());

            SerializationContext serialCtx =
                ProtoStreamMarshaller.getSerializationContext(indexedCaches);

            ProtoSchemaBuilder protoSchemaBuilder = new ProtoSchemaBuilder();
            try {
                String playerSchemaFile = protoSchemaBuilder.fileName("player.proto")
                    .addClass(Player.class)
                    .build(serialCtx);

                RemoteCache<String, String> metadataCache = indexedCaches
                    .getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);

                metadataCache.put("player.proto", playerSchemaFile);

                LOGGER.info("Initialized player remote cache manager");
                future.complete(null);
            } catch (IOException e) {
                LOGGER.error("Unable to auto-generate player.proto", e);
                future.fail(e);
            }
        };
    }

    public <K, V> Single<AsyncCache<K, V>> getCache(String name) {
        @SuppressWarnings("unchecked") AsyncCache<K, V> cache = cachesByName
            .get(Objects.requireNonNull(name, "The name must be set"));
        if (cache != null) {
            return Single.just(cache);
        }
        return vertx
            .<AsyncCache<K, V>>rxExecuteBlocking(f -> retrieveCache(f, name))
            .doOnSuccess(c -> LOGGER.info("Cache " + name + " has been retrieved successfully"))
            .doOnSuccess(c -> cachesByName.put(name, c));
    }

    public <K, V> Single<AsyncCache<K, V>> getIndexedCache(String name) {
        @SuppressWarnings("unchecked") AsyncCache<K, V> cache = cachesByName
            .get(Objects.requireNonNull(name, "The name must be set"));
        if (cache != null) {
            return Single.just(cache);
        }
        return vertx
            .<AsyncCache<K, V>>rxExecuteBlocking(f -> retrieveIndexedCache(f, name))
            .doOnSuccess(c -> cachesByName.put(name, c));
    }

    /**
     * Executed on a worker thread.
     */
    private <K, V> void retrieveCache(Future<AsyncCache<K, V>> future, String name) {
        if (useJDG) {
            LOGGER.info("Retrieving cache {} using Infinispan", name);
            try {
                RemoteCache<K, V> cache = caches.getCache(name);
                future.complete(new InfinispanAsyncCache<>(vertx, cache));
            } catch (Exception e) {
                e.printStackTrace();
                future.fail(e);
            }
        } else {
            LOGGER.info("Retrieving cache {} using Vert.x Shared Data", name);
            future.complete(new LocalMapAsyncCache<>(vertx, name));
        }
    }


    /**
     * Executed on a worker thread.
     */
    private <K, V> void retrieveIndexedCache(Future<AsyncCache<K, V>> future, String name) {
        if (useJDG) {
            LOGGER.info("Retrieving indexed cache {} using Infinispan", name);
            RemoteCache<K, V> cache = indexedCaches.getCache(name);
            LOGGER.info("Retrieved indexed cache {} using Infinispan", name);
            future.complete(new InfinispanAsyncCache<>(vertx, cache));
        } else {
            LOGGER.info("Retrieving cache {} using Vert.x Shared Data", name);
            future.complete(new LocalMapAsyncCache<>(vertx, name));
        }
    }


}
