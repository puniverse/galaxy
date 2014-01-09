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
package co.paralleluniverse.galaxy.cluster;

/**
 * A listener for lifecycle events.
 */
public interface LifecycleListener {
    /**
     * Invoked when this node has joined the cluster, but before it has gone online.
     */
    void joinedCluster();
    
    /**
     * Invoked when this node is online, i.e. operational and visible to the cluster.
     * @param master {@code true} if this node is a master, {@code false} if it's a slave.
     */
    void online(boolean master);

    /**
     * Invoked when this node is taken offline, i.e. shuts down. An offline node cannot go back online until the JVM has been restarted.
     */
    void offline();

    /**
     * Invoked when this (formerly) slave node has now become the master.
     */
    void switchToMaster();
}
