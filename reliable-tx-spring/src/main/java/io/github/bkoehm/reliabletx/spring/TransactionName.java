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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Set the name of a transaction for a method or class that is also annotated
 * with {@link org.springframework.transaction.annotation.Transactional}.
 * Annotation at the class level means that all public methods of the class
 * are implicitly annotated. If this annotation is not supplied with a paired
 * {@code Transactional} annotation, then this annotation will have no
 * effect.
 * 
 * <p>
 * This annotation is meant to be used with an overridden bean definition for
 * the transaction manager and the
 * {@code AnnotationTransactionAttributeSource} bean. See
 * {@link EnhancedTransactionManagerUtil} and
 * {@link ExtendedAnnotationTransactionAttributeSource}.
 * </p>
 * 
 * <p>
 * <b>Important note:</b> When using this annotation with a transaction
 * manager that utilizes
 * {@link EnhancedTransactionManagerUtil#checkNewTransactionStatusForName(org.springframework.transaction.TransactionDefinition, String)}
 * , the check name method won't get called if a transactional method calls
 * another in the same class instance. So the transaction name check will
 * only happen between method calls across different class instances.
 * </p>
 * 
 * @see org.springframework.transaction.annotation.Transactional
 * @see EnhancedTransactionManagerUtil
 * @see ExtendedAnnotationTransactionAttributeSource
 * 
 * @author Brian Koehmstedt
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface TransactionName {
    /**
     * The name of the transaction. Required.
     */
    String name() default "";

    /**
     * If the propagation behavior supports suspending a current transaction,
     * only suspend for current transactions with this name. Optional.
     */
    String suspendOnly() default "";
}
