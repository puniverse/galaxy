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

/**
 *
 * @author pron
 */
interface CacheMonitor {
    void setMonitoredObject(Object obj);

    void addMessageReceived(Message.Type msg);

    void addMessageSent(Message.Type msg);

    void addMessageHandlingDelay(int numDelayed, long totalDelayNanos, MessageDelayReason reason);

    void addOp(Op.Type type, long durationMicroSeconds);

    void addHit();

    void addStaleHit();

    void addMiss();

    void addInvalidate(int num);
    
    void addStalePurge(int num);

    enum MessageDelayReason {
        LOCK, BACKUP, OTHER
    }
}
