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
package io.github.bkoehm.reliabletx.spring.jdbc;

import javax.sql.DataSource;

import org.springframework.core.NamedThreadLocal;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.bkoehm.reliabletx.spring.EnhancedTransactionManagerUtil;

/**
 * @author Brian Koehmstedt
 */
public class EnhancedDataSourceTransactionManager extends DataSourceTransactionManager {

    private static final long serialVersionUID = 2483003329861737163L;
    private ThreadLocal<String> suspendedTransactionName = new NamedThreadLocal<String>(
            "Last suspended transaction name");

    public EnhancedDataSourceTransactionManager() {
        super();
    }

    public EnhancedDataSourceTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // throws an exception if the name checks don't pass
        EnhancedTransactionManagerUtil.checkNewTransactionStatusForName(definition, suspendedTransactionName.get());
        super.doBegin(transaction, definition);
    }

    /**
     * This won't get called if a transactional method calls another in the
     * same class instance.  So the transaction name check will only happen
     * between method calls across different class instances.
     */
    @Override
    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        // throws an exception if the name checks don't pass
        EnhancedTransactionManagerUtil.checkNewTransactionStatusForName(definition, suspendedTransactionName.get());
        super.prepareSynchronization(status, definition);
        EnhancedTransactionManagerUtil.prepareTrackingSynchronization(status);
    }

    @Override
    protected Object doSuspend(Object transaction) {
        String currentTxName = TransactionSynchronizationManager.getCurrentTransactionName();
        Object resource = super.doSuspend(transaction);
        suspendedTransactionName.set(currentTxName);
        return resource;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        super.doResume(transaction, suspendedResources);
        suspendedTransactionName.remove();
    }
}
