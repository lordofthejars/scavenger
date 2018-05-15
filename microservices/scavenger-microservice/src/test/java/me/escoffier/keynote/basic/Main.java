package me.escoffier.keynote.basic;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.util.CloseableIteratorSet;

import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Main {

    public static void main(String[] args) {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.addServer()
            .host("localhost")
            .port(11222);
        RemoteCacheManager manager = new RemoteCacheManager(cb.build());
        RemoteCache<String, String> cache = manager.getCache("players");

        Set<Map.Entry<String, String>> entries = cache.entrySet();
        entries.forEach(entry -> System.out.println("Entry: " + entry.getKey() + " : " + entry.getValue()));
    }
}
