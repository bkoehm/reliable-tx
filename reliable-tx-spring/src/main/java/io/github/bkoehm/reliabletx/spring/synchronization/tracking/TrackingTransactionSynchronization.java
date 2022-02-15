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
package io.github.bkoehm.reliabletx.spring.synchronization.tracking;

import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author Brian Koehmstedt
 */
public class TrackingTransactionSynchronization implements TransactionSynchronization {

    private ActionRecorder actionRecorder = new CrossTransactionActionRecorder();
    private final DefaultTransactionStatus status;
    private boolean outerTransactionCompleted;
    private String afterOuterTransactionCompletionActivityHistory;

    public TrackingTransactionSynchronization(final DefaultTransactionStatus status) {
        this.status = status;
    }

    public DefaultTransactionStatus getTransactionStatus() {
        return status;
    }

    private String getCurrentTransactionName() {
        return TransactionSynchronizationManager.getCurrentTransactionName();
    }

    public void synchronizationPrepared() {
        actionRecorder.addAction(begunAction(getCurrentTransactionName()));
    }

    @Override
    public void suspend() {
        actionRecorder.addAction(suspendAction(getCurrentTransactionName()));
    }

    @Override
    public void resume() {
        actionRecorder.addAction(resumeAction(getCurrentTransactionName()));
    }

    @Override
    public void flush() {
    }

    @Override
    public void beforeCommit(boolean readOnly) {
    }

    @Override
    public void beforeCompletion() {
    }

    @Override
    public void afterCommit() {
    }

    @Override
    public void afterCompletion(int status) {
        switch (status) {
            case STATUS_COMMITTED:
                actionRecorder.addAction(commitAction(getCurrentTransactionName()));
                break;
            case STATUS_ROLLED_BACK:
                actionRecorder.addAction(rollbackAction(getCurrentTransactionName()));
                break;
            case STATUS_UNKNOWN:
                actionRecorder.addAction(completionUnknownStatusAction(getCurrentTransactionName()));
                break;
            default:
                throw new IllegalStateException("Unknown completion status: " + status);
        }
        if (actionRecorder.getSuspendedCount() == 0) {
            // outer transaction is completing
            setOuterTransactionCompleted();
        }
    }

    @Override
    public String toString() {
        Action[] actionArray = new Action[actionRecorder.getActions().size()];
        return txActivityString(actionRecorder.getActions().toArray(actionArray));
    }

    public static String txActivityString(Action... actions) {
        StringBuffer buf = new StringBuffer("[");
        for (int i = 0; i < actions.length; i++) {
            buf.append(actions[i].toString());
            if (i + 1 < actions.length) {
                buf.append(", ");
            }
        }
        buf.append("]");
        return buf.toString();
    }

    public boolean isOuterTransactionCompleted() {
        return outerTransactionCompleted;
    }

    public void setOuterTransactionCompleted() {
        this.afterOuterTransactionCompletionActivityHistory = toString();
        actionRecorder.clear();
        this.outerTransactionCompleted = true;
        actionRecorder.recordCompletion(afterOuterTransactionCompletionActivityHistory);
    }

    public String getAfterOuterTransactionCompletionActivityHistory() {
        return afterOuterTransactionCompletionActivityHistory;
    }

    public static Action begunAction(String txName) {
        return new Begun(txName);
    }

    public static Action suspendAction(String txName) {
        return new Suspend(txName);
    }

    public static Action resumeAction(String txName) {
        return new Resume(txName);
    }

    public static CompletionAction commitAction(String txName) {
        return new Commit(txName);
    }

    public static CompletionAction rollbackAction(String txName) {
        return new Rollback(txName);
    }

    public static CompletionAction completionUnknownStatusAction(String txName) {
        return new CompletionUnknownStatus(txName);
    }
}
