package me.escoffier.keynote;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the round robin.
 */
public class RoundRobinTest {


    @Test
    public void testOnSingletonList() {
        RoundRobin<String> dispatcher = new RoundRobin<>(Collections.singletonList("a"));
        for (int i = 0; i < 10; i++) {
            assertThat(dispatcher.get()).isEqualTo("a");
        }
    }

    @Test
    public void testOnRegularList() {
        RoundRobin<String> dispatcher = new RoundRobin<>(Arrays.asList("a", "b", "c"));
        Map<String, AtomicInteger> counts = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            String item = dispatcher.get();
            AtomicInteger count = counts.computeIfAbsent(item, x -> new AtomicInteger());
            count.incrementAndGet();
        }
        assertThat(counts).hasSize(3);
        assertThat(counts.values().stream().map(AtomicInteger::get).collect(Collectors.toList()))
            .containsExactly(4, 4, 4);
    }

    @Test
    public void testWithLargerList() {
        RoundRobin<String> dispatcher = new RoundRobin<>(Arrays.asList("a", "b", "c", "d", "e", "f"));
        Map<String, AtomicInteger> counts = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            String item = dispatcher.get();
            AtomicInteger count = counts.computeIfAbsent(item, x -> new AtomicInteger());
            count.incrementAndGet();
        }
        assertThat(counts).hasSize(6);
        assertThat(counts.values().stream().map(AtomicInteger::get).collect(Collectors.toList()))
            .containsExactly(2, 2, 2, 2, 2, 2);
    }

}