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

import java.util.Set;

import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.DestinationStatistics;
import org.apache.activemq.broker.region.Queue;

/**
 * Report statistics for a queue and its corresponding DLQ. Used for testing
 * purposes.
 * 
 * @author Brian Koehmstedt
 */
public class QueueStatistics {
    public String queueName;
    public String dlqQueueName;
    public long pendingMessageCount;
    public long dlqPendingMessageCount;
    public long messagesEnqueued;
    public long messagesDequeued;
    public long dlqMessagesEnqueued;
    public long dlqMessagesDequeued;
    public long blockedSends;
    public long dlqBlockedSends;
    public long dispatched;
    public long dlqDispatched;
    public long expired;
    public long dlqExpired;
    public long forwards;
    public long dlqForwards;
    public long inflight;
    public long dlqInflight;
    public long messages;
    public long dlqMessages;

    @Override
    public String toString() {
        return queueName + "{pending=" + pendingMessageCount + ", enqueued=" + messagesEnqueued + ", dequeued="
                + messagesDequeued + ", messages=" + messages + ", blockedSends=" + blockedSends + ", dispatched="
                + dispatched + ", expired=" + expired + ", forwards=" + forwards + ", inflight=" + inflight + "}, "
                + dlqQueueName + "{pending=" + dlqPendingMessageCount + ", enqueued=" + dlqMessagesEnqueued
                + ", dequeued=" + dlqMessagesDequeued + ", messages=" + dlqMessages + ", blockedSends="
                + dlqBlockedSends + ", dispatched=" + dlqDispatched + ", expired=" + dlqExpired + ", forwards="
                + dlqForwards + ", inflight=" + dlqInflight + "}";
    }

    public static Queue getBrokerQueue(TestEmbeddedBroker broker, String queueName) throws Exception {
        Set<Destination> destinations = broker.getDestinationsByQueueName(queueName);
        return (destinations != null && destinations.size() > 0 ? (Queue) destinations.iterator().next() : null);
    }

    /**
     * @return Pending message count for the queue. Returns -1 when queue
     *         does not exist.
     */
    public static long getPendingMessageCount(Queue queue) {
        return (queue != null ? queue.getPendingMessageCount() : -1);
    }

    public static QueueStatistics getQueueStatistics(TestEmbeddedBroker broker, String queueName) throws Exception {
        Queue queue = getBrokerQueue(broker, queueName);
        Queue dlqQueue = getBrokerQueue(broker, "DLQ." + queueName);
        QueueStatistics stats = new QueueStatistics();
        stats.queueName = (queue != null ? queue.getName() : queueName);
        stats.dlqQueueName = (dlqQueue != null ? dlqQueue.getName() : "DLQ." + queueName);
        stats.pendingMessageCount = getPendingMessageCount(queue);
        stats.dlqPendingMessageCount = getPendingMessageCount(dlqQueue);

        DestinationStatistics s = (queue != null ? queue.getDestinationStatistics() : null);
        stats.messagesEnqueued = (s != null && s.getEnqueues().isEnabled() ? s.getEnqueues().getCount() : -1);
        stats.messagesDequeued = (s != null && s.getDequeues().isEnabled() ? s.getDequeues().getCount() : -1);
        stats.blockedSends = (s != null && s.getBlockedSends().isEnabled() ? s.getBlockedSends().getCount() : -1);
        stats.dispatched = (s != null && s.getDispatched().isEnabled() ? s.getDispatched().getCount() : -1);
        stats.expired = (s != null && s.getExpired().isEnabled() ? s.getExpired().getCount() : -1);
        stats.forwards = (s != null && s.getForwards().isEnabled() ? s.getForwards().getCount() : -1);
        stats.inflight = (s != null && s.getInflight().isEnabled() ? s.getInflight().getCount() : -1);
        stats.messages = (s != null && s.getMessages().isEnabled() ? s.getMessages().getCount() : -1);

        DestinationStatistics dlqs = (dlqQueue != null ? dlqQueue.getDestinationStatistics() : null);
        stats.dlqMessagesEnqueued = (dlqs != null && dlqs.getEnqueues().isEnabled() ? dlqs.getEnqueues().getCount()
                : -1);
        stats.dlqMessagesDequeued = (dlqs != null && dlqs.getDequeues().isEnabled() ? dlqs.getDequeues().getCount()
                : -1);
        stats.dlqBlockedSends = (dlqs != null && dlqs.getBlockedSends().isEnabled() ? dlqs.getBlockedSends().getCount()
                : -1);
        stats.dlqDispatched = (dlqs != null && dlqs.getDispatched().isEnabled() ? dlqs.getDispatched().getCount() : -1);
        stats.dlqExpired = (dlqs != null && dlqs.getExpired().isEnabled() ? dlqs.getExpired().getCount() : -1);
        stats.dlqForwards = (dlqs != null && dlqs.getForwards().isEnabled() ? dlqs.getForwards().getCount() : -1);
        stats.dlqInflight = (dlqs != null && dlqs.getInflight().isEnabled() ? dlqs.getInflight().getCount() : -1);
        stats.dlqMessages = (dlqs != null && dlqs.getMessages().isEnabled() ? dlqs.getMessages().getCount() : -1);

        return stats;
    }

    public void resetQueueStatistics(TestEmbeddedBroker broker, String queueName) throws Exception {
        Queue queue = getBrokerQueue(broker, queueName);
        if (queue != null && queue.getDestinationStatistics() != null) {
            queue.getDestinationStatistics().reset();
        }
        Queue dlqQueue = getBrokerQueue(broker, "DLQ." + queueName);
        if (dlqQueue != null && dlqQueue.getDestinationStatistics() != null) {
            dlqQueue.getDestinationStatistics().reset();
        }
    }
}
