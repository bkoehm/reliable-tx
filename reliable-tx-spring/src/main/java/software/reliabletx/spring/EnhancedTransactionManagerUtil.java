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
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Brian Koehmstedt
 */
public abstract class EnhancedTransactionManagerUtil {

    /**
     * Call this from an overridden {@code newTransactionStatus()} in a class
     * that extends
     * {@link org.springframework.transaction.support.AbstractPlatformTransactionManager}
     * .
     *
     * <p>
     * Important note:
     * {@code AbstractPlatformTransactionManager.newTransactionStatus()}
     * won't get called if a transactional method calls another in the same
     * class instance. So the transaction name check will only happen between
     * method calls across different class instances.
     * </p>
     * 
     * @throws TransactionUsageException
     *             If there is an existing transaction and the requested
     *             TransactionDefinition propagation behavior supports an
     *             existing transaction and the definition's transaction name
     *             does not match the name of the existing transaction.
     * 
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#newTransactionStatus(org.springframework.transaction.TransactionDefinition,
     *      java.lang.Object, boolean, boolean, boolean, java.lang.Object)
     */
    public static void checkNewTransactionStatusForName(TransactionDefinition definition)
            throws TransactionUsageException {
        if (definition != null && TransactionSynchronizationManager.isSynchronizationActive()
                && definition.getName() != null) {
            /**
             * The propagation behaviors that support executing within the
             * current transaction: PROPAGATION_MANDATORY,
             * PROPAGATION_REQUIRED, PROPAGATION_SUPPORTS.
             * 
             * <p>
             * If txAttr indicates any of these propagation behaviors and
             * there's an existing transaction, we want to assert that the
             * existing transaction name matches the transaction name in
             * txAttr.
             * </p>
             */
            String currentTransactionName = TransactionSynchronizationManager.getCurrentTransactionName();
            if (currentTransactionName == null) {
                throw new IllegalStateException(
                        "TransactionSynchronizationManager.isSynchronizationActive() returns true but TransactionSynchronizationManager.getCurrentTransactionName() returns null");
            }
            switch (definition.getPropagationBehavior()) {
                case TransactionDefinition.PROPAGATION_MANDATORY:
                case TransactionDefinition.PROPAGATION_REQUIRED:
                case TransactionDefinition.PROPAGATION_SUPPORTS:
                    // throws an exception if the transaction names don't
                    // match
                    assertExistingTxNameMatchesRequestedTxName(currentTransactionName, definition.getName());
                    break;
                default:
            }
        }
    }

    protected static void assertExistingTxNameMatchesRequestedTxName(String existingTxName, String requestedTxName)
            throws TransactionUsageException {
        if (!requestedTxName.equals(existingTxName)) {
            throw new TransactionUsageException(
                    "Specified propagation behavior supports an existing transaction but the existing transaction name of '"
                            + existingTxName + "' does not match the specified transaction name of '" + requestedTxName
                            + "'");
        }
    }
}
