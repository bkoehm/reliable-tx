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
package software.reliabletx.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Primarily ensures that when a {@link #commit()} or {@link #rollback()} is
 * called, the managed transaction is still the current transaction.
 * 
 * More specifically, this implementation adds the following semantics to a
 * standard Spring transaction:
 * 
 * <ul>
 * <li>Although not absolutely required, for reliable transactions the Spring
 * transaction manager in use should extend
 * <code>AbstractPlatformTransactionManager</code> and it should be using
 * synchronization: <code>SYNCHRONIZATION_ALWAYS</code> or
 * <code>SYNCHRONIZATION_ON_ACTUAL_TRANSACTION</code>.</li>
 * <li>{@link #beginTransaction(String)} must be called to start the
 * transaction. A transaction name must be provided. While the name should be
 * a unique string per transaction, this implementation does not enforce that
 * uniqueness.</li>
 * <li>When {@link #beginTransaction(String)} is called, a new transaction is
 * forced with <code>TransactionDefinition.PROPAGATION_REQUIRES_NEW</code>
 * semantics. If the transaction manager supports it and if there is an
 * existing transaction already established, it will be suspended (which will
 * be later resumed upon commit or rollback of this new transaction).</li>
 * <li>When {@link #commit()} or {@link #rollback()} is called, this
 * implementation guarantees (if synchronization is enabled) that this
 * managed transaction is still the current transaction within the
 * transaction manager.</li>
 * </ul>
 * 
 * @author Brian Koehmstedt
 */
public class ManagedSpringTransactionImpl implements ManagedSpringTransaction {

    private final Logger log = LoggerFactory.getLogger(getClass());

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
    private String _txName;
    /* if true, transaction manager must support synchronization and have it
     * set to something other than SYNCHRONIZATION_NEVER */
    private boolean _isSynchronizationEnforced = true;
    private volatile boolean _isStarted;

    public ManagedSpringTransactionImpl() {
    }

    /**
     * Initializes isSynchronizationEnforced to true if transactionManager is
     * an instance of AbstractPlatformTransactionManager.
     */
    public ManagedSpringTransactionImpl(PlatformTransactionManager transactionManager) {
        this(transactionManager, (transactionManager instanceof AbstractPlatformTransactionManager));
    }

    public ManagedSpringTransactionImpl(PlatformTransactionManager transactionManager,
            boolean isSynchronizationEnforced) {
        setSynchronizationEnforced(isSynchronizationEnforced);
        setTransactionManager(transactionManager);
    }

    public TransactionStatus getTxStatus() {
        return _txStatus;
    }

    public boolean isStarted() {
        return _isStarted;
    }

    protected SpringTransactionSynchronization getSynchronization() {
        return _synchronization;
    }

    public SynchronizationState getSynchronizationState() {
        return (getSynchronization() != null ? getSynchronization().getState() : SynchronizationState.NOT_SUPPORTED);
    }

    public PlatformTransactionManager getTransactionManager() {
        return _transactionManager;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this._transactionManager = transactionManager;
    }

    public boolean isSynchronizationEnforced() {
        return _isSynchronizationEnforced;
    }

    public void setSynchronizationEnforced(boolean isSynchronizationEnforced) {
        this._isSynchronizationEnforced = isSynchronizationEnforced;
    }

    public String getTransactionName() {
        return _txName;
    }

    protected TransactionDefinition getPropagationRequiresNewTransactionDefinition(String txName) {
        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txDef.setName(txName);
        return txDef;
    }

    public boolean isSynchronizationSupported() {
        return (getTransactionManager() instanceof AbstractPlatformTransactionManager)
                && (((AbstractPlatformTransactionManager) getTransactionManager())
                        .getTransactionSynchronization() != AbstractPlatformTransactionManager.SYNCHRONIZATION_NEVER);
    }

    private void assertSynchronizationEnforced() {
        if (isSynchronizationEnforced()) {
            if (!isSynchronizationSupported()) {
                throw new RuntimeException(
                        "isSynchronizationEnforced is enabled and synchronization is not supported or it's set to SYNCHRONIZATION_NEVER");
            }
        }
    }

    public synchronized void beginTransaction(String txName) throws IllegalStateException {
        if (isStarted()) {
            throw new IllegalStateException("This transaction has already begun");
        }
        assert txName != null;
        if (log.isTraceEnabled())
            log.trace("creating new tx using txName=" + txName);

        assertSynchronizationEnforced();

        /* Start the new transaction. */
        TransactionDefinition txDef = getPropagationRequiresNewTransactionDefinition(txName);

        /* Confirm we're in a new-transaction state. */
        initNewTransactionDefinition(txDef);

        /* If synchronization is supported, add a synchronization callback
         * object. */
        initNewSynchronization(txName);

        this._txName = txName;
        this._isStarted = true;
    }

    private void initNewTransactionDefinition(TransactionDefinition txDef) {
        this._txStatus = getTransactionManager().getTransaction(txDef);
        assert _txStatus.isNewTransaction();
    }

    private void initNewSynchronization(String txName) {
        if (isSynchronizationSupported()) {
            assert TransactionSynchronizationManager.isSynchronizationActive();
            assert TransactionSynchronizationManager.isActualTransactionActive();
            assert txName.equals(TransactionSynchronizationManager.getCurrentTransactionName());

            /* Initialize the synchronization object that belongs to this
             * transaction. */
            this._synchronization = new SpringTransactionSynchronization();
            _synchronization.init();

            /* Register the synchronization with the current transaction. */
            TransactionSynchronizationManager.registerSynchronization(_synchronization);
            _synchronization.assertTransactionCurrentAndActive();
        }
    }

    private void assertBegun() throws IllegalStateException {
        if (!isStarted()) {
            throw new IllegalStateException("This transaction has not begun");
        }
    }

    public boolean isCurrentAndActive() throws IllegalStateException {
        assertBegun();
        if (isSynchronizationSupported()) {
            return !getTxStatus().isCompleted()
                    && getSynchronization().isTransactionCurrentAndActive(getTransactionName());
        } else {
            /* since synchronization is not supported, all we have to go on
             * is txStatus.isCompleted */
            return !getTxStatus().isCompleted();
        }
    }

    public void assertCurrentAndActive() throws IllegalStateException {
        assert isCurrentAndActive();
    }

    public boolean isCurrentAndActiveAndNotRollbackOnly() throws IllegalStateException {
        return isCurrentAndActive() && !getTxStatus().isRollbackOnly();
    }

    public void assertCurrentAndActiveAndNotRollbackOnly() throws IllegalStateException {
        assert isCurrentAndActiveAndNotRollbackOnly();
    }

    public boolean isRollbackOnly() {
        return getTxStatus().isRollbackOnly();
    }

    public void markRollbackOnly() {
        assertCurrentAndActive();
        getTxStatus().setRollbackOnly();
        if (log.isTraceEnabled())
            log.trace("marked as rollback-only");
    }

    public void commit() throws IllegalStateException {
        if (getTxStatus().isRollbackOnly()) {
            if (log.isTraceEnabled())
                log.trace("marked as rollback-only so rolling back instead of committing");
            rollback();
        } else {
            assertCurrentAndActive();
            if (log.isTraceEnabled())
                log.trace("committing");
            getTransactionManager().commit(getTxStatus());
            assert getTxStatus().isCompleted();
            if (log.isTraceEnabled())
                log.trace("done with commit");
            if (isSynchronizationSupported()) {
                assert isCommitted();
            }
        }
    }

    public void rollback() throws IllegalStateException {
        assertCurrentAndActive();
        if (log.isTraceEnabled())
            log.trace("rolling back");
        getTransactionManager().rollback(getTxStatus());
        assert getTxStatus().isCompleted();
        if (log.isTraceEnabled())
            log.trace("done with rollback");
        if (isSynchronizationSupported()) {
            assert isRolledBack();
        }
    }

    public boolean isCommitted() throws IllegalStateException {
        if (!isSynchronizationSupported())
            throw new IllegalStateException("Synchronization is not supported for this transaction manager");
        return getSynchronization().getState().equals(SynchronizationState.COMMITTED);
    }

    public boolean isRolledBack() throws IllegalStateException {
        if (!isSynchronizationSupported())
            throw new IllegalStateException("Synchronization is not supported for this transaction manager");
        return getSynchronization().getState().equals(SynchronizationState.ROLLED_BACK);
    }
}
