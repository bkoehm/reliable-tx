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

import java.util.LinkedList;
import java.util.List;

/**
 * @author Brian Koehmstedt
 */
public class SimpleSameTransactionActionRecorder implements ActionRecorder {
    private LinkedList<Action> actions = new LinkedList<Action>();
    private Integer suspendedCount = 0;
    private String lastCompletionString;

    @Override
    public void addAction(Action action) {
        actions.add(action);

        if (action.getAction().equals(AbstractAction.ACTION_SUSPEND)) {
            suspendedCount++;
        } else if (action.getAction().equals(AbstractAction.ACTION_RESUME)) {
            suspendedCount--;
        }
    }

    @Override
    public List<Action> getActions() {
        return actions;
    }

    @Override
    public Integer getSuspendedCount() {
        return suspendedCount;
    }

    @Override
    public void clear() {
        actions.clear();
        suspendedCount = 0;
    }
    
    @Override
    public void recordCompletion(String completionString) {
        this.lastCompletionString = completionString;
    }
    
    @Override
    public String getLastCompletionString() {
        return lastCompletionString;
    }
}
