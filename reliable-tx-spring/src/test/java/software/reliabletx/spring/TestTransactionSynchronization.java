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

import org.springframework.transaction.support.TransactionSynchronization;

/**
 * @author Brian Koehmstedt
 */
public class TestTransactionSynchronization implements TransactionSynchronization {
    int suspendCount;
    int resumeCount;
    int beforeCommitCount;
    int beforeCompletionCount;
    int afterCommitCount;
    int afterCompletionCount;

    @Override
    public void suspend() {
        suspendCount++;
    }

    @Override
    public void resume() {
        resumeCount++;
    }

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void beforeCommit(boolean readOnly) {
        beforeCommitCount++;
    }

    @Override
    public void beforeCompletion() {
        beforeCompletionCount++;
    }

    @Override
    public void afterCommit() {
        afterCommitCount++;
    }

    @Override
    public void afterCompletion(int status) {
        afterCompletionCount++;
    }

    public boolean wasRolledBack() {
        return beforeCompletionCount == 1 && beforeCommitCount == 0 && afterCommitCount == 0
                && beforeCompletionCount == 1 && afterCompletionCount == 1;
    }

    public boolean wasCommitted() {
        return beforeCompletionCount == 1 && beforeCommitCount == 1 && afterCommitCount == 1
                && beforeCompletionCount == 1 && afterCompletionCount == 1;
    }
}
