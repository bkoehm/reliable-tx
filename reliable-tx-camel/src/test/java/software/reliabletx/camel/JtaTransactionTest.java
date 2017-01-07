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
package software.reliabletx.camel;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultProducerTemplate;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import software.reliabletx.camel.activemq.QueueStatistics;

/**
 * Tests reliable transactions for Camel using a JTA transaction manager.
 * 
 * @author Brian Koehmstedt
 */
public class JtaTransactionTest extends ActiveMQTestCase {
    protected Logger log = LoggerFactory.getLogger(ActiveMQTestCase.class);

    private static final int WAIT_FOR_REPLY_TIMEOUT = 5000; // milliseconds

    private static final String testTransactedQueueName = "testTransactedQueue";
    private static final String testFailingConsumerWithReplyExceptionsTransactedQueueName = "testFailingConsumerWithReplyExceptionsTransactedQueue";
    private static final String testFailingConsumerWithExchangeFailuresTransactedQueueName = "testFailingConsumerWithExchangeFailuresTransactedQueue";

    protected Endpoint testTransactedQueueEndpoint;
    protected Endpoint testTransactedQueueEndpointForProducer;

    protected Endpoint testFailingConsumerWithReplyExceptionsTransactedQueueEndpoint;
    protected Endpoint testFailingConsumerWithExchangeFailuresTransactedQueueEndpoint;

    protected Endpoint testFailingConsumerWithReplyExceptionsTransactedQueueEndpointForProducer;
    protected Endpoint testFailingConsumerWithExchangeFailuresTransactedQueueEndpointForProducer;

    protected DefaultCamelContext getCamelContext() {
        return getBean("jtaCamelContext", DefaultCamelContext.class);
    }

    /* request-reply producers need a different context because they can't be
     * transactional */
    protected DefaultCamelContext getProducerCamelContext() {
        return getBean("ntCamelContext", DefaultCamelContext.class);
    }

    protected PlatformTransactionManager getTransactionManager() {
        return getBean("jtaTransactionManager", PlatformTransactionManager.class);
    }

    protected ReliableTxConsumerBuilder getConsumerBuilder() {
        return getBean("consumerBuilder", ReliableTxConsumerBuilder.class);
    }

    /* The ProducerTemplate, which is the client for sending messages. */
    protected DefaultProducerTemplate getProducerTemplate() {
        DefaultProducerTemplate template = getBean("producer", DefaultProducerTemplate.class);
        if (!template.isStarted()) {
            throw new RuntimeException("Couldn't start template");
        }
        return template;
    }

    protected TestReceiverBean getTestReceiverBean() {
        return getBean("testReceiverBean", TestReceiverBean.class);
    }

    @Override
    public void setUp() throws Exception {
        /* Start test ActiveMQ embedded broker and initialize Spring. */
        setUpActiveMQ("resources-atomikos.xml");

        /* Print out DLQ messages to the log. Establishing the DLQs ahead of
         * time also initializes statistics numbers to 0 for these DLQs. */
        getEmbeddedBroker().setUpDlqConsumer("DLQ." + testTransactedQueueName);
        getEmbeddedBroker().setUpDlqConsumer("DLQ." + testFailingConsumerWithReplyExceptionsTransactedQueueName);
        getEmbeddedBroker().setUpDlqConsumer("DLQ." + testFailingConsumerWithExchangeFailuresTransactedQueueName);

        /* We don't add transacted=true because the consumer is transacted
         * but the producer can't be since it's request-reply. We make the
         * consumer transactional by using transacted() for the route. */
        this.testTransactedQueueEndpoint = resolveMandatoryEndpoint("activemq:queue:" + testTransactedQueueName
                + "?includeSentJMSMessageID=true&requestTimeout=" + WAIT_FOR_REPLY_TIMEOUT + "&transacted=true");
        this.testTransactedQueueEndpointForProducer = resolveMandatoryEndpointForProducer("activemq:queue:"
                + testTransactedQueueName + "?includeSentJMSMessageID=true&requestTimeout=" + WAIT_FOR_REPLY_TIMEOUT);

        this.testFailingConsumerWithReplyExceptionsTransactedQueueEndpoint = resolveMandatoryEndpoint("activemq:queue:"
                + testFailingConsumerWithReplyExceptionsTransactedQueueName + "?includeSentJMSMessageID=true"
                + "&requestTimeout=" + WAIT_FOR_REPLY_TIMEOUT + "&transacted=true");
        this.testFailingConsumerWithReplyExceptionsTransactedQueueEndpointForProducer = resolveMandatoryEndpointForProducer(
                "activemq:queue:" + testFailingConsumerWithReplyExceptionsTransactedQueueName
                        + "?includeSentJMSMessageID=true&requestTimeout=" + WAIT_FOR_REPLY_TIMEOUT);

        this.testFailingConsumerWithExchangeFailuresTransactedQueueEndpoint = resolveMandatoryEndpoint("activemq:queue:"
                + testFailingConsumerWithExchangeFailuresTransactedQueueName + "?includeSentJMSMessageID=true"
                + "&requestTimeout=" + WAIT_FOR_REPLY_TIMEOUT + "&transacted=true");
        this.testFailingConsumerWithExchangeFailuresTransactedQueueEndpointForProducer = resolveMandatoryEndpointForProducer(
                "activemq:queue:" + testFailingConsumerWithExchangeFailuresTransactedQueueName
                        + "?includeSentJMSMessageID=true&requestTimeout=" + WAIT_FOR_REPLY_TIMEOUT);

        /* Add the RouteBuilder which has our Camel routes. */
        getCamelContext().addRoutes(createRouteBuilder());
        if (!getCamelContext().isStarted()) {
            throw new RuntimeException("Couldn't start context");
        }
        waitForRoutesToStart(getCamelContext(), getCamelContext().getRoutes());

        /* do the same for the producer context */
        getProducerCamelContext().addRoutes(createProducerRouteBuilder());
        if (!getProducerCamelContext().isStarted()) {
            throw new RuntimeException("Couldn't start producerContext");
        }
        waitForRoutesToStart(getProducerCamelContext(), getProducerCamelContext().getRoutes());
    }

    @Override
    public void tearDown() throws Exception {
        stopSpringContext();
        log.debug("Stopping broker");
        getEmbeddedBroker().shutDownBroker();
        for (int i = 0; i < 10; i++) {
            if (getEmbeddedBroker().getBrokerService().isStopped()) {
                break;
            }
            Thread.sleep(1000);
        }
        if (!getEmbeddedBroker().getBrokerService().isStopped()) {
            throw new RuntimeException("Couldn't stop broker");
        }
    }

    protected Endpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = getCamelContext().getEndpoint(uri);
        assertNotNull(endpoint);
        return endpoint;
    }

    protected Endpoint resolveMandatoryEndpointForProducer(String uri) {
        Endpoint endpoint = getProducerCamelContext().getEndpoint(uri);
        assertNotNull(endpoint);
        return endpoint;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        SpringRouteBuilder routeBuilder = new SpringRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // consumer successfully consumes
                getConsumerBuilder()
                        .from(testTransactedQueueEndpoint,
                                ErrorResponseMode.EXCEPTION_AS_REPLY, this)
                        .bean(getTestReceiverBean(), "receiveMessage");

                // consumer throws exception where the exception is put into
                // the reply
                getConsumerBuilder()
                        .from(testFailingConsumerWithReplyExceptionsTransactedQueueEndpoint,
                                ErrorResponseMode.EXCEPTION_AS_REPLY, this)
                        .bean(getTestReceiverBean(), "receiveMessageThrowException");

                // consumer throws exception where the exchange is failed and
                // there is no reply
                getConsumerBuilder()
                        .from(testFailingConsumerWithExchangeFailuresTransactedQueueEndpoint,
                                ErrorResponseMode.EXCHANGE_FAILURE_NO_REPLY, this)
                        .bean(getTestReceiverBean(), "receiveMessageThrowException");
            }
        };

        routeBuilder.setApplicationContext(getSpringContext());

        return routeBuilder;
    }

    protected RouteBuilder createProducerRouteBuilder() throws Exception {
        SpringRouteBuilder routeBuilder = new SpringRouteBuilder() {
            @Override
            public void configure() throws Exception {
                // PRODUCER JMS REQUEST-REPLY CAN'T BE TRANSACTIONAL

                // successful send
                from("direct:transactedTest").to(testTransactedQueueEndpointForProducer);

                // producer throws an exception before message sent
                from("direct:transactedPreSentFailureTest").onException(Exception.class).maximumRedeliveries(0).end()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new Exception("purposely thrown exception");
                            }
                        }).to(testTransactedQueueEndpointForProducer);

                // producer throws an exception after message sent
                from("direct:transactedPostSentFailureTest").onException(Exception.class).maximumRedeliveries(0).end()
                        .to(testTransactedQueueEndpointForProducer).process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new Exception("purposely thrown exception");
                            }
                        });
            }
        };

        routeBuilder.setApplicationContext(getSpringContext());

        return routeBuilder;
    }

    public QueueStatistics getQueueStatistics(String queueName) throws Exception {
        return QueueStatistics.getQueueStatistics(getEmbeddedBroker(), queueName);
    }

    private void waitForRoutesToStart(CamelContext context, List<Route> routes) {
        for (Route route : routes) {
            waitForRouteToStart(context, route);
        }
    }

    private void waitForRouteToStart(CamelContext context, Route route) {
        assert route.getId() != null;
        int totalMillis = 0;
        for (totalMillis = 0; totalMillis < 5000; totalMillis += 100) {
            ServiceStatus status = context.getRouteStatus(route.getId());
            assertNotNull(status);
            if (!status.isStartable() || status.isStarted()) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                if (!context.getRouteStatus(route.getId()).isStarted()) {
                    throw new RuntimeException("Thread interrupted while waiting for route " + route.getId()
                            + " to start.  After interruption, it has still not been started.");
                }
                return;
            }
        }
        log.debug("Took " + totalMillis + "ms for route " + route.getId() + " to start.");

        if (!context.getRouteStatus(route.getId()).isStarted()) {
            throw new RuntimeException("Timeout while waiting for route " + route.getId() + " to start");
        }
    }

    protected void assertStatsSuccessfulConsumption(String queueName) throws Exception {
        QueueStatistics stats = getQueueStatistics(queueName);
        assertEquals(stats.pendingMessageCount, 0);
        assertEquals(stats.messagesEnqueued, 1);
        assertEquals(stats.messagesDequeued, 1);
        assertEquals(stats.dlqPendingMessageCount, 0);
        assertEquals(stats.dlqMessagesEnqueued, 0);
    }

    protected void assertStatsFailedExchange(String queueName) throws Exception {
        QueueStatistics stats = getQueueStatistics(queueName);
        assertEquals(stats.pendingMessageCount, 0);
        assertEquals(stats.messagesEnqueued, 1);
        assertEquals(stats.messagesDequeued, 1);
        assertEquals(stats.dlqPendingMessageCount, 0);
        assertEquals(stats.dlqMessagesEnqueued, 1);
        assertEquals(stats.dlqMessagesDequeued, 1);
    }

    @Test
    public void testSuccessfulConsumption() throws Exception {
        try {
            // we use send() so we can get at the response headers
            Exchange responseExchange = getProducerTemplate().send("direct:transactedTest", ExchangePattern.InOut,
                    new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setBody("hello world");
                        }
                    });
            String response = responseExchange.getOut().getBody(String.class);
            log.debug("response = " + response);
            assertTrue(getTestReceiverBean().fifo.size() > 0);
            assertEquals("hello world", getTestReceiverBean().fifo.pop());
            assertEquals("Camel acknowledged", response);
        } finally {
            log.debug("for testSuccessfulConsumption, STATS: " + getQueueStatistics(testTransactedQueueName));
        }
        assertStatsSuccessfulConsumption(testTransactedQueueName);
    }

    @Test
    public void testPreSentProducerFailure() throws Exception {
        try {
            getProducerTemplate().requestBody("direct:transactedPreSentFailureTest", "hello world pre");
        } catch (CamelExecutionException e) {
            assertEquals(0, getTestReceiverBean().fifo.size());
            return;
        }
        fail("exception should have been thrown");
    }

    @Test
    public void testPostSentProducerFailure() throws Exception {
        try {
            getProducerTemplate().requestBody("direct:transactedPostSentFailureTest", "hello world post");
        } catch (CamelExecutionException e) {
            /* Since our processor threw an exception AFTER we already
             * consumed from the queue, we expect the FIFO to have an entry. */
            assertTrue(getTestReceiverBean().fifo.size() > 0);
            assertEquals("hello world post", getTestReceiverBean().fifo.pop());
            QueueStatistics stats = getQueueStatistics(testTransactedQueueName);
            log.debug("for testPostSentProducerFailure, STATS: " + stats);
            assertStatsSuccessfulConsumption(testTransactedQueueName);
            return;
        }
        fail("exception should have been thrown");
    }

    /**
     * In this failure handling mode, we expect a consumer exception to be
     * returned back as the response. Since a response was sent, the message
     * does not go to the DLQ.
     */
    @Test
    public void testConsumerFailureWithExceptionResponse() throws Exception {
        Object response = null;
        try {
            response = getProducerTemplate().requestBody(
                    testFailingConsumerWithReplyExceptionsTransactedQueueEndpointForProducer,
                    "hello world, consumer failure");
            log.debug("response = " + response);
        } finally {
            log.debug("for testConsumerFailureWithExceptionResponse, STATS: "
                    + getQueueStatistics(testFailingConsumerWithReplyExceptionsTransactedQueueName));
        }
        assertEquals(0, getTestReceiverBean().fifo.size());
        /* Confirm that we have received an error reply. */
        assertTrue(response instanceof Exception);
        assertEquals(((Exception) response).getMessage(), "purposely thrown consumer exception in TestReceiverBean");
        /* Since using EXCEPTION_AS_REPLY mode for this test, there will be
         * no DLQ message. */
        assertStatsSuccessfulConsumption(testFailingConsumerWithReplyExceptionsTransactedQueueName);
    }

    /**
     * In this failure handling mode, we expect the Camel Exchange to fail
     * with no reply received. Since the exchange failed, the message should
     * go to the DLQ.
     */
    @Test
    public void testConsumerFailureWithExchangeFailure() throws Exception {
        try {
            /* we expect this to timeout after WAIT_FOR_REPLY_TIMEOUT
             * milliseconds */
            getProducerTemplate().requestBody(testFailingConsumerWithExchangeFailuresTransactedQueueEndpointForProducer,
                    "hello world, consumer failure");
        } catch (RuntimeCamelException e) {
            /* This should be a Camel timeout exception. We won't ever
             * receive a reply. */
            assert (e.getCause() instanceof ExchangeTimedOutException);

            assertEquals(0, getTestReceiverBean().fifo.size());

            /* Confirm that the broker moved the message to the DLQ since the
             * exchange failed. AMQ broker may take a little bit of time to
             * move it to DLQ.
             * 
             * Note that we're checking for the dequeued count because of the
             * DLQ consumer we set up using setUpDlqConsumer() in setUp(). If
             * no DLQ consumer is started, then we'd have to check for the
             * enqueued count instead. */
            QueueStatistics stats = null;
            for (int i = 0; i < 40; i++) {
                stats = getQueueStatistics(testFailingConsumerWithExchangeFailuresTransactedQueueName);
                log.debug("for testConsumerFailureWithExchangeFailure, STATS: " + stats);
                if (stats.dlqMessagesDequeued > 0) {
                    break;
                } else {
                    Thread.sleep(500);
                }
            }
            if (stats.dlqMessagesDequeued <= 0) {
                log.error("Timeout waiting for failed message to be put onto the DLQ");
            }
            assertStatsFailedExchange(testFailingConsumerWithExchangeFailuresTransactedQueueName);

            return;
        }
        fail("RuntimeCamelException should have been thrown");
    }
}
