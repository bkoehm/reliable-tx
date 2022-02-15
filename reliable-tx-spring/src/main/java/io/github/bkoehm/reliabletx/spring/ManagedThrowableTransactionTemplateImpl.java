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
package io.github.bkoehm.reliabletx.spring;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

import io.github.bkoehm.reliabletx.spring.ManagedTransactionTemplateUtil.TransactionCallbackHolder;

/**
 * @author Brian Koehmstedt
 */
public class ManagedThrowableTransactionTemplateImpl extends RuleBasedTransactionAttribute
        implements ManagedThrowableTransactionTemplate {

    private static final long serialVersionUID = -1839072360950773796L;

    private PlatformTransactionManager transactionManager;

    public ManagedThrowableTransactionTemplateImpl() {
        setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ManagedThrowableTransactionTemplateImpl(PlatformTransactionManager transactionManager) {
        this();
        setTransactionManager(transactionManager);
    }

    public ManagedThrowableTransactionTemplateImpl(PlatformTransactionManager transactionManager,
            TransactionDefinition transactionDefinition) throws IllegalArgumentException {
        setTransactionManager(transactionManager);
        initFromTransactionDefinition(transactionDefinition);
    }

    public ManagedThrowableTransactionTemplateImpl(PlatformTransactionManager transactionManager,
            TransactionAttribute transactionAttribute) throws IllegalArgumentException {
        setTransactionManager(transactionManager);
        initFromTransactionAttribute(transactionAttribute);
    }

    private void initFromTransactionDefinition(TransactionDefinition def) throws IllegalArgumentException {
        ManagedTransactionTemplateUtil.checkPropagationBehavior(def);
        setIsolationLevel(def.getIsolationLevel());
        setName(def.getName());
        setPropagationBehavior(def.getPropagationBehavior());
        setTimeout(def.getTimeout());
        setReadOnly(def.isReadOnly());
    }

    private void initFromTransactionAttribute(TransactionAttribute attr) throws IllegalArgumentException {
        initFromTransactionDefinition(attr);
        setQualifier(attr.getQualifier());
        if (attr instanceof RuleBasedTransactionAttribute) {
            setRollbackRules(((RuleBasedTransactionAttribute) attr).getRollbackRules());
        }
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public <T> T execute(ThrowableTransactionCallback<T> action) throws Throwable {
        return ManagedTransactionTemplateUtil.execute(getTransactionManager(), this,
                new TransactionCallbackHolder<T>(action, this));
    }
}
