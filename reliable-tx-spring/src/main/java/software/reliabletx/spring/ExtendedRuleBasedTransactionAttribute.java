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

import java.util.List;

import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;

/**
 * Extend Spring's {@link RuleBasedTransactionAttribute} to support
 * additional attributes from additional annotations.
 * 
 * @author Brian Koehmstedt
 */
public class ExtendedRuleBasedTransactionAttribute extends RuleBasedTransactionAttribute {

    private static final long serialVersionUID = 785531552436193644L;

    /**
     * If the propagation behavior supports suspending a current transaction,
     * only suspend for current transactions with this name.
     */
    private String suspendOnly;

    public ExtendedRuleBasedTransactionAttribute() {
    }

    public ExtendedRuleBasedTransactionAttribute(RuleBasedTransactionAttribute other) {
        super(other);
    }

    public ExtendedRuleBasedTransactionAttribute(int propagationBehavior, List<RollbackRuleAttribute> rollbackRules) {
        super(propagationBehavior, rollbackRules);
    }

    /**
     * @return If the propagation behavior supports suspending a current
     *         transaction, only suspend for current transactions with this
     *         name.
     */
    public String getSuspendOnly() {
        return suspendOnly;
    }

    /**
     * @param suspendOnly
     *            If the propagation behavior supports suspending a current
     *            transaction, only suspend for current transactions with
     *            this name.
     */
    public void setSuspendOnly(String suspendOnly) {
        this.suspendOnly = suspendOnly;
    }
}
