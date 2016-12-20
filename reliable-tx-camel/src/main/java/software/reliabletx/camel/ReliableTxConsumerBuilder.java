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

import java.io.Serializable;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spring.SpringRouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import software.reliabletx.spring.ManagedSpringTransaction;
import software.reliabletx.spring.ManagedSpringTransactionImpl;

/**
 * Build the start of a reliably transacted Camel route for a consumer.
 *
 * <p>
 * Both {@link ErrorResponseMode#EXCEPTION_AS_REPLY} and
 * {@link ErrorResponseMode#EXCHANGE_FAILURE_NO_REPLY} modes can be used for
 * InOut exchange patterns.
 * {@link ErrorResponseMode#EXCHANGE_FAILURE_NO_REPLY} mode should be used
 * for an <i>InOnly</i> pattern.
 * </p>
 *
 * <p>
 * Example: <code>
 * ReliableTxConsumerBuilder consumerBuilder = new ReliableTxConsumerBuilder();
 *
 * public RouteBuilder createRouteBuilder() throws Exception {
 *     SpringRouteBuilder routeBuilder = new SpringRouteBuilder() {
 *         {@literal @}Override
 *         public void configure() throws Exception {
 *             consumerBuilder
 *                     .from(consumerUri,
 *                             ErrorResponseMode.EXCEPTION_AS_REPLY, this)
 *                       .bean(myTestBean, "receiveMessage");
 *         }
 *     };
 *     routeBuilder.setApplicationContext(springContext);
 *     return routeBuilder;
 * }
 * </code>
 * </p>
 *
 * @author Brian Koehmstedt
 */
public class ReliableTxConsumerBuilder {
    final Logger log = LoggerFactory.getLogger(getClass());
    public static final String ORIGINAL_TX_NAME_PROPERTY = "originalTxName";
    public static final String MANAGED_TX_PROPERTY = "managedTx";

    private String transactionPolicyRefName;
    protected PlatformTransactionManager transactionManager;

    public ReliableTxConsumerBuilder() {
    }

    public ReliableTxConsumerBuilder(String transactionPolicyRefName) {
        this.transactionPolicyRefName = transactionPolicyRefName;
    }

    public void setTransactionPolicyRefName(String transactionPolicyRefName) {
        this.transactionPolicyRefName = transactionPolicyRefName;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public ProcessorDefinition<?> from(Endpoint origin, final ErrorResponseMode errorHandlingMode,
            SpringRouteBuilder routeBuilder) throws Exception {
        assertWithException(transactionPolicyRefName != null);
        return routeBuilder.from(origin)
                // onException(ReliableTxCamelException.class)
                /* This is here to preempt the general onException handling
                 * for Throwable. In other words, we don't want
                 * ReliableTxCamelExceptions being handled. We use this
                 * exception type when we want to mark the exchange as failed
                 * in EXCHANGE_FAILURE_NO_REPLY mode. */
                .onException(ReliableTxCamelException.class)
                // end of onException(ReliableTxCamelException.class)
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.debug("onException handling for ReliableTxCamelException");
                    }
                }).end()
                // onExeption(Throwable.class)
                .onException(Throwable.class).process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        log.debug("onException handling for Throwable");

                        Throwable exceptionCaught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                        if (exceptionCaught != null) {
                            log.error("Exception caught during exchange", exceptionCaught);
                        } else {
                            log.error("onException() called but there is no exception set in "
                                    + Exchange.EXCEPTION_CAUGHT + " exchange parameter");
                        }

                        ManagedSpringTransaction managedTx = getManagedSpringTransaction(exchange);
                        assertWithException(managedTx != null);
                        assertWithException(managedTx.getTransactionStatus() != null);

                        if (managedTx.isRollbackOnly()) {
                            log.debug("Managed transaction for this exchange has already been marked as rollback-only");
                            return;
                        } else {
                            log.debug("Marking managed transaction for this exchange as rollback-only");
                            managedTx.markRollbackOnly();
                        }
                    }
                }).handled(true)
                // end of onException(Throwable.class)
                .end()
                // onCompletion()
                .onCompletion().modeBeforeConsumer().process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        ManagedSpringTransaction managedTx = getManagedSpringTransaction(exchange);
                        assertWithException(managedTx != null);
                        assertWithException(managedTx.getTransactionStatus() != null);

                        String originalTxName = getOriginalTransactionName(exchange);

                        TransactionStatus originalTxStatus = exchange.getProperty("originalTxStatus",
                                TransactionStatus.class);

                        boolean didRollback = false;

                        try {
                            /* Do the explicit commit or rollback on our
                             * managed transaction. We do this so the tx is
                             * committed or rolled back before the reply is
                             * sent. ManagedSpringTransactionImpl takes care
                             * of checking that we're committing or rolling
                             * back the same transaction as was started and
                             * it also confirms the final state as fully
                             * committed or rolled back. It throws an
                             * exception otherwise. */
                            if (!exchange.isFailed() && !managedTx.isRollbackOnly()) {
                                managedTx.commit();
                                if (log.isDebugEnabled()) {
                                    log.debug("committed");
                                }
                            } else {
                                log.debug("Exchange is either failed or the managedTx is marked as rollback-only.");
                                managedTx.rollback();
                                didRollback = true;
                                if (log.isDebugEnabled()) {
                                    log.debug("rolled back");
                                }

                                /* Note: Calling originalTxStatus
                                 * setRollbackOnly() here or anywhere else in
                                 * this consumer won't achieve anything
                                 * because we do not have the "original" or
                                 * "owning" TransactionStatus object for the
                                 * original exchange transaction. So marking
                                 * it rollback-only would just be a no-op. */
                            }
                        } catch (Exception e) {
                            throw new ReliableTxCamelException("Managed transaction rollback failed", e);
                        }

                        /* commit() or rollback() did not throw an exception,
                         * so all should be well as long as the original
                         * transaction was resumed.
                         *
                         * We have to confirm that the original transaction
                         * has been resumed, otherwise Camel won't transact
                         * the completion of this exchange properly. If it's
                         * not the original transaction, then it's possible a
                         * third transaction was started by code executing in
                         * the route destination. This is not what we
                         * want/expect, so log an error so it can be fixed.
                         * If we want to get aggressive, we can also mark the
                         * current and original transactions for rollback
                         * since not in expected state. */
                        if (!originalTxName.equals(TransactionSynchronizationManager.getCurrentTransactionName())) {
                            String msg = "The managed transaction has been committed or rolled back but the original transaction has not been resumed.  "
                                    + "It's possible that a third transaction was improperly started by code executed by the "
                                    + "route destination.  originalTxName=" + originalTxName + ", currentTxName="
                                    + TransactionSynchronizationManager.getCurrentTransactionName() + ".";
                            throw new ReliableTxCamelException(msg);
                        }

                        /* originalTx has been restored. All is well. */

                        assertWithException(TransactionSynchronizationManager.isActualTransactionActive());
                        if (log.isDebugEnabled()) {
                            log.debug("originalTx has been restored.  All should be well with sending the reply.");
                        }

                        if (didRollback || (managedTx.isSynchronizationSupported() && managedTx.isRolledBack())) {
                            /* How we respond in a rollback situation depends
                             * on the error response mode. */
                            Throwable exceptionCaught = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                                    Throwable.class);
                            if (errorHandlingMode == ErrorResponseMode.EXCEPTION_AS_REPLY) {
                                /* We put the exception, or a failure message
                                 * if the exception isn't available, in the
                                 * reply. In this reply mode, the exchange
                                 * itself is not failed. i.e.,
                                 * exchange.isFailed() returns false and
                                 * exchange.getException() returns null. */
                                if (exceptionCaught != null) {
                                    exchange.getOut().setBody(convertExceptionAsReplyBody(exceptionCaught));
                                } else {
                                    exchange.getOut().setBody("Transaction/Exchange has failed for an unknown reason.");
                                }
                            } else if (errorHandlingMode == ErrorResponseMode.EXCHANGE_FAILURE_NO_REPLY) {
                                /* Put the exception in the exchange which
                                 * will mark the exchange as failed. There
                                 * will be no reply. Gives message brokers a
                                 * chance to move the message to DLQ if
                                 * they're configured to do so. */
                                if (exceptionCaught != null) {
                                    exchange.setException(new ReliableTxCamelException(exceptionCaught));
                                } else {
                                    exchange.setException(new ReliableTxCamelException(
                                            "Transaction/Exchange has failed for an unknown reason."));
                                }
                            }
                        } else {
                            if (exchange.getOut().getBody() == null) {
                                exchange.getOut().setBody("Exchange succeeded but there was no output.");
                            }
                        }
                    }
                })
                // end of onCompletion()
                .end()
                // start of exchange processor
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        /* If we were debugging, we could add a
                         * synchronization to the current transaction here to
                         * see what happens to it. But, we expect it to
                         * suspend when we create our explicit tx, and we
                         * expect it to resume when we commit our explicit
                         * tx. */

                        /* There should be an existing tx. */
                        assertWithException(TransactionSynchronizationManager.isActualTransactionActive());
                        Integer isolationLevel = TransactionSynchronizationManager
                                .getCurrentTransactionIsolationLevel();

                        exchange.setProperty(ORIGINAL_TX_NAME_PROPERTY,
                                TransactionSynchronizationManager.getCurrentTransactionName());

                        TransactionStatus originalTxStatus = getMandatoryCurrentTransactionStatus();
                        assertWithException(!originalTxStatus.isCompleted());
                        exchange.setProperty("originalTxStatus", originalTxStatus);

                        /* Short-circuit the current tx by creating a new one
                         * that we will manage on our own. We do this so we
                         * can commit this transaction before the reply is
                         * sent. If the transaction manager supports tx
                         * suspension, this will suspend the transaction that
                         * Camel started for the exchange. It will be resumed
                         * after we commit this transaction. */

                        ManagedSpringTransaction managedTx = new ManagedSpringTransactionImpl(transactionManager);

                        String txName = "ConsumerBuilderManagedTx:exchange:" + exchange.getExchangeId();
                        if (log.isTraceEnabled()) {
                            log.trace("using txName " + txName);
                        }
                        managedTx.setTransactionName(txName);

                        /* This will suspend the existing transaction and
                         * start our own explicit-managed transaction. */
                        managedTx.beginTransaction();

                        /* this managed transaction belongs to this exchange */
                        exchange.setProperty(MANAGED_TX_PROPERTY, managedTx);

                        /* We compare the old and new isolation levels to
                         * make sure they are the same. */
                        if (isolationLevel != null) {
                            assertWithException(isolationLevel
                                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()));
                        }
                    }
                })
                // transacted
                .transacted(transactionPolicyRefName);
    }

    protected Serializable convertExceptionAsReplyBody(Throwable e) {
        if (e instanceof RuntimeCamelException || e instanceof CamelException) {
            if (e.getCause() != null) {
                e = e.getCause();
            } else {
                return e.toString();
            }
        }
        if (e instanceof Serializable) {
            return e;
        } else {
            return e.toString();
        }
    }

    protected TransactionStatus getMandatoryCurrentTransactionStatus() {
        return transactionManager
                .getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_MANDATORY));
    }

    protected static void assertWithException(boolean condition) throws RuntimeException {
        if (!condition) {
            throw new RuntimeException("assertion failed");
        }
    }

    public static String getOriginalTransactionName(Exchange exchange) {
        return exchange.getProperty(ORIGINAL_TX_NAME_PROPERTY, String.class);
    }

    public static ManagedSpringTransaction getManagedSpringTransaction(Exchange exchange) {
        return exchange.getProperty(MANAGED_TX_PROPERTY, ManagedSpringTransaction.class);
    }

    public static String getManagedTransactionName(Exchange exchange) {
        return getManagedSpringTransaction(exchange).getTransactionName();
    }
}
