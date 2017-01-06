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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.camel.spring.spi.TransactionErrorHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import software.reliabletx.spring.ManagedNestedTransactionTemplate;
import software.reliabletx.spring.ManagedSpringTransaction;

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
    public static final String ALREADY_COMPLETE_PROPERTY = "exchangeAlreadyComplete";

    private String transactionPolicyRefName;
    private ErrorHandlerBuilder errorHandler;
    protected PlatformTransactionManager transactionManager;
    private boolean checkConfiguration = true;
    /* Defaults to true and if true the camel processor will throw an
     * exception if there isn't an existing transaction name at the beginning
     * of the route. For example, if the from endpoint is a JMS endpoint,
     * then the expectation will be that the JMS MessageListener has already
     * started a transaction and it has set a non-null transaction name. Note
     * that it is possible to be in a transaction but the tx name is null. */
    private boolean enforceExistanceOfEnteringTransactionName = true;

    public ReliableTxConsumerBuilder() {
    }

    public ReliableTxConsumerBuilder(String transactionPolicyRefName, ErrorHandlerBuilder errorHandler) {
        this.transactionPolicyRefName = transactionPolicyRefName;
        this.errorHandler = errorHandler;
    }

    public String getTransactionPolicyRefName() {
        return transactionPolicyRefName;
    }

    public void setTransactionPolicyRefName(String transactionPolicyRefName) {
        this.transactionPolicyRefName = transactionPolicyRefName;
    }

    public ErrorHandlerBuilder getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandlerBuilder errorHandler) {
        this.errorHandler = errorHandler;
    }

    public boolean getEnforceExistanceOfEnteringTransactionName() {
        return enforceExistanceOfEnteringTransactionName;
    }

    public void setEnforceExistanceOfEnteringTransactionName(boolean enforceExistanceOfEnteringTransactionName) {
        this.enforceExistanceOfEnteringTransactionName = enforceExistanceOfEnteringTransactionName;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public boolean isConfigurationChecked() {
        return checkConfiguration;
    }

    public void setConfigurationChecked(boolean checkConfiguration) {
        this.checkConfiguration = checkConfiguration;
    }

    public ProcessorDefinition<?> from(final Endpoint origin, final ErrorResponseMode errorHandlingMode,
            final SpringRouteBuilder routeBuilder) throws Exception {
        if (isConfigurationChecked()) {
            if (!(errorHandler instanceof TransactionErrorHandlerBuilder)) {
                throw new RuntimeException(
                        "errorHandler is not an instanceof TransactionErrorHandlerBuilder.  Instead, it's: "
                                + (errorHandler != null ? errorHandler.getClass().getName() : "null"));
            }
            TransactionErrorHandlerBuilder errorHandlerBuilder = (TransactionErrorHandlerBuilder) errorHandler;
            if (!(errorHandlerBuilder.getTransactionTemplate() instanceof ManagedNestedTransactionTemplate)) {
                throw new RuntimeException(
                        "errorHandler.transactionTemplate is not an instance of ManagedTransactionTemplate.  Instead, it's: "
                                + (errorHandlerBuilder.getTransactionTemplate() != null
                                        ? errorHandlerBuilder.getTransactionTemplate().getClass().getName() : "null"));
            }
            assertWithException(transactionPolicyRefName != null);
            SpringTransactionPolicy stp = routeBuilder.getApplicationContext().getBean(transactionPolicyRefName,
                    SpringTransactionPolicy.class);
            if (stp == null) {
                throw new RuntimeException("transactionPolicyRefName is set to " + transactionPolicyRefName
                        + " and either that bean does not exist or it's not an instance of SpringTransactionPolicy");
            }
            if (!(stp.getTransactionTemplate() instanceof ManagedNestedTransactionTemplate)) {
                throw new RuntimeException(transactionPolicyRefName
                        + ".transactionTemplate is not an instance of ManagedTransactionTemplate.  Instead, it's: "
                        + stp.getClass().getName());
            }

            /**
             * Exchange can still be transacted when the endpoint is not
             * configured as transacted, so confirm the endpoint is
             * transacted, if we can.
             */
            Boolean isEndpointTransacted = isEndpointTransacted(origin);
            if (isEndpointTransacted != null && isEndpointTransacted == Boolean.FALSE) {
                throw new RuntimeException(
                        "Endpoint's isTransacted() and/or getTransactionManager() method is returning false or null: endpoint="
                                + origin);
            }
        }
        return routeBuilder.from(origin).errorHandler(errorHandler)
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
                        if (exceptionCaught instanceof ReliableTxCamelException) {
                            /* onException can be called a second time when
                             * the original exception is wrapped. Since we've
                             * already handled the exception, call
                             * setException() again to re-mark the exchange
                             * as failed and then return. */
                            log.debug("Exception has already been handled.");
                            exchange.setException(exceptionCaught);
                            return;
                        }
                        if (exceptionCaught != null) {
                            log.error("Exception caught during exchange", exceptionCaught);
                        } else {
                            log.error("onException() called but there is no exception set in "
                                    + Exchange.EXCEPTION_CAUGHT + " exchange parameter");
                        }

                        ManagedSpringTransaction managedTx = getManagedSpringTransaction(exchange);
                        assertWithException(managedTx != null);
                        assertWithException(managedTx.getTransactionStatus() != null);

                        if (managedTx.isRollbackOnly() || managedTx.isRolledBack()) {
                            log.debug(
                                    "Managed transaction for this exchange has already been marked as rollback-only or has already been rolled back");
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
                        // I'm not sure what causes onCompletion() to
                        // sometimes be called multiple times on the same
                        // exchange.
                        if (exchange.getProperty(ALREADY_COMPLETE_PROPERTY).equals(Boolean.TRUE)) {
                            log.debug("onCompletion() has already ran for exchange " + exchange.getExchangeId());
                            return;
                        }
                        exchange.setProperty(ALREADY_COMPLETE_PROPERTY, Boolean.TRUE);

                        ManagedSpringTransaction managedTx = getManagedSpringTransaction(exchange);
                        assertWithException(managedTx != null);
                        assertWithException(managedTx.getTransactionStatus() != null);

                        String originalTxName = getOriginalTransactionName(exchange);

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
                            if (!exchange.isFailed() && !managedTx.isRollbackOnly() && !managedTx.isRolledBack()) {
                                managedTx.commit();
                                if (log.isDebugEnabled()) {
                                    log.debug("committed");
                                }
                            } else {
                                log.debug(
                                        "Exchange is either failed or the managedTx is marked as rollback-only or already rolled back.");
                                if (!managedTx.isRolledBack()) {
                                    managedTx.rollback();
                                }
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
                            throw new ReliableTxCamelException("Managed transaction commit or rollback failed", e);
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
                        if (getEnforceExistanceOfEnteringTransactionName() && originalTxName == null) {
                            throw new ReliableTxCamelException(
                                    "The original transaction name was null and enforceExistanceOfEnteringTransactionName is true.");
                        }
                        if (originalTxName != null && !originalTxName
                                .equals(TransactionSynchronizationManager.getCurrentTransactionName())) {
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

                        /* The current transaction should be a managed
                         * transaction, thanks to
                         * ManagedNestedTransactionTemplate used by the
                         * errorHandler (errorHandler.transactionTemplate). */
                        ManagedSpringTransaction managedTx = ManagedNestedTransactionTemplate
                                .getExistingManagedTransaction();
                        assertWithException(managedTx != null);
                        assertWithException(managedTx.isCurrentAndActive());

                        /* The exchange should be transacted. */
                        if (!exchange.isTransacted()) {
                            managedTx.markRollbackOnly();
                            throw new ReliableTxCamelException("This exchange isn't transacted.  Is origin "
                                    + origin.getEndpointUri() + " transactional?  The origin Endpoint class is "
                                    + origin.getClass().getName());
                        }

                        /* The ManagedNestedTransactionTemplate may have
                         * suspended a transaction in order to create a new
                         * nested transaction at the start of this exchange. */
                        String originalTxName = managedTx.getOriginalTransactionName();
                        if (originalTxName != null || managedTx.getOriginalTransactionWasSuspended()) {
                            log.debug("Original tx name that was suspended for this exchange: " + originalTxName);
                            exchange.setProperty(ORIGINAL_TX_NAME_PROPERTY, originalTxName);
                        }
                        if (getEnforceExistanceOfEnteringTransactionName()
                                && exchange.getProperty(ORIGINAL_TX_NAME_PROPERTY) == null) {
                            managedTx.markRollbackOnly();
                            throw new ReliableTxCamelException(
                                    "Upon entering the route, before starting the managed transaction for the route, the original transaction name was null.  Either no transaction was active or there was an active transaction with a null transaction name.  The expectation is there should be an existing transaction with a name upon entering the route.  If this is not the case, set enforceExistanceOfEnteringTransactionName to false.");
                        }

                        /* this managed transaction belongs to this exchange */
                        exchange.setProperty(MANAGED_TX_PROPERTY, managedTx);
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

    protected static Boolean isEndpointTransacted(Endpoint endpoint)
            throws SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        /**
         * Using reflection here is unfortunate but transactional Camel
         * Endpoints don't implement any interface that exposes an
         * isTransacted() method (as of Camel 2.18). So we have to hope our
         * Endpoint has an isTransacted() method that we call with
         * reflection. (This is the case with a JmsEndpoint, JdbcEndpoint and
         * SqlEndpoint.)
         * 
         * If it doesn't have an isTransacted() method, we can't check
         * whether it's transactional or not. We don't throw an exception in
         * this case because it's possible there are transactional endpoints
         * that don't implement an isTransacted() method.
         */
        try {
            Method isTransactedMethod = endpoint.getClass().getMethod("isTransacted");
            Boolean isTransacted = (Boolean) isTransactedMethod.invoke(endpoint);
            if (isTransacted == Boolean.FALSE) {
                return Boolean.FALSE;
            }
        } catch (NoSuchMethodException e) {
            /* We can't check since it lacks the method. */
            return null;
        }

        /**
         * Do the same for the getTransactionManager() method.
         */
        try {
            Method getTransactionManagerMethod = endpoint.getClass().getMethod("getTransactionManager");
            PlatformTransactionManager txMgr = (PlatformTransactionManager) getTransactionManagerMethod
                    .invoke(endpoint);
            return txMgr != null;
        } catch (NoSuchMethodException e) {
            /* We can't check since it lacks the method. */
            return null;
        }
    }
}
