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
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.support.TransactionCallback;

/**
 * Execute a transaction using a callback. See the documentation for the
 * {@link #execute(PlatformTransactionManager, TransactionDefinition, TransactionCallbackHolder)}
 * method for details.
 * 
 * <p>
 * This class is not meant to be used by itself. Use
 * {@link ManagedTransactionTemplateImpl} or
 * {@link ManagedThrowableTransactionTemplateImpl} instead, which are
 * transaction templates that use this utility class. This class is public so
 * that other template implementations can be made.
 * </p>
 * 
 * @author Brian Koehmstedt
 */
public abstract class ManagedTransactionTemplateUtil {
    /**
     * The message for the exception that gets thrown when a callback marks a
     * transaction as rollbackOnly but the rollback fails.
     */
    public static final String ROLLBACKONLY_ROLLBACK_FAILURE_MESSAGE = "A rollback failed.  The rollback was attempted because the callback marked the transaction as rollbackOnly.";

    /**
     * The message for the exception that gets thrown when rollbackOn returns
     * true for an exception but the rollback fails.
     */
    public static final String ROLLBACKON_TRUE_ROLLBACK_FAILURE_MESSAGE = "A rollback failed.  The rollback was attempted because the callback threw an exception and rollbackOn() returned true for that exception.";

    /**
     * The message for the exception that gets thrown when rollbackOn returns
     * false for an exception and the commit fails and the subsequent
     * rollback fails too.
     */
    public static final String ROLLBACKON_FALSE_COMMIT_FAILURE_ROLLBACK_FAILURE_MESSAGE = "A rollback failed.  The rollback was attempted because the callback threw an exception and rollbackOn() returned false for that exception, so a commit() was attempted.  That commit() failed, causing the attempted rollback().";

    /**
     * The message for the exception that gets thrown when rollbackOn returns
     * false for an exception and the commit fails and the subsquent rollback
     * succeeds.
     */
    public static final String ROLLBACKON_FALSE_COMMIT_FAILURE_ROLLBACK_SUCCEEDED_MESSAGE = "The transaction was rolled back because the callback threw an exception and rollbackOn() returned false for that exception, so a commit() was attempted.  That commit() failed, causing the rollback.";

    /**
     * Class that holds a transaction callback for a template. There are two
     * kinds of callbacks: {@link TransactionCallback} that can only throw
     * runtime exceptions. {@link ThrowableTransactionCallback} that can
     * throw any kind of exception. The holder can only hold one type of
     * callback.
     * 
     * @param <T>
     *            The type of the return value of the callback.
     */
    public static final class TransactionCallbackHolder<T> {
        protected final TransactionCallback<T> notThrowable;
        protected final ThrowableTransactionCallback<T> throwable;
        protected final TransactionAttribute txAttr;

        public TransactionCallbackHolder(TransactionCallback<T> notThrowable, TransactionAttribute txAttr) {
            this.notThrowable = notThrowable;
            this.throwable = null;
            this.txAttr = txAttr;
        }

        public TransactionCallbackHolder(ThrowableTransactionCallback<T> throwable,
                RuleBasedTransactionAttribute txAttr) {
            this.notThrowable = null;
            this.throwable = throwable;
            this.txAttr = txAttr;
        }

        public boolean isThrowable() {
            return throwable != null;
        }
    }

    // @formatter:off
    /**
     * The basic contract of this method is that a new transaction is
     * started upon method entry and that transaction is guaranteed to be
     * completed on method exit, unless {@link
     * org.springframework.transaction.TransactionException} is thrown,
     * which would indicate an indeterminate completion state.  If a
     * transaction is already active within Spring's transaction manager,
     * that transaction will be suspended upon method entry and would be
     * resumed upon method exit (again, unless {@link
     * org.springframework.transaction.TransactionException} is thrown,
     * which would be indeterminate in regards to whether the previously
     * suspended transaction is resumed).  If no exceptions are thrown by
     * this method, the return value of the method is the same as the return
     * value of the callback.
     * 
     * <ul>
     *   <li>If holder.isThrowable() is false:
     *     <ul>
     *       <li>Use the holder's txAttr {@link
     *           org.springframework.transaction.interceptor.DefaultTransactionAttribute#rollbackOn(Throwable)},
     *           to determine if a rollback occurs on the exception. 
     *           DefaultTransactionAttribute's behavior is to indicate a
     *           rollback on any RuntimeException.</li>
     *     </ul>
     *   </li>
     *   <li>If holder.isThrowable() is true (meaning the callback can throw
     *       more than just RuntimeExceptions):
     *     <ul>
     *       <li>Use the
     *           holder's txAttr {@link
     *           RuleBasedTransactionAttribute#rollbackOn(Throwable)}, to
     *           determine if a rollback occurs on the exception.  If no
     *           rule applies to the exception,
     *           RuleBasedTransactionAttribute's behavior is to indicate a
     *           rollback if it's a RuntimeException <b>and to commit if
     *           it's a checked exception</b>.</li>
     *     </ul>
     *   </li>
     * </ul>
     * 
     * <p>
     * In all cases (except when a {@link
     * org.springframework.transaction.TransactionException} is thrown),
     * callback exceptions are propagated unmodified and unwrapped up to the
     * caller (regardless of whether the exception triggered a rollback). 
     * If holder.isThrowable() is false, then the propagated exception can
     * only be a RuntimeException.
     * </p>
     * 
     * <p>
     * This rollback and exception propagation behavior is consistent with
     * Spring's {@link
     * org.springframework.transaction.annotation.Transactional} annotation
     * behavior.  (Except, possibly, when a commit or rollback operation
     * fails.  See next paragraphs.)
     * </p>
     * 
     * <p>
     * In the situation where the callback marks the transaction as
     * rollback-only, a rollback will be attempted and if that rollback
     * succeeds, this method will exit normally without an exception being
     * thrown.  The return value of the method will equal the return value
     * of the callback.
     * </p>
     * 
     * <p>
     * In the situation where a checked exception is thrown but
     * txAttr.rollbackOn() returns false, a commit will occur.  If that
     * commit fails, a rollback will be attempted.  If that rollback
     * succeeds, a {@link UnexpectedRollbackException} will be thrown where
     * the message matches {@link
     * #ROLLBACKON_FALSE_COMMIT_FAILURE_ROLLBACK_SUCCEEDED_MESSAGE}.  The
     * callback exception is in {@link
     * UnexpectedRollbackException#getCause()} and the commit exception is
     * in {@link UnexpectedRollbackException#getSuppressed()}.  If that
     * rollback fails, see item 3 below.
     * </p>
     *  
     * <p>
     * There are three situations where a {@link TransactionSystemException}
     * is thrown from this method when a rollback fails.
     * </p>
     * <ol>
     *   <li>When the callback marks the transaction as rollbackOnly and
     *       then the rollback fails.
     *       <ul>
     *         <li>The exception message will match {@link
     *             #ROLLBACKONLY_ROLLBACK_FAILURE_MESSAGE}.</li>
     *         <li>The rollback exception is in {@link
     *             TransactionSystemException#getCause()}.</li>
     *       </ul>
     *   </li>
     *   <li>When an exception is thrown that txAttr.rollbackOn() returns
     *       true for and then that rollback fails.
     *       <ul>
     *         <li>The exception message will match {@link
     *             #ROLLBACKON_TRUE_ROLLBACK_FAILURE_MESSAGE}.</li>
     *         <li>The rollback exception is in {@link
     *             TransactionSystemException#getCause()}.</li>
     *         <li>The callback exception is in {@link
     *             TransactionSystemException#getApplicationException()}.</li>
     *       </ul>
     *   </li>
     *   <li>When an exception is thrown that txAttr.rollbackOn() returns
     *       false for and the commit() fails and then subsequent rollback
     *       fails too.
     *       <ul>
     *         <li>The exception message will match {@link
     *             #ROLLBACKON_FALSE_COMMIT_FAILURE_ROLLBACK_FAILURE_MESSAGE}.</li>
     *         <li>The rollback exception is in {@link
     *              TransactionSystemException#getCause()}.</li>
     *         <li>The callback exception is in {@link
     *             TransactionSystemException#getApplicationException()}.</li>
     *         <li>The commit exception is in {@link
     *             TransactionSystemException#getSuppressed()}.</li>
     *       </ul>
     *   </li>
     * </ol>
     * 
     * @param <T> The return type of the callback.
     * 
     * @param txMgr Execute the transaction within this transaction manager.
     *
     * @param def Use this TransactionDefinition to establish a new
     * transaction.  <b>The propagation behavior of this definition must be
     * REQUIRES_NEW_TRANSACTION</b>.  If it isn't, a
     * IllegalArgumentException will be thrown.
     *
     * @param holder Contains the callback to execute within the
     * transaction.  See @{link TransactionCallbackHolder}.  There are two
     * possible types of callbacks that the holder can contain (but it can
     * contain only one): A {@link TransactionCallback} that can only throw
     * a runtime exception or a {@link ThrowableTransactionCallback} that
     * can throw any type of exception.
     *
     * @return The return value from the callback.
     *
     * @throws Throwable The exception thrown by the callback or a {@link
     * org.springframework.transaction.TransactionException} if a problem
     * occurred with completing the transaction.
     */
    // @formatter:on
    public static <T> T execute(PlatformTransactionManager txMgr, TransactionDefinition def,
            TransactionCallbackHolder<T> holder) throws Throwable {
        checkPropagationBehavior(def);
        ManagedSpringTransactionImpl tx = createManagedSpringTransaction(txMgr, def);
        try {
            try {
                tx.beginTransaction();
            } catch (Throwable t) {
                throw new TransactionSystemException("Couldn't begin transaction", t);
            }
            T result = (holder.isThrowable() ? holder.throwable.doInTransaction(tx.getTransactionStatus())
                    : holder.notThrowable.doInTransaction(tx.getTransactionStatus()));
            if (!tx.getTransactionStatus().isRollbackOnly()) {
                // The callback exited with no exception and no rollbackOnly
                // flag, so we commit.
                tx.commit();
            } else {
                // The callback marked the transaction for rollbackOnly, so
                // we rollback instead of commit.
                try {
                    // The commit failed, so now we try a rollback.
                    tx.rollback();
                    // The callback marking the transaction as rollbackOnly
                    // results in normal exit (no exception thrown).
                } catch (RuntimeException rollbackException) {
                    // Both the commit and the rollback failed.
                    throw new TransactionSystemException(ROLLBACKONLY_ROLLBACK_FAILURE_MESSAGE, rollbackException);
                }
            }
            return result;
        } catch (Throwable t) {
            if (!tx.isStarted()) {
                if (t instanceof TransactionSystemException) {
                    throw t;
                } else {
                    throw new TransactionSystemException(
                            "An unexpected exception occured establishing the transaction: the transaction hasn't started yet.",
                            t);
                }
            }
            if (rollbackOn(holder, t)) {
                try {
                    tx.rollback();
                } catch (RuntimeException rollbackException) {
                    // The rollback failed.
                    TransactionSystemException tse = new TransactionSystemException(
                            ROLLBACKON_TRUE_ROLLBACK_FAILURE_MESSAGE, rollbackException);
                    tse.initApplicationException(t);
                    throw tse;
                }
            } else {
                // Since rollbackOn() returned false for this exception, we
                // commit.
                try {
                    tx.commit();
                } catch (RuntimeException commitException) {
                    try {
                        tx.rollback();
                    } catch (RuntimeException rollbackException) {
                        // The post-commit-failure rollback failed.
                        TransactionSystemException tse = new TransactionSystemException(
                                ROLLBACKON_FALSE_COMMIT_FAILURE_ROLLBACK_FAILURE_MESSAGE, rollbackException);
                        tse.initApplicationException(t);
                        tse.addSuppressed(commitException);
                        throw tse;
                    }
                    // The post-commit-failure rollback succeeded.
                    UnexpectedRollbackException rbe = new UnexpectedRollbackException(
                            ROLLBACKON_FALSE_COMMIT_FAILURE_ROLLBACK_SUCCEEDED_MESSAGE, t);
                    rbe.addSuppressed(commitException);
                    throw rbe;
                }
            }
            throw t;
        }
    }

    protected static final void checkPropagationBehavior(TransactionDefinition def) throws IllegalArgumentException {
        if (def.getPropagationBehavior() != TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            throw new IllegalArgumentException("The only supported propagation behavior is PROPAGATION_REQUIRES_NEW");
        }
    }

    protected static ManagedSpringTransactionImpl createManagedSpringTransaction(PlatformTransactionManager txMgr,
            TransactionDefinition def) {
        return new ManagedSpringTransactionImpl(txMgr, def);
    }

    /**
     * See
     * {@link #execute(PlatformTransactionManager, TransactionDefinition, TransactionCallbackHolder)}
     * for rollbackOn logic.
     */
    protected static <T> boolean rollbackOn(TransactionCallbackHolder<T> holder, Throwable t) {
        return holder.txAttr.rollbackOn(t);
    }
}
