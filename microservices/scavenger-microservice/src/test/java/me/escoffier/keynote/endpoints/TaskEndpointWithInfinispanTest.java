package me.escoffier.keynote.endpoints;

import me.escoffier.keynote.basic.Servers;

/**
 * Test the REST API to manage tasks.
 */
public class TaskEndpointWithInfinispanTest extends TaskEndpointTest {

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

    //TODO Test listen.

}
