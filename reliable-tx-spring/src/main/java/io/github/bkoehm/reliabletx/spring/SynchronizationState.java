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

/**
 * An enum used to describe the current state of the transaction as processed
 * by a synchronization callback added to the transaction. Only relevant for
 * PlatformTransactionManagers that use synchronization.
 * 
 * @author Brian Koehmstedt
 * @see SpringTransactionSynchronization
 */
public enum SynchronizationState {
    /**
     * Transaction manager does not support synchronization.
     */
    NOT_SUPPORTED,

    /**
     * Synchronization has not been initialized (added to a transaction) yet.
     */
    UNINITIALIZED,

    /**
     * Transaction is active.
     */
    ACTIVE,

    /**
     * Transaction is suspended.
     */
    SUSPENDED,

    /**
     * Transaction is completed and committed.
     */
    COMMITTED,

    /**
     * Transaction is completed and rolled back.
     */
    ROLLED_BACK,

    /**
     * Transaction is completed but whether it was a success or a failure is
     * unknown.
     */
    COMPLETED_BUT_UNKNOWN
}
