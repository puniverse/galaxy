/*
 * Galaxy
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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.concurrent.OrderedThreadPoolExecutor;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.ConstructorProperties;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Runs only Runnables that implement NodeAttached (you can extend NodeTask).
 * @author pron
 */
public class NodeOrderedThreadPoolExecutor extends OrderedThreadPoolExecutor {

    @ConstructorProperties({"cluster", "corePoolSize", "maximumPoolSize", "keepAliveTime", "unit", "maxQueueSize"})
    public NodeOrderedThreadPoolExecutor(Cluster cluster, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize, new ThreadFactoryBuilder().setDaemon(true).build());
        cluster.addNodeChangeListener(listener);
    }

    @ConstructorProperties({"cluster", "corePoolSize", "maximumPoolSize", "keepAliveTime", "unit", "maxQueueSize", "threadFactory"})
    public NodeOrderedThreadPoolExecutor(Cluster cluster, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize, threadFactory);
        cluster.addNodeChangeListener(listener);
    }

    @ConstructorProperties({"cluster", "corePoolSize", "maximumPoolSize", "keepAliveTime", "unit", "maxQueueSize", "handler"})
    public NodeOrderedThreadPoolExecutor(Cluster cluster, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize, new ThreadFactoryBuilder().setDaemon(true).build(),handler);
        cluster.addNodeChangeListener(listener);
    }

    @ConstructorProperties({"cluster", "corePoolSize", "maximumPoolSize", "keepAliveTime", "unit", "maxQueueSize", "threadFactory", "handler"})
    public NodeOrderedThreadPoolExecutor(Cluster cluster, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize, threadFactory, handler);
        cluster.addNodeChangeListener(listener);
    }
    
    private final NodeChangeListener listener = new NodeChangeListener() {

        @Override
        public void nodeRemoved(short id) {
            removeChildExecutor(id);
        }
        
        @Override
        public void nodeAdded(short id) {
        }

        @Override
        public void nodeSwitched(short id) {
        }
    };
    
    @Override
    protected Object getChildExecutorKey(Runnable task) {
        return ((NodeAttached)task).getNode();
    }
}
