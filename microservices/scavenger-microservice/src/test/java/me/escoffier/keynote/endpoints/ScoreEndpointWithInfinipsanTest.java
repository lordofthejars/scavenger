package me.escoffier.keynote.endpoints;

import me.escoffier.keynote.basic.Servers;

/**
 * Checks the score endpoint.
 */
public class ScoreEndpointWithInfinipsanTest extends ScoreEndpointTest {

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