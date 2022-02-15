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

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Brian Koehmstedt
 */
public class CrossTransactionActionRecorder implements ActionRecorder {

    private static ActionHolderSingleton actionHolder = new ActionHolderSingleton();

    @Override
    public void addAction(Action action) {
        actionHolder.addAction(action);
    }

    @Override
    public List<Action> getActions() {
        return actionHolder.getActions();
    }

    @Override
    public Integer getSuspendedCount() {
        return actionHolder.getSuspendedCount();
    }

    @Override
    public void clear() {
        actionHolder.clear();
    }

    @Override
    public void recordCompletion(String completionString) {
        actionHolder.recordCompletion(completionString);
    }

    @Override
    public String getLastCompletionString() {
        return actionHolder.getLastCompletionString();
    }
    
    public static String getStaticLastCompletionString() {
        return actionHolder.getLastCompletionString();
    }

    protected static class ActionHolderSingleton {
        private ThreadLocal<List<Action>> actions = ThreadLocal.withInitial(new Supplier<List<Action>>() {
            @Override
            public List<Action> get() {
                return new LinkedList<Action>();
            }
        });
        private ThreadLocal<Integer> suspendedCount = ThreadLocal.withInitial(new Supplier<Integer>() {
            @Override
            public Integer get() {
                return 0;
            }
        });
        private ThreadLocal<String> lastCompletionString = new ThreadLocal<String>();

        public void addAction(Action action) {
            actions.get().add(action);
            if (action.getAction().equals(AbstractAction.ACTION_SUSPEND)) {
                suspendedCount.set(suspendedCount.get() + 1);
            } else if (action.getAction().equals(AbstractAction.ACTION_RESUME)) {
                suspendedCount.set(suspendedCount.get() - 1);
            }
        }

        public List<Action> getActions() {
            return actions.get();
        }

        public Integer getSuspendedCount() {
            return suspendedCount.get();
        }

        public void clear() {
            actions.remove();
            suspendedCount.remove();
        }

        public void recordCompletion(String completionString) {
            lastCompletionString.set(completionString);
        }

        public String getLastCompletionString() {
            return lastCompletionString.get();
        }
    }
}
