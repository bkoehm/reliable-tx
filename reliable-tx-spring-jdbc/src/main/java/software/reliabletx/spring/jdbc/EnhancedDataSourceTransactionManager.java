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
package software.reliabletx.spring.jdbc;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

import software.reliabletx.spring.EnhancedTransactionManagerUtil;

/**
 * @author Brian Koehmstedt
 */
public class EnhancedDataSourceTransactionManager extends DataSourceTransactionManager {

    private static final long serialVersionUID = 2483003329861737163L;

    public EnhancedDataSourceTransactionManager() {
        super();
    }

    public EnhancedDataSourceTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    /**
     * Important note: This won't get called if a transactional method calls
     * another in the same class instance. So the transaction name check will
     * only happen between method calls across different class instances.
     * 
     * @see org.springframework.transaction.support.AbstractPlatformTransactionManager#newTransactionStatus(org.springframework.transaction.TransactionDefinition,
     *      java.lang.Object, boolean, boolean, boolean, java.lang.Object)
     */
    @Override
    protected DefaultTransactionStatus newTransactionStatus(TransactionDefinition definition, Object transaction,
            boolean newTransaction, boolean newSynchronization, boolean debug, Object suspendedResources) {
        // throws an exception if the name check doesn't pass
        EnhancedTransactionManagerUtil.checkNewTransactionStatusForName(definition);
        return super.newTransactionStatus(definition, transaction, newTransaction, newSynchronization, debug,
                suspendedResources);
    }
}
