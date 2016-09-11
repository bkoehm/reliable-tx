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

import java.util.Properties;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionUsageException;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * @author Brian Koehmstedt
 */
public class ExtendedTransactionInterceptor extends TransactionInterceptor {

    private static final long serialVersionUID = -1313055992511430605L;

    public ExtendedTransactionInterceptor() {
        super();
    }

    public ExtendedTransactionInterceptor(PlatformTransactionManager ptm, Properties attributes) {
        super(ptm, attributes);
    }

    public ExtendedTransactionInterceptor(PlatformTransactionManager ptm, TransactionAttributeSource tas) {
        super(ptm, tas);
    }

    /**
     * Important note: This won't get called if a transactional method calls
     * another in the same class instance. So the transaction name check will
     * only happen between method calls across different class instances.
     * 
     * @see org.springframework.transaction.interceptor.TransactionAspectSupport#createTransactionIfNecessary(org.springframework.transaction.PlatformTransactionManager,
     *      org.springframework.transaction.interceptor.TransactionAttribute,
     *      java.lang.String)
     */
    @Override
    protected TransactionInfo createTransactionIfNecessary(PlatformTransactionManager tm, TransactionAttribute txAttr,
            String joinpointIdentification) {
        if (txAttr != null) {
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
            TransactionInfo currentTxInfo = currentTransactionInfo();
            if (currentTxInfo != null && currentTxInfo.hasTransaction()) {
                switch (txAttr.getPropagationBehavior()) {
                    case TransactionDefinition.PROPAGATION_MANDATORY:
                    case TransactionDefinition.PROPAGATION_REQUIRED:
                    case TransactionDefinition.PROPAGATION_SUPPORTS: {
                        // throws an exception if the transaction names don't
                        // match
                        assertExistingTxNameMatchesRequestedTxName(currentTxInfo.getTransactionAttribute(), txAttr,
                                joinpointIdentification);
                    }
                        break;
                    default:
                }
            }

        }

        return super.createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
    }

    protected void assertExistingTxNameMatchesRequestedTxName(TransactionAttribute existingTxAttr,
            TransactionAttribute requestedTxAttr, String requestedJoinpointIdentification)
            throws TransactionUsageException {
        if (requestedJoinpointIdentification == null) {
            throw new IllegalStateException(
                    "The joinpointIdentification for the requested transaction is unexpectedly null.");
        }
        String requestedTxName = (requestedTxAttr.getName() != null ? requestedTxAttr.getName()
                : requestedJoinpointIdentification);
        String existingTxName = existingTxAttr.getName();
        if (existingTxName == null) {
            throw new IllegalStateException("The existing transaction unexpectedly has no name.");
        }
        if (!requestedTxName.equals(existingTxName)) {
            throw new TransactionUsageException(
                    "Specified propagation behavior supports an existing transaction but the existing transaction name of '"
                            + existingTxName + "' does not match the specified transaction name of '" + requestedTxName
                            + "'");
        }
    }
}
