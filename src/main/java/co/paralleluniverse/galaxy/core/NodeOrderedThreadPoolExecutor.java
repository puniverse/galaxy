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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.concurrent.OrderedThreadPoolExecutor;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import java.beans.ConstructorProperties;
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
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize);
        cluster.addNodeChangeListener(listener);
    }

    @ConstructorProperties({"cluster", "corePoolSize", "maximumPoolSize", "keepAliveTime", "unit", "maxQueueSize", "threadFactory"})
    public NodeOrderedThreadPoolExecutor(Cluster cluster, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize, threadFactory);
        cluster.addNodeChangeListener(listener);
    }

    @ConstructorProperties({"cluster", "corePoolSize", "maximumPoolSize", "keepAliveTime", "unit", "maxQueueSize", "handler"})
    public NodeOrderedThreadPoolExecutor(Cluster cluster, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, int maxQueueSize, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, maxQueueSize, handler);
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
