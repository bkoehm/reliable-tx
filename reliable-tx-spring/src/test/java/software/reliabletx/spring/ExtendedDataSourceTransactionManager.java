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

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * @author Brian Koehmstedt
 */
public class ExtendedDataSourceTransactionManager extends DataSourceTransactionManager {
    private static final long serialVersionUID = -7145730577106059685L;

    boolean dumpStack = false;

    int rollbackCount;
    int commitCount;
    int markRollbackOnlyCount;
    int suspendCount;

    public void resetStats() {
        rollbackCount = 0;
        commitCount = 0;
        markRollbackOnlyCount = 0;
        suspendCount = 0;
    }

    public boolean hasBeenRolledBack() {
        return rollbackCount > 0;
    }

    public boolean hasBeenCommitted() {
        return commitCount > 0;
    }

    public boolean hasBeenMarkedForRollbackOnly() {
        return markRollbackOnlyCount > 0;
    }

    public boolean hasBeenSuspended() {
        return suspendCount > 0;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        if (dumpStack) {
            Thread.dumpStack();
        }
        super.doRollback(status);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (dumpStack) {
            Thread.dumpStack();
        }
        super.doCommit(status);
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        if (dumpStack) {
            Thread.dumpStack();
        }
        super.doSetRollbackOnly(status);
    }

    @Override
    protected Object doSuspend(Object transaction) {
        if (dumpStack) {
            Thread.dumpStack();
        }
        return super.doSuspend(transaction);
    }
}
