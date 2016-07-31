package software.reliabletx.camel;

import software.reliabletx.camel.activemq.TestEmbeddedBroker;

public abstract class ActiveMQTestCase extends SpringTestCase {
    private TestEmbeddedBroker testEmbeddedBroker;

    public void setUpActiveMQ(String springResourcesFile) throws Exception {
        /* Start-up the broker before creating the Spring context so that
         * other things instantiated via Spring don't try to start their own
         * embedded broker when they first see the embedded broker URL. */
        this.testEmbeddedBroker = new TestEmbeddedBroker();
        testEmbeddedBroker.startUpBroker();

        setUpSpring(springResourcesFile);
    }

    public TestEmbeddedBroker getEmbeddedBroker() {
        assertNotNull(testEmbeddedBroker);
        return testEmbeddedBroker;
    }
}
