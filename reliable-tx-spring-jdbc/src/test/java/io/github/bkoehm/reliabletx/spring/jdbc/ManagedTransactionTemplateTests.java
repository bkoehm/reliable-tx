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

import io.github.bkoehm.reliabletx.spring.ManagedThrowableTransactionTemplateImpl;
import io.github.bkoehm.reliabletx.spring.ManagedTransactionTemplateImpl;
import io.github.bkoehm.reliabletx.spring.ThrowableTransactionCallback;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Brian Koehmstedt
 */
public class ManagedTransactionTemplateTests extends SpringTestCase {
    @Test
    public void testTemplateWithUncheckedCallback() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();
        assertNoTransaction(dataSource);

        ManagedTransactionTemplateImpl template = new ManagedTransactionTemplateImpl(transactionManager);
        boolean result = template.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                TransactionSynchronizationManager.registerSynchronization(synchronization);
                Connection conn = TestTransactionalBean.getCurrentConnection(dataSource);
                try {
                    TestTransactionalBean.insertKey(conn, 1);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                return true;
            }
        });
        assertTrue(result);

        // We expect tx completion.
        assertNoTransaction(dataSource);
        // We expect 1 row.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 1);
        // We expect the synchronization callback to have recorded a
        // commit.
        assertTrue(synchronization.wasCommitted());
    }

    @Test
    public void testTemplateSetRollbackOnly() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();
        assertNoTransaction(dataSource);

        ManagedTransactionTemplateImpl template = new ManagedTransactionTemplateImpl(transactionManager);
        boolean result = template.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                TransactionSynchronizationManager.registerSynchronization(synchronization);
                Connection conn = TestTransactionalBean.getCurrentConnection(dataSource);
                try {
                    TestTransactionalBean.insertKey(conn, 1);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                status.setRollbackOnly();
                return true;
            }
        });
        assertTrue(result);

        // We expect tx completion.
        assertNoTransaction(dataSource);
        // We expect no rows due to rollback.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 0);
        // We expect the synchronization callback to have recorded a
        // rollback.
        assertTrue(synchronization.wasRolledBack());
    }

    @Test
    public void testTemplateWithUncheckedCallbackAndExceptionThrown() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();
        assertNoTransaction(dataSource);

        ManagedTransactionTemplateImpl template = new ManagedTransactionTemplateImpl(transactionManager);
        RuntimeException e = null;
        try {
            template.execute(new TransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    TransactionSynchronizationManager.registerSynchronization(synchronization);
                    Connection conn = TestTransactionalBean.getCurrentConnection(dataSource);
                    try {
                        TestTransactionalBean.insertKey(conn, 1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException("purposely thrown exception");
                }
            });
        } catch (RuntimeException _e) {
            e = _e;
        }

        // We expect our callback exception to be propagated.
        assertNotNull(e);
        assertTrue(e.getMessage().equals("purposely thrown exception"));

        // We expect tx completion.
        assertNoTransaction(dataSource);
        // We expect 0 rows due to rollback.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 0);
        // We expect the synchronization callback to have recorded a
        // rollback.
        assertTrue(synchronization.wasRolledBack());
    }

    @Test
    public void testTemplateWithCheckedCallbackAndUncheckedExceptionThrown() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();
        assertNoTransaction(dataSource);

        ManagedThrowableTransactionTemplateImpl template = new ManagedThrowableTransactionTemplateImpl(
                transactionManager);
        Throwable t = null;
        try {
            template.execute(new ThrowableTransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    TransactionSynchronizationManager.registerSynchronization(synchronization);
                    Connection conn = TestTransactionalBean.getCurrentConnection(dataSource);
                    try {
                        TestTransactionalBean.insertKey(conn, 1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException("purposely thrown exception");
                }
            });
        } catch (Throwable _t) {
            t = _t;
        }

        // We expect our callback exception to be propagated.
        assertNotNull(t);
        assertTrue(t.getMessage().equals("purposely thrown exception"));

        // We expect tx completion.
        assertNoTransaction(dataSource);
        // We expect 0 rows due to rollback.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 0);
        // We expect the synchronization callback to have recorded a
        // rollback.
        assertTrue(synchronization.wasRolledBack());
    }

    @Test
    public void testTemplateWithCheckedCallbackAndRollbackForExceptionThrown() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();
        assertNoTransaction(dataSource);

        List<RollbackRuleAttribute> rollbackRules = new ArrayList<RollbackRuleAttribute>();
        rollbackRules.add(new RollbackRuleAttribute(Exception.class));
        ManagedThrowableTransactionTemplateImpl template = new ManagedThrowableTransactionTemplateImpl(
                transactionManager,
                new RuleBasedTransactionAttribute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, rollbackRules));
        Throwable t = null;
        try {
            template.execute(new ThrowableTransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) throws Exception {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    TransactionSynchronizationManager.registerSynchronization(synchronization);
                    Connection conn = TestTransactionalBean.getCurrentConnection(dataSource);
                    try {
                        TestTransactionalBean.insertKey(conn, 1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    throw new Exception("purposely thrown exception");
                }
            });
        } catch (Throwable _t) {
            t = _t;
        }

        // We expect our callback exception to be propagated.
        assertNotNull(t);
        assertTrue(t.getMessage().equals("purposely thrown exception"));

        // We expect tx completion.
        assertNoTransaction(dataSource);
        // We expect 0 rows due to rollback because
        // rollbackOn(Exception.class) returns true.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 0);
        // We expect the synchronization callback to have recorded a
        // rollback.
        assertTrue(synchronization.wasRolledBack());
    }

    @Test
    public void testTemplateWithCheckedCallbackAndNotRollbackForExceptionThrown() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();
        assertNoTransaction(dataSource);

        ManagedThrowableTransactionTemplateImpl template = new ManagedThrowableTransactionTemplateImpl(
                transactionManager);
        Throwable t = null;
        try {
            template.execute(new ThrowableTransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) throws Exception {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    TransactionSynchronizationManager.registerSynchronization(synchronization);
                    Connection conn = TestTransactionalBean.getCurrentConnection(dataSource);
                    try {
                        TestTransactionalBean.insertKey(conn, 1);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                    throw new Exception("purposely thrown exception");
                }
            });
        } catch (Throwable _t) {
            t = _t;
        }

        // We expect our callback exception to be propagated.
        assertNotNull(t);
        assertTrue(t.getMessage().equals("purposely thrown exception"));

        // We expect tx completion.
        assertNoTransaction(dataSource);
        // We expect 1 row due to commit because rollbackOn(Exception.class)
        // returns false.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 1);
        // We expect the synchronization callback to have recorded a
        // commit.
        assertTrue(synchronization.wasCommitted());
    }
}
