package me.escoffier.keynote.jdg;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.shareddata.LocalMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Testing purpose only.
 */
public class LocalMapAsyncCache<K, V> implements AsyncCache<K, V> {
    private final LocalMap<K, V> cache;
    private final String name;
    private final Vertx vertx;
    private String address;

    public LocalMapAsyncCache(Vertx vertx, String name) {
        this.vertx = Objects.requireNonNull(vertx, "Vert.x must be set");
        this.name = Objects.requireNonNull(name, "Name must be set");
        this.cache = vertx
            .sharedData().getLocalMap(name);
    }

    @Override
    public void listen(String address) {
        this.address = address;
    }

    @Override
    public Completable put(K k, V v) {
        Objects.requireNonNull(k, "The key must be set");
        Objects.requireNonNull(v, "The value must be set");
        V old = cache.put(k, v);
        if (old == null) {
            fireCreatedEvent(k);
        } else {
            fireUpdatedEvent(k);
        }

        return Completable.complete();
    }

    @Override
    public Completable put(K k, V v, long amount, TimeUnit tu) {
       return put(k, v);
    }

    private void fireUpdatedEvent(K k) {
        if (address != null) {
            vertx.eventBus().send(address, new JsonObject().put("event", "updated").put("entry", k));
        }
    }

    private void fireCreatedEvent(K k) {
        if (address != null) {
            vertx.eventBus().send(address, new JsonObject().put("event", "created").put("entry", k));
        }
    }

    private void fireRemovedEvent(K k) {
        if (address != null) {
            vertx.eventBus().send(address, new JsonObject().put("event", "removed").put("entry", k));
        }
    }

    @Override
    public Maybe<V> get(K k) {
        Objects.requireNonNull(k, "The key must be set");
        V value = cache.get(k);
        if (value == null) {
            return Maybe.empty();
        }
        return Maybe.just(value);
    }

    @Override
    public Single<V> get(K k, V def) {
        Objects.requireNonNull(k, "The key must be set");
        return get(k)
            .toSingle(def);
    }

    @Override
    public Completable remove(K k) {
        Objects.requireNonNull(k, "The key must be set");
        cache.remove(k);
        fireRemovedEvent(k);
        return Completable.complete();
    }

    @Override
    public Completable clear() {
        cache.clear();
        // TODO We should notify for all entries...
        return Completable.complete();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Single<Integer> size() {
        return Single.just(cache.size());
    }

    @Override
    public Single<Map<K, V>> all() {
        Map<K, V> map = new HashMap<>();
        Set<Map.Entry<K, V>> entries = cache.getDelegate().entrySet();
        entries.forEach(entry -> map.put(entry.getKey(), entry.getValue()));
        return Single.just(map);
    }

    @Override
    public Single<Boolean> replace(K key, V oldValue, V newValue) {
        boolean item = cache.replaceIfPresent(key, oldValue, newValue);
        if (item) {
            fireUpdatedEvent(key);
        }
        return Single.just(item);
    }
}
