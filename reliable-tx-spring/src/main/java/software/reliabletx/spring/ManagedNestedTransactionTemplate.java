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
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * This transaction template has these semantics:
 * <ul>
 * <li>Synchronization must be supported by the transaction manager and it
 * must be active.</li>
 * <li>If the current transaction is not a managed transaction and if the
 * propagation behavior supports creating new transactions, the current
 * transaction will be suspended and a new managed transaction will be
 * started.</li>
 * <li>If the propagation behavior doesn't support new transactions and the
 * current transaction isn't managed, an exception will be thrown.</li>
 * <li>If a transaction is suspended and it has a transaction name, it can be
 * retrieved using
 * TransactionSynchronizationManager.getBinding(ORIGINAL_TX_NAME_RESOURCE)
 * for as long as the new managed transaction stays the current and active
 * transaction.</li>
 * </ul>
 * 
 * <p>
 * For the purposes of supporting these semantics, these propagation
 * behaviors support suspending the current transaction and creating new
 * managed transactions: PROPAGATION_REQUIRES_NEW, PROPAGATION_REQUIRED,
 * PROPAGATION_SUPPORTS.
 * </p>
 * 
 * <p>
 * This template does not support PROPAGATION_NESTED and throws an exception
 * if an attempt is made to use that propagation behavior.
 * </p>
 * 
 * <p>
 * As such, the definition of the following behaviors are slightly modified
 * to:
 * </p>
 * <ul>
 * <li>PROPAGATION_REQUIRED: Support a current MANAGED transaction; create a
 * new one if no transaction exists. If an unmanaged transaction exists,
 * suspend it before creating the new managed transaction.</li>
 * <li>PROPAGATION_SUPPORTS: Support a current MANAGED transaction; execute
 * non-transactionally if no transaction exists. If an unmanaged transaction
 * exists, suspend it before creating the new managed transaction.</li>
 * </ul>
 * 
 *
 * @author Brian Koehmstedt
 */
public class ManagedNestedTransactionTemplate extends TransactionTemplate {

    private static final long serialVersionUID = -400074375111451364L;
    final Logger log = LoggerFactory.getLogger(getClass());
    public static final String ORIGINAL_TX_NAME_RESOURCE = "originalTxName";

    public ManagedNestedTransactionTemplate() {
        super();
        setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ManagedNestedTransactionTemplate(PlatformTransactionManager transactionManager) {
        super(transactionManager);
        setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ManagedNestedTransactionTemplate(PlatformTransactionManager transactionManager,
            TransactionDefinition transactionDefinition) {
        super(transactionManager, transactionDefinition);
    }

    @Override
    public <T> T execute(TransactionCallback<T> action) throws TransactionException {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new RuntimeException(
                    "Synchronization must be active.  TransactionSynchronizationManager.isSynchronizationActive() is false.");
        }

        int propagation = getPropagationBehavior();
        if (propagation == TransactionDefinition.PROPAGATION_NESTED) {
            throw new RuntimeException("PROPAGATION_NESTED not supported by this transaction template");
        }

        boolean currentTransactionExists = TransactionSynchronizationManager.isActualTransactionActive();
        boolean startNewTransaction;
        if (propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            startNewTransaction = true;
        } else if (propagation == TransactionDefinition.PROPAGATION_REQUIRED
                || propagation == TransactionDefinition.PROPAGATION_SUPPORTS) {
            if (currentTransactionExists) {
                ManagedSpringTransaction managedTx = null;
                for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                    if (sync instanceof SpringTransactionSynchronization) {
                        managedTx = ((SpringTransactionSynchronization) sync).getOwningManagedTransaction();
                        break;
                    }
                }
                /* Suspend the existing transaction and start a new
                 * transaction if the existing transaction is not managed. */
                startNewTransaction = managedTx == null;
            } else {
                /* no current transaction */
                startNewTransaction = true;
            }
        } else {
            startNewTransaction = false;
        }

        if (startNewTransaction) {
            /* start a new ManagedSpringTransaction */
            if (currentTransactionExists) {
                log.info("Suspending current transaction "
                        + TransactionSynchronizationManager.getCurrentTransactionName());
                if (TransactionSynchronizationManager.hasResource(ORIGINAL_TX_NAME_RESOURCE)) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(ORIGINAL_TX_NAME_RESOURCE);
                }
                // TODO: This should probably really go into the
                // ManagedSpringTransaction.
                TransactionSynchronizationManager.bindResource(ORIGINAL_TX_NAME_RESOURCE,
                        TransactionSynchronizationManager.getCurrentTransactionName());
            }
            DefaultTransactionDefinition requiresNew = new DefaultTransactionDefinition(this);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            ManagedSpringTransaction managedTx = createManagedSpringTransaction(getTransactionManager(), requiresNew);
            try {
                managedTx.beginTransaction();
            } catch (Throwable t) {
                throw new TransactionSystemException("Couldn't begin transaction", t);
            }
            log.info("Started a new transaction: " + managedTx.getTransactionName());
            if (!managedTx.getTransactionName().equals(TransactionSynchronizationManager.getCurrentTransactionName())) {
                throw new RuntimeException(
                        "TransactionSynchronizationManager is not reporting the right transaction name.  It's reporting "
                                + TransactionSynchronizationManager.getCurrentTransactionName());
            }
        } else if (currentTransactionExists && getExistingManagedTransaction() == null) {
            throw new RuntimeException(
                    "A current unmanaged transaction exists but the propagation behavior is not one that supports suspending the current transaction and creating a new managed transaction.");
        }

        return super.execute(action);
    }

    protected static ManagedSpringTransactionImpl createManagedSpringTransaction(PlatformTransactionManager txMgr,
            TransactionDefinition def) {
        return new ManagedSpringTransactionImpl(txMgr, true, def);
    }

    public static ManagedSpringTransaction getExistingManagedTransaction() {
        ManagedSpringTransaction managedTx = null;
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            if (sync instanceof SpringTransactionSynchronization) {
                managedTx = ((SpringTransactionSynchronization) sync).getOwningManagedTransaction();
                break;
            }
        }
        return managedTx;
    }
}
