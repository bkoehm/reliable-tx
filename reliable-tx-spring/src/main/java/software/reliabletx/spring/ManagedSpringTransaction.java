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

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * Adds the following to a standard Spring transaction:
 * <ul>
 * <li>A name for the transaction.</li>
 * <li>Attaches a {@link SynchronizationState} to the the transaction to
 * track state during the transaction lifecycle.</li>
 * </ul>
 * 
 * @author Brian Koehmstedt
 */
public interface ManagedSpringTransaction {
    void beginTransaction() throws IllegalStateException;

    boolean isStarted();

    boolean isCurrentAndActive() throws IllegalStateException;

    boolean isCurrentAndActiveAndNotRollbackOnly() throws IllegalStateException;

    TransactionStatus getTransactionStatus();

    SpringTransactionSynchronization getSynchronization();

    SynchronizationState getSynchronizationState();

    void commit() throws IllegalStateException;

    void rollback() throws IllegalStateException;

    boolean isRollbackOnly() throws IllegalStateException;

    void markRollbackOnly() throws IllegalStateException;

    boolean isSynchronizationSupported();

    boolean isCommitted() throws IllegalStateException;

    boolean isRolledBack() throws IllegalStateException;

    TransactionDefinition getTransactionDefinition();

    void setTransactionDefinition(TransactionDefinition transactionDefinition);

    String getTransactionName();

    void setTransactionName(String txName);

    boolean isSynchronizationEnforced();

    boolean getOriginalTransactionWasSuspended();

    String getOriginalTransactionName();
}
