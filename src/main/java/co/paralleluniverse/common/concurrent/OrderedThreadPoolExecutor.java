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

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapted from Netty's org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor
 *
 * @author pron
 */
public abstract class OrderedThreadPoolExecutor extends ThreadPoolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(OrderedThreadPoolExecutor.class);
    protected final ConcurrentMap<Object, Executor> childExecutors = new ConcurrentHashMap<Object, Executor>();
    private final int maxQueueSize;

    public OrderedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory, handler);
        this.maxQueueSize = maxQueueSize;
    }

    public OrderedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), handler);
        this.maxQueueSize = maxQueueSize;
    }

    public OrderedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>(), threadFactory);
        this.maxQueueSize = maxQueueSize;
    }

    public OrderedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new SynchronousQueue<Runnable>());
        this.maxQueueSize = maxQueueSize;
    }

    @Override
    public void execute(Runnable task) {
        assert !(task instanceof ChildExecutor);

        getChildExecutor(task).execute(task);
    }

    protected abstract Object getChildExecutorKey(Runnable task);

    protected boolean removeChildExecutor(Object key) {
        // FIXME: Succeed only when there is no task in the ChildExecutor's queue.
        //        Note that it will need locking which might slow down task submission.
        return childExecutors.remove(key) != null;
    }

    protected Executor getChildExecutor(Runnable task) {
        Object key = getChildExecutorKey(task);
        Executor executor = childExecutors.get(key);
        if (executor == null) {
            executor = new ChildExecutor();
            Executor oldExecutor = childExecutors.putIfAbsent(key, executor);
            if (oldExecutor != null) {
                executor = oldExecutor;
            }
        }

        return executor;
    }

    private final class ChildExecutor implements Executor, Runnable {
        private final Queue<Runnable> tasks = QueueFactory.getInstance(maxQueueSize);
        private boolean running;

        @Override
        public void execute(Runnable command) {
            boolean start = false;
            synchronized (this) {
                try {
                    tasks.add(command); // TODO: What todo if the add return false ?
                } catch (IllegalStateException ex) {
                    LOG.error("my queue full", ex);
                    throw ex;
                }
                if (!running) {
                    running = true;
                    start = true;
                }
            }

            if (start) {
                try {
                    OrderedThreadPoolExecutor.super.execute(this);
                } catch (Exception e) {
                    LOG.error("exexution failed. poolsize {}. activeCount {}", OrderedThreadPoolExecutor.super.getPoolSize(), OrderedThreadPoolExecutor.super.getActiveCount());
                    throw e;
                }
            }
        }

        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            for (;;) {
                final Runnable task;
                synchronized (this) {
                    task = tasks.poll();
                    if (task == null) {
                        running = false;
                        break;
                    }
                }

                beforeExecute(thread, task);
                try {
                    task.run();
                    afterExecute(task, null);
                } catch (RuntimeException e) {
                    afterExecute(task, e);
                    LOG.error("Error while executing task " + task, e);
                }
            }
        }
    }
}
