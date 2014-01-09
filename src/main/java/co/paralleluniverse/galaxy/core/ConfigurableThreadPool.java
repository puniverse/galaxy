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
