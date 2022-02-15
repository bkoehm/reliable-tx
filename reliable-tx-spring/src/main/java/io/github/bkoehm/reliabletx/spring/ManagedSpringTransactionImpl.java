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
package io.github.bkoehm.reliabletx.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;

/**
 * Primarily ensures that when a {@link #commit()} or {@link #rollback()} is
 * called, the managed transaction is still the current transaction.
 * 
 * <p>
 * More specifically, this implementation adds the following semantics to a
 * standard Spring transaction:
 * </p>
 * 
 * <ul>
 * 
 * <li>Although not absolutely required, for reliable transactions the Spring
 * transaction manager in use should support transaction synchronization. If
 * using a transaction manager that extends
 * {@link AbstractPlatformTransactionManager}, synchronization should be
 * enabled with:
 * {@link AbstractPlatformTransactionManager#SYNCHRONIZATION_ALWAYS} or
 * {@link AbstractPlatformTransactionManager#SYNCHRONIZATION_ON_ACTUAL_TRANSACTION}.
 * Additionally, for synchronization to be enabled,
 * {@link TransactionSynchronizationManager#initSynchronization()} must have
 * been called.</li>
 * 
 * <li>If a transaction name has not specified by the caller before
 * {@link #beginTransaction()} has been called, then a random transaction
 * name is generated and can be retrieved with {@link #getTransactionName()}
 * after {@link #beginTransaction()} has been called.</li>
 * 
 * <li>{@link #beginTransaction()} must be called to start the transaction.
 * </li>
 * 
 * <li>After beginTransaction() has been called and synchronization is
 * enabled, if a transaction was suspended then
 * getOriginalTransactionWasSuspended() will return true and if it had a
 * transaction name, it can be retrieved using getOriginalTransactionName().
 * It is possible that an original transaction existed with a null
 * transaction name.</li>
 * 
 * <li>When {@link #beginTransaction()} is called, a new transaction is
 * forced with {@link TransactionDefinition#PROPAGATION_REQUIRES_NEW}
 * semantics. If the transaction manager supports it and if there is an
 * existing transaction already established, it will be suspended (which will
 * be later resumed upon commit or rollback of this new transaction).</li>
 * 
 * <li>When {@link #commit()} or {@link #rollback()} is called, this
 * implementation guarantees (if synchronization is enabled) that this
 * managed transaction is still the current transaction within the
 * transaction manager before commencement of the commit or rollback.</li>
 * 
 * </ul>
 * 
 * @author Brian Koehmstedt
 */
public class ManagedSpringTransactionImpl implements ManagedSpringTransaction {

    private final Logger log = LoggerFactory.getLogger(ManagedSpringTransactionImpl.class);

    private static final AlternativeJdkIdGenerator idGenerator = new AlternativeJdkIdGenerator();

    /* General Notes
     * 
     * The way it seems to work in Spring is only TransactionStatus objects
     * that return isNewTransaction()==true can be committed. Otherwise
     * Spring considers it not the "originator" or "root" txStatus and Spring
     * will only really commit the "true originator" of a transaction.
     * commit() calls on isNewTransaction()==false status objects are silent
     * no-ops. That's why managed transactions have to start with a new
     * transaction and can't start with an existing transaction (at least in
     * the cases where the original txStatus is not available: we could add
     * support for existing txStatus objects where isNewTransaction==true).
     * 
     * For transaction managers that support transaction suspension, when a
     * new transaction is started with beginTransaction() here, if there's an
     * existing transaction, it will be suspended. That transaction will be
     * resumed when this transaction is committed or rolled back. */

    private PlatformTransactionManager _transactionManager;
    private TransactionStatus _txStatus;
    private SpringTransactionSynchronization _synchronization;
    /* if true, transaction manager must support synchronization and have it
     * set to something other than SYNCHRONIZATION_NEVER */
    private boolean _isSynchronizationEnforced = true;
    private volatile boolean _isStarted;
    private DefaultTransactionDefinition _transactionDefinition = new DefaultTransactionDefinition(
            TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    /* If synchronization enabled: was there a transaction suspended when
     * this managed transaction was begun? */
    private boolean originalTransactionWasSuspended = false;
    /* If synchronization enabled: the name of the transaction that was
     * suspended when this managed transaction was begun. It's possible
     * originalTransactionWasSuspended=true but the transaction name for that
     * transaction was null. */
    private String originalTransactionName = null;

    public ManagedSpringTransactionImpl() {
    }

    /**
     * Initializes isSynchronizationEnforced to true if transactionManager is
     * an instance of AbstractPlatformTransactionManager.
     */
    public ManagedSpringTransactionImpl(PlatformTransactionManager transactionManager) {
        this(transactionManager, (transactionManager instanceof AbstractPlatformTransactionManager)
                || TransactionSynchronizationManager.isSynchronizationActive(), null);
    }

    public ManagedSpringTransactionImpl(PlatformTransactionManager transactionManager, TransactionDefinition def) {
        this(transactionManager, (transactionManager instanceof AbstractPlatformTransactionManager)
                || TransactionSynchronizationManager.isSynchronizationActive(), def);
    }

    public ManagedSpringTransactionImpl(PlatformTransactionManager transactionManager, String txName) {
        this(transactionManager, (transactionManager instanceof AbstractPlatformTransactionManager)
                || TransactionSynchronizationManager.isSynchronizationActive(), null);
        setTransactionName(txName);
    }

    public ManagedSpringTransactionImpl(PlatformTransactionManager transactionManager,
            boolean isSynchronizationEnforced, TransactionDefinition def) {
        setSynchronizationEnforced(isSynchronizationEnforced);
        setTransactionManager(transactionManager);
        if (def != null) {
            setTransactionDefinition(def);
        }
    }

    @Override
    public TransactionStatus getTransactionStatus() {
        return _txStatus;
    }

    @Override
    public boolean isStarted() {
        return _isStarted;
    }

    @Override
    public SpringTransactionSynchronization getSynchronization() {
        return _synchronization;
    }

    @Override
    public SynchronizationState getSynchronizationState() {
        return (getSynchronization() != null ? getSynchronization().getState() : SynchronizationState.NOT_SUPPORTED);
    }

    public PlatformTransactionManager getTransactionManager() {
        return _transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this._transactionManager = transactionManager;
    }

    @Override
    public boolean isSynchronizationEnforced() {
        return _isSynchronizationEnforced;
    }

    public void setSynchronizationEnforced(boolean isSynchronizationEnforced) {
        this._isSynchronizationEnforced = isSynchronizationEnforced;
    }

    @Override
    public TransactionDefinition getTransactionDefinition() {
        return _transactionDefinition;
    }

    @Override
    public void setTransactionDefinition(TransactionDefinition transactionDefinition) {
        if (transactionDefinition.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            throw new IllegalArgumentException("The only supported propagation behavior is PROPAGATION_REQUIRES_NEW");
        }
        _transactionDefinition.setIsolationLevel(transactionDefinition.getIsolationLevel());
        _transactionDefinition.setName(transactionDefinition.getName());
        _transactionDefinition.setTimeout(transactionDefinition.getTimeout());
        _transactionDefinition.setReadOnly(transactionDefinition.isReadOnly());
    }

    @Override
    public void setTransactionName(String txName) {
        ((DefaultTransactionDefinition) getTransactionDefinition()).setName(txName);
    }

    @Override
    public String getTransactionName() {
        return getTransactionDefinition().getName();
    }

    @Override
    public boolean isSynchronizationSupported() {
        return ((getTransactionManager() instanceof AbstractPlatformTransactionManager)
                && (((AbstractPlatformTransactionManager) getTransactionManager())
                        .getTransactionSynchronization() != AbstractPlatformTransactionManager.SYNCHRONIZATION_NEVER))
                || TransactionSynchronizationManager.isSynchronizationActive();
    }

    private void assertSynchronizationEnforced() {
        if (isSynchronizationEnforced()) {
            if (!isSynchronizationSupported()) {
                throw new RuntimeException(
                        "isSynchronizationEnforced is enabled and synchronization is not supported or it's set to SYNCHRONIZATION_NEVER");
            }
        }
    }

    @Override
    public synchronized void beginTransaction() throws IllegalStateException {
        if (isStarted()) {
            throw new IllegalStateException("This transaction has already begun");
        }

        assertSynchronizationEnforced();

        /* If synchronization is supported and there's a transaction active,
         * note the transaction name as the original name that we're
         * suspending. */
        boolean synchronizationOriginalTxActive = false;
        String synchronizationOriginalTxName = null;
        if (isSynchronizationSupported()) {
            synchronizationOriginalTxActive = TransactionSynchronizationManager.isActualTransactionActive();
            synchronizationOriginalTxName = TransactionSynchronizationManager.getCurrentTransactionName();
        }

        if (getTransactionName() == null) {
            setTransactionName(getUniqueTransactionName());
        }

        if (log.isTraceEnabled()) {
            log.trace("creating new tx using txName=" + getTransactionName());
        }

        /* Use the configured transactionDefinition that must be for a new
         * transaction. */
        DefaultTransactionDefinition txDef = (DefaultTransactionDefinition) getTransactionDefinition();

        /* Establish a new transaction status. */
        initNewTransactionFromTransactionDefinition(txDef);

        /* If synchronization is supported, add a synchronization callback
         * object. */
        initNewSynchronization(txDef.getName(), synchronizationOriginalTxActive, synchronizationOriginalTxName);

        this._isStarted = true;
    }

    private void initNewTransactionFromTransactionDefinition(TransactionDefinition txDef) {
        this._txStatus = getTransactionManager().getTransaction(txDef);
        assertWithException(_txStatus.isNewTransaction());
    }

    private void initNewSynchronization(String txName, boolean originalTxActive, String originalTxName) {
        if (isSynchronizationEnforced()) {
            assertWithException(TransactionSynchronizationManager.isSynchronizationActive());
            assertWithException(TransactionSynchronizationManager.isActualTransactionActive());
            assertWithException(txName.equals(TransactionSynchronizationManager.getCurrentTransactionName()));

            /* Initialize the synchronization object that belongs to this
             * transaction. */
            this._synchronization = new SpringTransactionSynchronization(this);
            _synchronization.init();

            /* Register the synchronization with the current transaction. */
            TransactionSynchronizationManager.registerSynchronization(_synchronization);
            _synchronization.assertTransactionCurrentAndActive();

            this.originalTransactionWasSuspended = originalTxActive;
            this.originalTransactionName = originalTxName;
        }
    }

    private void assertBegun() throws IllegalStateException {
        if (!isStarted()) {
            throw new IllegalStateException("This transaction has not begun");
        }
    }

    /**
     * @param logMsgs
     *            If true, log warnings if not current or active.
     */
    public boolean isCurrentAndActive(boolean logMsgs) throws IllegalStateException {
        assertBegun();
        if (isSynchronizationEnforced()) {
            boolean isCompleted = getTransactionStatus().isCompleted();
            if (logMsgs && isCompleted) {
                log.warn("Transaction is not active because it's already completed");
            }
            boolean isTxCurrentAndActive = getSynchronization().isTransactionCurrentAndActive(getTransactionName(),
                    logMsgs);
            if (logMsgs && !isTxCurrentAndActive) {
                log.warn("The synchronization says the transaction is not current and active");
            }
            return !isCompleted && isTxCurrentAndActive;
        } else {
            /* since synchronization is not supported, all we have to go on
             * is txStatus.isCompleted */
            boolean isCompleted = getTransactionStatus().isCompleted();
            if (isCompleted) {
                log.warn("Transaction is not active because it's already completed (synchronization is not supported)");
            }
            return !isCompleted;
        }
    }

    @Override
    public boolean isCurrentAndActive() throws IllegalStateException {
        return isCurrentAndActive(false);
    }

    public void assertCurrentAndActive() throws IllegalStateException {
        assertWithException(isCurrentAndActive(true));
    }

    @Override
    public boolean isCurrentAndActiveAndNotRollbackOnly() throws IllegalStateException {
        return isCurrentAndActive() && !getTransactionStatus().isRollbackOnly();
    }

    public void assertCurrentAndActiveAndNotRollbackOnly() throws IllegalStateException {
        assertWithException(isCurrentAndActiveAndNotRollbackOnly());
    }

    @Override
    public boolean isRollbackOnly() {
        return getTransactionStatus().isRollbackOnly();
    }

    @Override
    public void markRollbackOnly() {
        assertCurrentAndActive();
        getTransactionStatus().setRollbackOnly();
        if (log.isTraceEnabled()) {
            log.trace("marked as rollback-only");
        }
    }

    protected void assertNotAlreadyCompleted() {
        if (getTransactionStatus().isCompleted()) {
            String msg = "This transaction is already completed: rolledBack=" + isRolledBack() + ", committed="
                    + isCommitted() + ", state=" + getSynchronization().getState().name() + ".";
            log.error(msg
                    + "  This is the stack trace of the last state change for the synchronization to help you figure out where it was originally committed or rolled back",
                    getSynchronization().getLastStateChangeAsThrowable());
            throw new RuntimeException(msg);
        }
    }

    @Override
    public void commit() throws IllegalStateException {
        assertNotAlreadyCompleted();
        if (getTransactionStatus().isRollbackOnly()) {
            if (log.isTraceEnabled()) {
                log.trace("marked as rollback-only so rolling back instead of committing");
            }
            rollback();
        } else {
            assertCurrentAndActive();
            if (log.isTraceEnabled()) {
                log.trace("committing");
            }
            getTransactionManager().commit(getTransactionStatus());
            assertWithException(getTransactionStatus().isCompleted());
            if (log.isTraceEnabled()) {
                log.trace("done with commit");
            }
            if (isSynchronizationEnforced()) {
                assertWithException(isCommitted());
            }
        }
    }

    @Override
    public void rollback() throws IllegalStateException {
        assertNotAlreadyCompleted();
        assertCurrentAndActive();
        if (log.isTraceEnabled()) {
            log.trace("rolling back");
        }
        getTransactionManager().rollback(getTransactionStatus());
        assertWithException(getTransactionStatus().isCompleted());
        if (log.isTraceEnabled()) {
            log.trace("done with rollback");
        }
        if (isSynchronizationEnforced()) {
            assertWithException(isRolledBack());
        }
    }

    @Override
    public boolean isCommitted() throws IllegalStateException {
        if (!isSynchronizationSupported()) {
            throw new IllegalStateException("Synchronization is not supported for this transaction manager");
        }
        return getSynchronization().getState().equals(SynchronizationState.COMMITTED);
    }

    @Override
    public boolean isRolledBack() throws IllegalStateException {
        if (!isSynchronizationSupported()) {
            throw new IllegalStateException("Synchronization is not supported for this transaction manager");
        }
        return getSynchronization().getState().equals(SynchronizationState.ROLLED_BACK);
    }

    @Override
    public boolean getOriginalTransactionWasSuspended() {
        return originalTransactionWasSuspended;
    }

    @Override
    public String getOriginalTransactionName() {
        return originalTransactionName;
    }

    protected static IdGenerator getIdGenerator() {
        return idGenerator;
    }

    protected static String getUniqueTransactionName() {
        return getIdGenerator().generateId().toString();
    }

    private static void assertWithException(boolean condition) throws RuntimeException {
        if (!condition) {
            throw new RuntimeException("assertion failed");
        }
    }

    /**
     * If synchronization is enabled, get the currently active
     * SpringTransactionSynchronization.
     */
    public static SpringTransactionSynchronization getCurrentTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                if (sync instanceof SpringTransactionSynchronization) {
                    return ((SpringTransactionSynchronization) sync);
                }
            }
        }
        return null;
    }

    /**
     * If synchronization is enabled, get the currently active
     * ManagedSpringTransaction.
     */
    public static ManagedSpringTransaction getCurrentManagedSpringTransaction() {
        SpringTransactionSynchronization synchronization = getCurrentTransactionSynchronization();
        return (synchronization != null ? synchronization.getOwningManagedTransaction() : null);
    }
}
