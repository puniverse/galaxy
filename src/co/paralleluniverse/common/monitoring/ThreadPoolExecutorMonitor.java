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
package co.paralleluniverse.common.monitoring;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
public class ThreadPoolExecutorMonitor extends Monitor<ThreadPoolExecutor> implements ThreadPoolExecutorMXBean {
    public static void register(String name, ThreadPoolExecutor executor) {
        new ThreadPoolExecutorMonitor(name, executor);
    }
    
    public ThreadPoolExecutorMonitor(String name, ThreadPoolExecutor executor) {
        super("co.paralleluniverse:type=ThreadPoolExecutor,name=" + name, executor);
        registerMBean();
    }

    @Override
    public boolean isTerminating() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return false;
        return executor.isTerminating();
    }

    @Override
    public boolean isTerminated() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return false;
        return executor.isTerminated();
    }

    @Override
    public boolean isShutdown() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return false;
        return executor.isShutdown();
    }

    @Override
    public long getTaskCount() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getTaskCount();
    }

    @Override
    public String getRejectedExecutionHandler() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return null;
        return executor.getRejectedExecutionHandler().toString();
    }

    @Override
    public int getPoolSize() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getPoolSize();
    }

    @Override
    public int getMaximumPoolSize() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getMaximumPoolSize();
    }

    @Override
    public int getLargestPoolSize() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getLargestPoolSize();
    }

    @Override
    public long getKeepAliveTime() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    @Override
    public int getCorePoolSize() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getCorePoolSize();
    }

    @Override
    public long getCompletedTaskCount() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getCompletedTaskCount();
    }

    @Override
    public int getActiveCount() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getActiveCount();
    }
    
    @Override
    public int getQueuedTasks() {
        final ThreadPoolExecutor executor = getMonitored();
        if (executor == null)
            return -1;
        return executor.getQueue().size();
    }
}
