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

import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Brian Koehmstedt
 */
public class TestReceiverBean {
    private final Logger log = LoggerFactory.getLogger(getClass());

    protected PlatformTransactionManager jmsTransactionManager;
    protected ConcurrentLinkedDeque<String> fifo = new ConcurrentLinkedDeque<String>();

    public void setJmsTransactionManager(PlatformTransactionManager jmsTransactionManager) {
        this.jmsTransactionManager = jmsTransactionManager;
    }

    public synchronized String receiveMessage(Exchange exchange) {
        assert exchange.getUnitOfWork().isTransacted();

        log.info("receiveMessage()");

        fifo.push(exchange.getIn().getBody(String.class));
        return "Camel acknowledged";
    }

    public synchronized String receiveMessageThrowException(Exchange exchange) throws Exception {
        log.debug("Received exchange but about to throw an exception on purpose");
        assert exchange.getUnitOfWork().isTransacted();
        throw new RuntimeException("purposely thrown consumer exception in TestReceiverBean");
    }

    public synchronized int fifoSize() {
        return fifo.size();
    }
}
