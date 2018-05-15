package me.escoffier.keynote;

import me.escoffier.keynote.basic.Servers;

/**
 * Test connection followed by an upload. It "waits" until it get the score frame.
 */
public class UploadWithInfinispanTest extends UploadTest {

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
