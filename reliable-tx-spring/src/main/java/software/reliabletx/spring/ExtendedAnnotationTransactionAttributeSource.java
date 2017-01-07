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

import java.lang.reflect.AnnotatedElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.SpringTransactionAnnotationParser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Extend Spring's {@link AnnotationTransactionAttributeSource} to add
 * support for processing the {@link TransactionName} annotation.
 * 
 * @author Brian Koehmstedt
 */
public class ExtendedAnnotationTransactionAttributeSource extends AnnotationTransactionAttributeSource {

    private static final long serialVersionUID = -2286102829471698373L;

    public ExtendedAnnotationTransactionAttributeSource() {
        super(new ExtendedSpringTransactionAnnotationParser());
    }

    /**
     * @author Brian Koehmstedt
     */
    protected static class ExtendedSpringTransactionAnnotationParser extends SpringTransactionAnnotationParser {

        private static final long serialVersionUID = 5958837260277006521L;
        private final Logger log = LoggerFactory.getLogger(ExtendedSpringTransactionAnnotationParser.class);

        public static final String ATTR_NAME = "name";
        public static final String ATTR_SUSPEND_ONLY = "suspendOnly";

        public ExtendedSpringTransactionAnnotationParser() {
            super();
        }

        @Override
        public TransactionAttribute parseTransactionAnnotation(AnnotatedElement ae) {
            AnnotationAttributes transactionalAttributes = AnnotatedElementUtils.getMergedAnnotationAttributes(ae,
                    Transactional.class);
            if (transactionalAttributes != null) {
                AnnotationAttributes transactionNameAttributes = AnnotatedElementUtils.getMergedAnnotationAttributes(ae,
                        TransactionName.class);
                if (transactionNameAttributes != null) {
                    addTransactionalAttribute(transactionalAttributes, transactionNameAttributes, ATTR_NAME, true);
                    addTransactionalAttribute(transactionalAttributes, transactionNameAttributes, ATTR_SUSPEND_ONLY,
                            false);
                }
                return parseTransactionAnnotation(transactionalAttributes);
            } else {
                return null;
            }
        }

        @Override
        protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
            ExtendedRuleBasedTransactionAttribute txAttr = new ExtendedRuleBasedTransactionAttribute(
                    (RuleBasedTransactionAttribute) super.parseTransactionAnnotation(attributes));
            if (attributes.containsKey(ATTR_NAME) && attributes.getString(ATTR_NAME).trim().length() > 0) {
                txAttr.setName(attributes.getString(ATTR_NAME));
            }
            if (attributes.containsKey(ATTR_SUSPEND_ONLY)
                    && attributes.getString(ATTR_SUSPEND_ONLY).trim().length() > 0) {
                txAttr.setSuspendOnly(attributes.getString(ATTR_SUSPEND_ONLY));
            }
            return txAttr;
        }

        @Override
        public boolean equals(Object other) {
            return (this == other || other instanceof ExtendedSpringTransactionAnnotationParser);
        }

        @Override
        public int hashCode() {
            return ExtendedSpringTransactionAnnotationParser.class.hashCode();
        }

        protected void addTransactionalAttribute(AnnotationAttributes transactionalAttributes,
                AnnotationAttributes transactionNameAttributes, String attrName, boolean isRequired) {
            if (isRequired && (!transactionNameAttributes.containsKey(attrName)
                    || transactionNameAttributes.getString(attrName).trim().length() == 0)) {
                throw new IllegalArgumentException(
                        "Attribute '" + attrName + "' is required and cannot be missing or be an empty string.");

            }
            if (transactionNameAttributes.containsKey(attrName)) {
                String attrValue = transactionNameAttributes.getString(attrName);
                if (transactionalAttributes.containsKey(attrName)
                        && !transactionalAttributes.getString(attrName).equals(attrValue)) {
                    log.warn("Unexpected condition where @Transactional annotation attributes already contain a '"
                            + attrName + "' attribute: " + transactionalAttributes.getString(attrName));
                }
                transactionalAttributes.putIfAbsent(attrName, attrValue);
            }
        }
    }
}
