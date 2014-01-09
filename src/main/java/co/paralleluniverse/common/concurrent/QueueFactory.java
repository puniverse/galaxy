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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.SynchronousQueue;


/**
 *
 * @author pron
 */
public final class QueueFactory{
    
    private QueueFactory() {
    }
    
    public static <T> BlockingQueue<T> getInstance(int maxSize) {
        if(maxSize == 0)
            return new SynchronousQueue<T>();
        else if(maxSize > 0)
            return new ArrayBlockingQueue<T>(maxSize);
        else
            return new LinkedTransferQueue<T>();
    }
}
