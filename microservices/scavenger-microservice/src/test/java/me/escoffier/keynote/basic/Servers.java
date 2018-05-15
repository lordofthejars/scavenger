package me.escoffier.keynote.basic;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.Index;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.internal.PrivateGlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.server.core.admin.embeddedserver.EmbeddedServerAdminOperationHandler;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.server.hotrod.configuration.HotRodServerConfigurationBuilder;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Servers {

    private static final AtomicBoolean isLocalRunning = new AtomicBoolean(false);
    private static final List<Cache> caches = new ArrayList<>();

    private Servers() {
    }

    public static Closeable local() {
        final GlobalConfigurationBuilder globalBuilder = new GlobalConfigurationBuilder();
        globalBuilder.addModule(PrivateGlobalConfigurationBuilder.class).serverMode(true);
        final DefaultCacheManager cacheManager = new DefaultCacheManager(globalBuilder.build());

        cacheManager.defineConfiguration("SHOULD_NOT_BE_NEEDED",
            new ConfigurationBuilder().build());

        definePlayersCache(cacheManager);

        final HotRodServerConfigurationBuilder serverCfg =
            new HotRodServerConfigurationBuilder();
        serverCfg.defaultCacheName("SHOULD_NOT_BE_NEEDED");

        final HotRodServer server = new HotRodServer();
        server.start(serverCfg.build(), cacheManager);

        serverCfg.adminOperationsHandler(new EmbeddedServerAdminOperationHandler());
        caches.add(cacheManager.administration().getOrCreateCache("active", (String) null));
        caches.add(cacheManager.administration().getOrCreateCache("txs", (String) null));
        caches.add(cacheManager.administration().getOrCreateCache("tasks", (String) null));
        caches.add(cacheManager.administration().getOrCreateCache("admin", (String) null));
        caches.add(cacheManager.administration().getOrCreateCache("default0", (String) null));
        caches.add(cacheManager.getCache("players"));
        
        isLocalRunning.set(true);

        return () -> {
            server.stop();
            cacheManager.close();
        };
    }

    private static void definePlayersCache(DefaultCacheManager cacheManager) {
        final ConfigurationBuilder cfg = new ConfigurationBuilder();
        cfg.indexing().index(Index.LOCAL).autoConfig(true);
        cacheManager.defineConfiguration("players",
            cfg.build());
    }

    public static void reset() {
        caches.forEach(Cache::clear);
    }

    public static Closeable startIfNotRunning(Supplier<Closeable> server) {
        boolean isRunning = isLocalRunning.get();
        return isRunning
            ? () -> {}
            : server.get();
    }
}
