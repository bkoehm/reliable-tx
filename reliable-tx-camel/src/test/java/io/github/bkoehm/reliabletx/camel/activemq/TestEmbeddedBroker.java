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
package io.github.bkoehm.reliabletx.camel.activemq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.IndividualDeadLetterStrategy;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.broker.region.policy.RedeliveryPolicyMap;
import org.apache.activemq.broker.util.RedeliveryPlugin;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.transport.vm.VMTransportFactory;
import org.apache.log4j.Logger;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * An embedded ActiveMQ broker for testing purposes.
 *
 * @author Brian Koehmstedt
 */
public class TestEmbeddedBroker {
    public static final String AMQ_VM = "vm://localhost?broker.persistent=false&broker.useJmx=false";

    protected Logger log = Logger.getLogger(TestEmbeddedBroker.class);
    private BrokerService brokerService;

    private List<Connection> consumerConnections = new LinkedList<Connection>();

    public void startUpBroker() throws Exception {
        // make sure the vm transport is fully stopped from previous runs
        VMTransportFactory.stopped("localhost");
        // Start up an embedded ActiveMQ broker
        this.brokerService = new BrokerService();
        brokerService.setUseJmx(false);
        brokerService.setPersistent(false);
        brokerService.addConnector(AMQ_VM);

        brokerService.setSchedulerSupport(true);

        // set the deadLetterStrategy for individual DLQs
        setUpDeadLetterStrategy();

        // default redelivery policy
        setUpRedeliveryPlugin();

        // Start the broker
        brokerService.setStartAsync(false);
        brokerService.start();
        for (int i = 0; i < 10; i++) {
            if (brokerService.isStarted()) {
                break;
            }
            Thread.sleep(1000);
        }
        if (!brokerService.isStarted()) {
            throw new RuntimeException("Could not start broker");
        }
    }

    public void shutDownBroker() throws Exception {
        for (Connection conn : consumerConnections) {
            conn.close();
        }
        consumerConnections.clear();
        brokerService.stop();
        VMTransportFactory.stopped("localhost");
    }

    public BrokerService getBrokerService() {
        return brokerService;
    }

    /**
     * Set a deadLetterStrategy that uses individual DLQs.
     */
    protected void setUpDeadLetterStrategy() {
        // http://activemq.apache.org/message-redelivery-and-dlq-handling.html
        // Broker -> DestinationPolicy -> policyMap -> policyEntries ->
        // policyEntry(">") -> deadLetterStrategy

        // create a policyEntry for queue ">"
        PolicyEntry defaultPolicyEntry = new PolicyEntry();
        ActiveMQQueue defaultDest = new ActiveMQQueue(">");
        defaultPolicyEntry.setDestination(defaultDest);

        // Set the IndividualDeadLetterStrategy for this policy entry. This
        // means each queue gets its own dead letter queue. The dead letter
        // queue name is "DLQ.<queueName>".
        IndividualDeadLetterStrategy dls = new IndividualDeadLetterStrategy();
        dls.setQueuePrefix("DLQ.");
        dls.setUseQueueForQueueMessages(true);
        dls.setProcessNonPersistent(true);
        defaultPolicyEntry.setDeadLetterStrategy(dls);

        // create the policy map
        PolicyMap destinationPolicy = new PolicyMap();
        destinationPolicy.put(defaultDest, defaultPolicyEntry);

        // set the DestinationPolicy in the broker
        brokerService.setDestinationPolicy(destinationPolicy);
    }

    /**
     * Get a Destination object for a queue or topic.
     *
     * @param name Queue or topic name.
     * @throws Exception If there is a problem retrieving the Destination object.
     */
    public Set<org.apache.activemq.broker.region.Destination> getDestinationsByQueueName(String name) throws Exception {
        return brokerService.getBroker().getDestinations(new ActiveMQQueue(name));
    }

    public Connection getNewJmsConnection() throws JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(AMQ_VM);
        Connection connection = connectionFactory.createConnection();
        connection.start();
        return connection;
    }

    public Session getNewJmsSession(Connection connection) throws JMSException {
        return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    /**
     * Sets up a consumer for a DLQ that just prints out the dead message to
     * the log.
     *
     * @throws JMSException If there is a problem setting up the consumer.
     */
    public void setUpDlqConsumer(final String dlqName) throws JMSException {
        Connection connection = getNewJmsConnection();
        consumerConnections.add(connection);
        final Session session = getNewJmsSession(connection);

        Destination queue = session.createQueue(dlqName);

        // Message consumer
        MessageConsumer consumer = session.createConsumer(queue);
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                try {
                    log.debug(dlqName + " RECEIVED message class of type " + message.getClass().getName());
                    if (message instanceof TextMessage) {
                        log.debug(dlqName + " RECEIVED: " + ((TextMessage) message).getText());
                    }
                } catch (Exception e) {
                    log.error("onMessage exception", e);
                }
            }
        });
    }

    protected void setUpRedeliveryPlugin() throws Exception {
        // http://activemq.apache.org/message-redelivery-and-dlq-handling.html
        RedeliveryPlugin plugin = new RedeliveryPlugin();
        plugin.setFallbackToDeadLetter(true);
        plugin.setSendToDlqIfMaxRetriesExceeded(true);
        RedeliveryPolicyMap redeliveryPolicyMap = new RedeliveryPolicyMap();
        RedeliveryPolicy defaultEntry = new RedeliveryPolicy();
        defaultEntry.setMaximumRedeliveries(0);
        redeliveryPolicyMap.setDefaultEntry(defaultEntry);
        plugin.setRedeliveryPolicyMap(redeliveryPolicyMap);
        plugin.installPlugin(brokerService.getBroker());
    }
}
