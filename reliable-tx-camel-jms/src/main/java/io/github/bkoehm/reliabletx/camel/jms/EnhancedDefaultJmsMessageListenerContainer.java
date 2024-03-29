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
package io.github.bkoehm.reliabletx.camel.jms;

import java.security.SecureRandom;
import java.util.Random;

import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;

import io.github.bkoehm.reliabletx.spring.ManagedSpringTransaction;
import io.github.bkoehm.reliabletx.spring.ManagedSpringTransactionImpl;
import org.apache.camel.component.jms.DefaultJmsMessageListenerContainer;
import org.apache.camel.component.jms.JmsEndpoint;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Consumes from a JMS endpoint using a managed transaction.
 * 
 * @author Brian Koehmstedt
 */
public class EnhancedDefaultJmsMessageListenerContainer extends DefaultJmsMessageListenerContainer {

    private String transactionName;
    private int transactionTimeout;
    private boolean appendRandomToTxName = true;
    private Random random;

    private ThreadLocal<ManagedSpringTransaction> currentManagedTransaction = new ThreadLocal<ManagedSpringTransaction>();

    public EnhancedDefaultJmsMessageListenerContainer(JmsEndpoint endpoint, boolean allowQuickStop) {
        super(endpoint, allowQuickStop);
    }

    public EnhancedDefaultJmsMessageListenerContainer(JmsEndpoint endpoint) {
        super(endpoint);
    }

    /**
     * @see org.springframework.jms.listener.AbstractPollingMessageListenerContainer#receiveAndExecute(java.lang.Object,
     *      jakarta.jms.Session, jakarta.jms.MessageConsumer)
     */
    @Override
    protected boolean receiveAndExecute(Object invoker, Session session, MessageConsumer consumer) throws JMSException {
        if (getTransactionManager() != null) {
            // Execute receive within transaction.
            ManagedSpringTransaction managedTx = establishTransaction();
            try {
                /* TransactionStatus status =
                 * managedTx.getTransactionStatus(); */
                boolean messageReceived;
                try {
                    messageReceived = doReceiveAndExecute(invoker, session, consumer, managedTx.getTransactionStatus());
                } catch (JMSException ex) {
                    rollbackOnException(managedTx, ex);
                    throw ex;
                } catch (RuntimeException ex) {
                    rollbackOnException(managedTx, ex);
                    throw ex;
                } catch (Error err) {
                    rollbackOnException(managedTx, err);
                    throw err;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("receiveAndExecute(): about to commit " + managedTx.getTransactionName());
                }
                managedTx.commit();
                if (!managedTx.isCommitted()) {
                    throw new RuntimeException("Something went wrong trying to commit the managed transaction "
                            + managedTx.getTransactionName());
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("receiveAndExecute(): committed " + managedTx.getTransactionName());
                }

                return messageReceived;
            } finally {
                deassociateCurrentManagedTransaction();
            }
        } else {
            // Execute receive outside of transaction.
            return doReceiveAndExecute(invoker, session, consumer, null);
        }
    }

    /**
     * @see org.springframework.jms.listener.AbstractPollingMessageListenerContainer#setTransactionName(java.lang.String)
     */
    @Override
    public void setTransactionName(String transactionName) {
        super.setTransactionName(transactionName);
        this.transactionName = transactionName;
    }

    protected String getTransactionName() {
        return transactionName;
    }

    /**
     * @see org.springframework.jms.listener.AbstractPollingMessageListenerContainer#setTransactionTimeout(int)
     */
    @Override
    public void setTransactionTimeout(int transactionTimeout) {
        super.setTransactionTimeout(transactionTimeout);
        this.transactionTimeout = transactionTimeout;
    }

    protected int getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * @see org.springframework.jms.listener.DefaultMessageListenerContainer#initialize()
     */
    @Override
    public void initialize() {
        super.initialize();
        if (transactionName == null) {
            this.transactionName = getBeanName();
        }

        if (isAppendingRandomToTxName()) {
            random = new Random(new SecureRandom().nextLong());
        }
    }

    /**
     * Perform a rollback, handling rollback exceptions properly.
     */
    protected void rollbackOnException(ManagedSpringTransaction managedTx, Throwable ex) {
        if (logger.isDebugEnabled()) {
            logger.debug("Initiating transaction rollback on listener exception", ex);
        }
        try {
            managedTx.rollback();
        } catch (RuntimeException ex2) {
            logger.error("Listener exception overridden by rollback exception", ex);
            throw ex2;
        } catch (Error err) {
            logger.error("Listener exception overridden by rollback error", ex);
            throw err;
        }
    }

    protected ManagedSpringTransaction establishTransaction() {
        ManagedSpringTransactionImpl managedTx = (ManagedSpringTransactionImpl) ManagedSpringTransactionImpl
                .getCurrentManagedSpringTransaction();
        if (managedTx == null || !managedTx.isCurrentAndActive()) {
            managedTx = new ManagedSpringTransactionImpl(getTransactionManager(),
                    (isAppendingRandomToTxName() ? getTransactionName() + "#" + nextRandom() : getTransactionName()));
            if (managedTx.getTransactionDefinition() instanceof DefaultTransactionDefinition) {
                ((DefaultTransactionDefinition) managedTx.getTransactionDefinition())
                        .setTimeout(getTransactionTimeout());
            }
            managedTx.beginTransaction();
            if (!managedTx.isCurrentAndActive()) {
                throw new RuntimeException("Something went wrong trying to establish a new managed transaction for "
                        + managedTx.getTransactionName() + ": the new transaction is not current nor active");
            }
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "establishTransaction() there wasn't an already current and active managed transaction so established a new transaction: "
                                + managedTx.getTransactionName());
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("establishTransaction(): keeping an already current and active managed transaction: "
                        + managedTx.getTransactionName());
            }
        }
        currentManagedTransaction.set(managedTx);
        return managedTx;
    }

    protected void deassociateCurrentManagedTransaction() {
        currentManagedTransaction.remove();
    }

    public ManagedSpringTransaction getCurrentManagedTransaction() {
        return currentManagedTransaction.get();
    }

    protected boolean isAppendingRandomToTxName() {
        return appendRandomToTxName;
    }

    protected void setAppendingRandomToTxName(boolean appendRandomToTxName) {
        this.appendRandomToTxName = appendRandomToTxName;
    }

    protected int nextRandom() {
        return random.nextInt(Integer.MAX_VALUE);
    }
}
