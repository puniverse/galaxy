/*
 * Galaxy
 * Copyright (C) 2012 Parallel Universe Software Co.
 * 
 * This file is part of Galaxy.
 *
 * Galaxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * Galaxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapted from Netty's org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor
 * @author pron
 */
public abstract class OrderedThreadPoolExecutor extends ThreadPoolExecutor {
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
        if (task instanceof ChildExecutor)
            super.execute(task);
        else
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
    
    protected final class ChildExecutor implements Executor, Runnable {
        private final Queue<Runnable> tasks = QueueFactory.getInstance(maxQueueSize);
        private final AtomicBoolean isRunning = new AtomicBoolean();

        @Override
        public void execute(Runnable command) {
            tasks.add(command); // TODO: What todo if the add return false ?

            if (!isRunning.get())
                OrderedThreadPoolExecutor.super.execute(this);
        }

        @Override
        public void run() {
            if (isRunning.compareAndSet(false, true)) {
                try {
                    Thread thread = Thread.currentThread();
                    for (;;) {
                        final Runnable task = tasks.poll();
                        if (task == null)
                            break;

                        beforeExecute(thread, task);
                        try {
                            task.run();
                            afterExecute(task, null);
                        } catch (RuntimeException e) {
                            afterExecute(task, e);
                            throw e;
                        }
                    }
                } finally {
                    isRunning.set(false);
                }

                if (!isRunning.get() && tasks.peek() != null)
                    OrderedThreadPoolExecutor.super.execute(this);
            }
        }
    }
}
