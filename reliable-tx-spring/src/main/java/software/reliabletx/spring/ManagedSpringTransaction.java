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
    public void beginTransaction(String txName) throws IllegalStateException;

    public boolean isStarted();

    public String getTransactionName();

    public boolean isCurrentAndActive() throws IllegalStateException;

    public boolean isCurrentAndActiveAndNotRollbackOnly() throws IllegalStateException;

    public TransactionStatus getTxStatus();

    public SynchronizationState getSynchronizationState();

    public void commit() throws IllegalStateException;

    public void rollback() throws IllegalStateException;

    public boolean isRollbackOnly() throws IllegalStateException;

    public void markRollbackOnly() throws IllegalStateException;

    public boolean isSynchronizationSupported();

    public boolean isCommitted() throws IllegalStateException;

    public boolean isRolledBack() throws IllegalStateException;
}
