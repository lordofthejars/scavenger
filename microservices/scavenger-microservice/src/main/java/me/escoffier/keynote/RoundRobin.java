package me.escoffier.keynote;

import java.util.Collections;
import java.util.List;

/**
 * Implement a round robin based on a list of urls.
 */
public class RoundRobin<T> {

    private final List<T> list;
    private int current = 0;

    public RoundRobin(List<T> list) {
        this.list = Collections.unmodifiableList(list);
    }

    public T get() {
        T item = list.get(current);
        current = current + 1;
        if (current >= list.size()) {
            current = 0;
        }
        return item;
    }
}
