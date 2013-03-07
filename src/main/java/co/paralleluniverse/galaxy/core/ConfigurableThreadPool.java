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

import java.beans.ConstructorProperties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
public class ConfigurableThreadPool extends ThreadPoolExecutor {
    @ConstructorProperties({"corePoolSize", "maximumPoolSize", "keepAliveMillis", "maxQueueSize"})
    public ConfigurableThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveMillis, int maxQueueSize) {
        super(corePoolSize, maximumPoolSize, keepAliveMillis, TimeUnit.MILLISECONDS, makeQueue(maxQueueSize));
    }
    
    private static BlockingQueue<Runnable> makeQueue(int maxSize) {
        if(maxSize == 0)
            return new SynchronousQueue<Runnable>();
        else if(maxSize > 0)
            return new ArrayBlockingQueue<Runnable>(maxSize);
        else
            return new LinkedTransferQueue<Runnable>();
    }
}
