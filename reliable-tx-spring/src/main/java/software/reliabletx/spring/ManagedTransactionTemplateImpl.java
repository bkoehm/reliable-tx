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

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionCallback;

import software.reliabletx.spring.ManagedTransactionTemplateUtil.TransactionCallbackHolder;

/**
 * @author Brian Koehmstedt
 */
public class ManagedTransactionTemplateImpl extends DefaultTransactionAttribute implements ManagedTransactionTemplate {

    private static final long serialVersionUID = -1043369014484560217L;

    private PlatformTransactionManager transactionManager;

    public ManagedTransactionTemplateImpl() {
        setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ManagedTransactionTemplateImpl(PlatformTransactionManager transactionManager) {
        this();
        setTransactionManager(transactionManager);
    }

    public ManagedTransactionTemplateImpl(PlatformTransactionManager transactionManager,
            TransactionDefinition transactionDefinition) throws IllegalArgumentException {
        setTransactionManager(transactionManager);
        initFromTransactionDefinition(transactionDefinition);
    }

    public ManagedTransactionTemplateImpl(PlatformTransactionManager transactionManager,
            TransactionAttribute transactionAttribute) throws IllegalArgumentException {
        setTransactionManager(transactionManager);
        initFromTransactionAttribute(transactionAttribute);
    }

    private void initFromTransactionAttribute(TransactionAttribute attr) throws IllegalArgumentException {
        if (attr instanceof RuleBasedTransactionAttribute) {
            throw new IllegalArgumentException(
                    "RuleBasedTransactionAttribute is not supported with this class.  Use ManagedTransactionTemplateImpl instead.");
        }
        initFromTransactionDefinition(attr);
        setQualifier(attr.getQualifier());
    }

    private void initFromTransactionDefinition(TransactionDefinition def) throws IllegalArgumentException {
        ManagedTransactionTemplateUtil.checkPropagationBehavior(def);
        setIsolationLevel(def.getIsolationLevel());
        setName(def.getName());
        setPropagationBehavior(def.getPropagationBehavior());
        setTimeout(def.getTimeout());
        setReadOnly(def.isReadOnly());
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
    public <T> T execute(TransactionCallback<T> action) {
        try {
            return ManagedTransactionTemplateUtil.execute(getTransactionManager(), this,
                    new TransactionCallbackHolder<T>(action, this));
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            // shouldn't ever happen because action.doInTransaction() can't
            // throw anything but a RuntimeException
            throw new IllegalStateException(t);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (this.transactionManager == null) {
            throw new IllegalArgumentException("Property 'transactionManager' is required");
        }
    }
}
