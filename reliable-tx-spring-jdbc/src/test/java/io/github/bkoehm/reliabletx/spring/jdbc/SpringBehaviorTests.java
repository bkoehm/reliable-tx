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

import io.github.bkoehm.reliabletx.spring.synchronization.tracking.CrossTransactionActionRecorder;
import io.github.bkoehm.reliabletx.spring.synchronization.tracking.TrackingTransactionSynchronization;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Brian Koehmstedt
 */
public class SpringBehaviorTests extends SpringTestCase {

    static class TrackingSynchronizationHolder {
        TrackingTransactionSynchronization tracker;
    }

    @Test
    public void testTransactionalAnnotationCommittingBehavior() throws Exception {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();

        // We start fresh.
        assertNoTransaction(dataSource);

        /**
         * Call the method with the correct transaction name. See
         * callRealTestTransaction() comments for why we do it this way.
         */
        testBean.runTransactionWithCorrectName(synchronization, new Runnable() {
            @Override
            public void run() {
                try {
                    callRealTestTransaction(false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // No transaction should be active since testTransaction() should
        // have committed since that method uses Spring's AOP @Transactional
        // and we started with no transaction upon testTransaction()'s
        // entry.
        assertNoTransaction(dataSource);

        // We expect one row to be inserted, confirming that the testBean
        // transaction ran successfully.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 1);

        // We expect the synchronization callback to have recorded a commit.
        assertTrue(synchronization.wasCommitted());

        assertEquals(TrackingTransactionSynchronization.txActivityString(TrackingTransactionSynchronization.begunAction("myTransaction"), TrackingTransactionSynchronization.commitAction("myTransaction")),
                getLastCompletionString());
    }

    /**
     * I haven't seen obvious documentation on what Spring's @Transactional
     * is supposed to do when checked exceptions are thrown that aren't in
     * the annotation's rollbackFor parameter.
     *
     * <p>
     * This test confirms that Spring will perform a commit on checked
     * exceptions that aren't listed in rollbackFor.
     * </p>
     */
    @Test
    public void testTransactionalAnnotationCheckedExceptionBehavior() throws Exception {
        TestTransactionSynchronization synchronization = getStandardSynchronization();

        // We start fresh.
        assertNoTransaction(dataSource);

        Exception exception = null;
        try {
            testBean.testTransaction(true, synchronization);
        } catch (Exception e) {
            exception = e;
        }

        assertNotNull(exception);
        assertEquals(exception.getMessage(), "purposely thrown exception");

        // We expect tx completion.
        assertNoTransaction(dataSource);

        // We expect one row to be inserted, confirming that the testBean
        // transaction is committed despite the checked exception.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 1);

        // We expect the synchronization callback to have recorded a commit
        // despite the checked exception.
        assertTrue(synchronization.wasCommitted());

        assertEquals(TrackingTransactionSynchronization.txActivityString(TrackingTransactionSynchronization.begunAction("myTransaction"), TrackingTransactionSynchronization.commitAction("myTransaction")),
                getLastCompletionString());
    }

    /**
     * Confirm that TransactionTemplate does not throw an exception when the
     * callback marks the transaction as rollbackOnly.
     */
    @Test
    public void testTransactionTemplateCallbackSettingStatusAsRollbackOnly() throws SQLException {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();

        // We start fresh.
        assertNoTransaction(dataSource);

        TransactionTemplate template = new TransactionTemplate(transactionManager,
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        template.setName("txTemplate");
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

        assertEquals(TrackingTransactionSynchronization.txActivityString(TrackingTransactionSynchronization.begunAction("txTemplate"), TrackingTransactionSynchronization.rollbackAction("txTemplate")),
                getLastCompletionString());
    }

    @Test
    public void testTransactionalAnnotationWithIncorrectName() throws Exception {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();

        // We start fresh.
        assertNoTransaction(dataSource);

        Exception exception = null;
        try {
            /**
             * Call the method with the incorrect transaction name. See
             * callRealTestTransaction() comments for why we do it this way.
             */
            testBean.runTransactionWithIncorrectName(synchronization, new Runnable() {
                @Override
                public void run() {
                    try {
                        callRealTestTransaction(false);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (Exception e) {
            exception = e;
        }

        assertNotNull(exception);
        final String expectedExceptionMessage = "org.springframework.transaction.TransactionUsageException: Specified propagation behavior supports an existing transaction but the existing transaction name of 'incorrectTransactionName' does not match the specified transaction name of 'myTransaction'";
        assertEquals(exception.getMessage(), expectedExceptionMessage);

        // We expect tx completion.
        assertNoTransaction(dataSource);

        // We expect no rows due to rollback.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 0);

        // We expect the synchronization callback to have recorded a
        // rollback.
        assertTrue(synchronization.wasRolledBack());

        assertEquals(
                TrackingTransactionSynchronization.txActivityString(TrackingTransactionSynchronization.begunAction("incorrectTransactionName"), TrackingTransactionSynchronization.rollbackAction("incorrectTransactionName")),
                getLastCompletionString());
    }

    /**
     * We use a callback approach to calling testTransaction() from this
     * class because Spring won't invoke the TransactionInterceptor for the
     * second method call if one transactional method in the same class calls
     * another.
     */
    final void callRealTestTransaction(boolean throwCheckedException) throws Exception {
        testBean.testTransaction(throwCheckedException, null);
    }

    @Test
    public void testTransactionalAnnotationSuspendingNameCheckBehavior() throws Exception {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();

        // We start fresh.
        assertNoTransaction(dataSource);

        /**
         * Call a suspendee method that will start a transaction that will be
         * suspended by a call to another method with REQUIRES_NEW. See
         * callRealSuspendeeTransaction() comments for why we do it this way.
         */
        assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
        testBean.suspendeeTransaction(synchronization, new Runnable() {
            @Override
            public void run() {
                assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
                try {
                    callRealSuspenderTransaction();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // "suspendee" was resumed, but that was completed too, so there
        // should be no transaction active.
        assertNoTransaction(dataSource);

        // We expect one row to be inserted, confirming that the "suspender"
        // transaction ran successfully.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 1);

        // We expect the synchronization callback to have recorded a commit.
        assertTrue(synchronization.wasCommitted());

        assertEquals(
                TrackingTransactionSynchronization.txActivityString(TrackingTransactionSynchronization.begunAction("suspendee"), TrackingTransactionSynchronization.suspendAction("suspendee"), TrackingTransactionSynchronization.begunAction("suspender"),
                        TrackingTransactionSynchronization.commitAction("suspender"), TrackingTransactionSynchronization.resumeAction("suspendee"), TrackingTransactionSynchronization.commitAction("suspendee")),
                getLastCompletionString());

    }

    @Test
    public void testTransactionalAnnotationSuspendingIncorrectNameCheckBehavior() throws Exception {
        final TestTransactionSynchronization synchronization = getStandardSynchronization();

        // We start fresh.
        assertNoTransaction(dataSource);

        /**
         * Call a suspendee method that will start a transaction that should
         * throw an exception because it makes a call to another method with
         * REQUIRES_NEW and a suspendOnly name that doesn't match
         * suspendeeTransactionWithIncorrectName's transaction name. See
         * callRealSuspendeeTransaction() comments for why we do it this way.
         */
        Exception exception = null;
        try {
            testBean.suspendeeTransactionWithIncorrectName(synchronization, new Runnable() {
                @Override
                public void run() {
                    try {
                        callRealSuspenderTransaction();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (Exception e) {
            exception = e;
        }

        assertNotNull(exception);
        final String expectedExceptionMessage = "org.springframework.transaction.TransactionUsageException: Specified propagation behavior supports suspending the current transaction but the current transaction name of 'incorrectSuspendee' does not match the specified 'suspendOnly' transaction name of 'suspendee'";
        assertEquals(exception.getMessage(), expectedExceptionMessage);

        assertNoTransaction(dataSource);

        // We expect no rows inserted.
        assertTrue(TestTransactionalBean.getRowCount(dataSource) == 0);

        // We expect the synchronization callback to have recorded a
        // rollback.
        assertTrue(synchronization.wasRolledBack());

        assertEquals(
                TrackingTransactionSynchronization.txActivityString(TrackingTransactionSynchronization.begunAction("incorrectSuspendee"), TrackingTransactionSynchronization.suspendAction("incorrectSuspendee"),
                        TrackingTransactionSynchronization.resumeAction("incorrectSuspendee"), TrackingTransactionSynchronization.rollbackAction("incorrectSuspendee")),
                getLastCompletionString());
    }

    /**
     * We use a callback approach to calling suspenderTransaction() from this
     * class because Spring won't invoke the TransactionInterceptor for the
     * second method call if one transactional method in the same class calls
     * another.
     */
    final void callRealSuspenderTransaction() throws Exception {
        testBean.suspenderTransaction(null);
    }

    protected String getLastCompletionString() {
        return CrossTransactionActionRecorder.getStaticLastCompletionString();
    }
}
