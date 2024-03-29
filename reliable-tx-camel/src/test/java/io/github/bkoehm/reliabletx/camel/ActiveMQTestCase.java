/*-
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The
 * ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.github.bkoehm.reliabletx.camel;

import io.github.bkoehm.reliabletx.camel.activemq.TestEmbeddedBroker;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Brian Koehmstedt
 */
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
