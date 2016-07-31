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

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * An implementation of a Spring <code>TransactionSynchronization</code> that
 * tracks transaction state throughout the lifecycle of the transaction.
 * 
 * @author Brian Koehmstedt
 */
public class SpringTransactionSynchronization implements TransactionSynchronization, Serializable {
    private static final long serialVersionUID = 352975547922387276L;
    // private final transient Logger log =
    // LoggerFactory.getLogger(getClass());

    private SynchronizationState state;
    private String txName;

    public SpringTransactionSynchronization() {
        this.state = SynchronizationState.UNINITIALIZED;
    }

    @Override
    public String toString() {
        return txName;
    }

    public void init() {
        assert TransactionSynchronizationManager.isSynchronizationActive();
        assert TransactionSynchronizationManager.isActualTransactionActive();
        this.txName = TransactionSynchronizationManager.getCurrentTransactionName();
        this.state = SynchronizationState.ACTIVE;
    }

    public boolean isTransactionCurrent() {
        return toString().equals(TransactionSynchronizationManager.getCurrentTransactionName());
    }

    public boolean isTransactionCurrent(String txName) {
        return toString().equals(txName)
                && toString().equals(TransactionSynchronizationManager.getCurrentTransactionName());
    }

    public void assertTransactionCurrent() {
        assert isTransactionCurrent();
    }

    public boolean isTransactionCurrentAndActive() {
        return SynchronizationState.ACTIVE.equals(state) && isTransactionCurrent()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    public boolean isTransactionCurrentAndActive(String txName) {
        return SynchronizationState.ACTIVE.equals(state) && isTransactionCurrent(txName)
                && TransactionSynchronizationManager.isActualTransactionActive();
    }

    public void assertTransactionCurrentAndActive() {
        assert isTransactionCurrentAndActive();
    }

    @Override
    public void suspend() {
        assertTransactionCurrent();
        state = SynchronizationState.SUSPENDED;
    }

    @Override
    public void resume() {
        assertTransactionCurrentAndActive();
        state = SynchronizationState.ACTIVE;
    }

    @Override
    public void flush() {
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
    }

    public SynchronizationState getState() {
        return state;
    }
}
