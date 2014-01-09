/*
 * Copyright (c) 2012-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.common.concurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author pron
 */
public class SimpleFuture<V> implements Future<V> {

    private final Lock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();
    private volatile boolean done;
    private V result;
    private Throwable exception;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        lock.lock();
        try {
            while (!done)
                cond.await();
            return getResult();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        lock.lock();
        try {
            if (!cond.await(timeout, unit))
                throw new TimeoutException();
            else
                return getResult();
        } finally {
            lock.unlock();
        }
    }

    private V getResult() throws ExecutionException {
        if(exception != null)
            throw new ExecutionException(exception);
        else
            return result;
    }
    
    public void setResult(V result) {
        this.result = result;
    }
    
    public void setException(Throwable exception) {
        this.exception = exception;
    }
    
    public void done() {
        lock.lock();
        try {
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
    
    public void done(V result) {
        lock.lock();
        try {
            setResult(result);
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
    
    public void failed(Throwable exception) {
        lock.lock();
        try {
            setException(exception);
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
