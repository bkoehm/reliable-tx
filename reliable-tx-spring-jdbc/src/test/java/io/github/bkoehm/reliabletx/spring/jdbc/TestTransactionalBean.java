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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import io.github.bkoehm.reliabletx.spring.TransactionName;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Brian Koehmstedt
 */
public class TestTransactionalBean implements ApplicationContextAware {
    private AbstractPlatformTransactionManager transactionManager;
    private DataSource dataSource;

    @Transactional
    @TransactionName(name = "myTransaction")
    public void testTransaction(boolean throwCheckedException, TransactionSynchronization synchronization)
            throws Exception {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        if (synchronization != null) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
        Connection conn = getCurrentConnection(dataSource);
        insertKey(conn, 1);

        if (throwCheckedException) {
            throw new Exception("purposely thrown exception");
        }
    }

    @Transactional
    @TransactionName(name = "myTransaction")
    public void runTransactionWithCorrectName(final TransactionSynchronization synchronization, Runnable runnable)
            throws Exception {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        if (synchronization != null) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
        runnable.run();
    }

    @Transactional
    @TransactionName(name = "incorrectTransactionName")
    public void runTransactionWithIncorrectName(final TransactionSynchronization synchronization, Runnable runnable)
            throws Exception {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        if (synchronization != null) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
        runnable.run();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionName(name = "suspender", suspendOnly = "suspendee")
    public void suspenderTransaction(TransactionSynchronization synchronization) throws Exception {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        if (synchronization != null) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
        Connection conn = getCurrentConnection(dataSource);
        insertKey(conn, 1);
    }

    @Transactional
    @TransactionName(name = "suspendee")
    public void suspendeeTransaction(TransactionSynchronization synchronization, Runnable runnable) throws Exception {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        if (synchronization != null) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }

        // should call suspenderTransaction() with null synchronization,
        // which has REQUIRES_NEW and a suspendOnly of "suspendee" which
        // matches this method's transaction name
        runnable.run();
    }

    @Transactional
    @TransactionName(name = "incorrectSuspendee")
    public void suspendeeTransactionWithIncorrectName(TransactionSynchronization synchronization, Runnable runnable)
            throws Exception {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        if (synchronization != null) {
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }

        // should call suspenderTransaction() with null synchronization,
        // which has REQUIRES_NEW and a suspendOnly of "suspendee" which
        // doesn't match this method's transaction name, so should throw an
        // exception
        runnable.run();
    }

    public static void createTable(Connection conn) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("CREATE TABLE Test (key INTEGER)");
        try {
            ps.executeUpdate();
        } finally {
            ps.close();
        }
    }

    public static void insertKey(Connection conn, Integer key) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT INTO Test VALUES (?)");
        try {
            ps.setInt(1, key);
            assertTrue(ps.executeUpdate() == 1);
        } finally {
            ps.close();
        }
    }

    public static Connection getCurrentConnection(DataSource dataSource) {
        return ((ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource)).getConnection();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.dataSource = applicationContext.getBean("dataSource", DataSource.class);
        this.transactionManager = applicationContext.getBean("transactionManager",
                AbstractPlatformTransactionManager.class);
        assertNotNull(dataSource);
        assertNotNull(transactionManager);
    }

    public static int getRowCount(DataSource dataSource) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("SELECT count(*) FROM Test");
            try {
                ResultSet rs = ps.executeQuery();
                try {
                    rs.next();
                    return rs.getInt(1);
                } finally {
                    rs.close();
                }
            } finally {
                ps.close();
            }
        } finally {
            connection.close();
        }
    }

    public void init() throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            createTable(connection);
        } finally {
            connection.close();
        }
    }

    public void cleanUp() throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            PreparedStatement ps = connection.prepareStatement("DROP TABLE IF EXISTS Test");
            try {
                ps.executeUpdate();
            } finally {
                ps.close();
            }
        } finally {
            connection.close();
        }
    }
}
