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
package software.reliabletx.spring.synchronization.tracking;

/**
 * @author Brian Koehmstedt
 */
public abstract class AbstractAction implements Action {
    public static final String ACTION_BEGUN = "begun";
    public static final String ACTION_SUSPEND = "suspend";
    public static final String ACTION_RESUME = "resume";
    public static final String ACTION_COMMIT = "commit";
    public static final String ACTION_ROLLBACK = "rollback";
    public static final String ACTION_COMPLETION_UNKNOWN_STATUS = "completionUnknownStatus";
    public static final String ACTION_OUTER_COMPLETION = "outerCompletion";

    private final String action;
    private final String transactionName;

    public AbstractAction(final String transactionName, final String action) {
        this.transactionName = transactionName;
        this.action = action;
    }

    @Override
    public String getAction() {
        return action;
    }

    @Override
    public String getTransactionName() {
        return transactionName;
    }

    @Override
    public String toString() {
        return "{\"action\":\"" + getAction() + "\",\"txName\":\"" + getTransactionName() + "\"}";
    }
}
