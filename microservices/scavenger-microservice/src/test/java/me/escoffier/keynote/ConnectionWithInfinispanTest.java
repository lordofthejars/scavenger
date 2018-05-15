package me.escoffier.keynote;

import me.escoffier.keynote.basic.Servers;
import org.junit.Test;

/**
 * Check the connection sequence.
 */
public class ConnectionWithInfinispanTest extends ConnectionTest {


    @Override
    public String getConfiguration() {
        return "src/test/resources/my-dev-configuration-with-infinispan.json";
    }

    @Override
    public void init() {
        Servers.startIfNotRunning(Servers::local);
    }

    @Override
    public void cleanup() {
        Servers.reset();
    }
}
