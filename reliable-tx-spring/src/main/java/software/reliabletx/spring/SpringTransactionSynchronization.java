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

import java.io.Serializable;
import java.io.StringWriter;
import java.io.IOException;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An implementation of a Spring {@link TransactionSynchronization} that
 * tracks transaction state throughout the lifecycle of the transaction.
 * 
 * @author Brian Koehmstedt
 */
public class SpringTransactionSynchronization implements TransactionSynchronization, Serializable {
    private static final long serialVersionUID = 352975547922387276L;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ManagedSpringTransaction owningTx;
    private SynchronizationState state;
    private String txName;
    private StackTraceElement[] lastStateChangeStackTrace;

    public SpringTransactionSynchronization(ManagedSpringTransaction owningTx) {
        this.owningTx = owningTx;
        this.state = SynchronizationState.UNINITIALIZED;
    }

    @Override
    public String toString() {
        return txName;
    }

    public void init() {
        assertWithException(TransactionSynchronizationManager.isSynchronizationActive());
        assertWithException(TransactionSynchronizationManager.isActualTransactionActive());
        this.txName = TransactionSynchronizationManager.getCurrentTransactionName();
        this.state = SynchronizationState.ACTIVE;
        updateStackTrace();
    }

    public boolean isTransactionCurrent() {
        return toString().equals(TransactionSynchronizationManager.getCurrentTransactionName());
    }

    public boolean isTransactionCurrent(String txName) {
        return toString().equals(txName)
                && toString().equals(TransactionSynchronizationManager.getCurrentTransactionName());
    }

    public void assertTransactionCurrent() {
        assertWithException(isTransactionCurrent());
    }

    public boolean isTransactionCurrentAndActive() {
        return isTransactionCurrentAndActive(false);
    }

    /**
     * @param logMsgs
     *            If true, log warnings if not current or active.
     */
    public boolean isTransactionCurrentAndActive(boolean logMsgs) {
        if (logMsgs) {
            if (!SynchronizationState.ACTIVE.equals(state)) {
                log.warn("Synchronization state for transaction is not active");
            }
            if (!isTransactionCurrent()) {
                log.warn("This transaction is not the current transaction");
            } else if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                log.warn("The TransactionSynchronizationManager says this transaction is not active");
            }
        }
        return SynchronizationState.ACTIVE.equals(state) && isTransactionCurrent()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    public boolean isTransactionCurrentAndActive(String txName) {
        return isTransactionCurrentAndActive(txName, false);
    }

    public boolean isTransactionCurrentAndActive(String txName, boolean logMsgs) {
        if (logMsgs) {
            if (!SynchronizationState.ACTIVE.equals(state)) {
                log.warn("Synchronization state for transaction is not active");
            }
            if (!isTransactionCurrent(txName)) {
                log.warn("Transaction " + txName
                        + " is not the current transaction.  Instead, TransactionSynchronizationManager is reporting "
                        + TransactionSynchronizationManager.getCurrentTransactionName()
                        + " as the current transaction.");
            } else if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                log.warn("The TransactionSynchronizationManager says the current transaction is not active");
            }
        }
        return SynchronizationState.ACTIVE.equals(state) && isTransactionCurrent(txName)
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    public void assertTransactionCurrentAndActive() {
        assertWithException(isTransactionCurrentAndActive(true));
    }

    @Override
    public void suspend() {
        assertTransactionCurrent();
        state = SynchronizationState.SUSPENDED;
        updateStackTrace();
    }

    @Override
    public void resume() {
        state = SynchronizationState.ACTIVE;
        updateStackTrace();
        assertTransactionCurrentAndActive();
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void beforeCommit(boolean readOnly) {
        assertTransactionCurrentAndActive();
    }

    @Override
    public void beforeCompletion() {
        assertTransactionCurrentAndActive();
    }

    @Override
    public void afterCommit() {
        assertTransactionCurrentAndActive();
    }

    @Override
    public void afterCompletion(int status) {
        assertTransactionCurrentAndActive();
        switch (status) {
            case STATUS_COMMITTED:
                state = SynchronizationState.COMMITTED;
                break;
            case STATUS_ROLLED_BACK:
                state = SynchronizationState.ROLLED_BACK;
                break;
            default:
                state = SynchronizationState.COMPLETED_BUT_UNKNOWN;
                break;
        }
        updateStackTrace();
    }

    public SynchronizationState getState() {
        return state;
    }

    public ManagedSpringTransaction getOwningManagedTransaction() {
        return owningTx;
    }

    private static void assertWithException(boolean condition) throws RuntimeException {
        if (!condition) {
            throw new RuntimeException("assertion failed");
        }
    }

    public StackTraceElement[] getLastStateChangeStackTrace() {
        return lastStateChangeStackTrace;
    }

    public Throwable getLastStateChangeAsThrowable() {
        Throwable t = new Throwable();
        t.setStackTrace(lastStateChangeStackTrace);
        return t;
    }

    public String getLastStateChangeStackTraceAsString() throws IOException {
        Throwable t = getLastStateChangeAsThrowable();
        StringWriter sw = new StringWriter();
        PrintWriter writer = new PrintWriter(sw);
        String str = null;
        try {
            t.printStackTrace(writer);
            str = sw.toString();
        } finally {
            writer.close();
            sw.close();
        }
        return str;
    }

    protected void updateStackTrace() {
        this.lastStateChangeStackTrace = Thread.currentThread().getStackTrace();
    }
}
