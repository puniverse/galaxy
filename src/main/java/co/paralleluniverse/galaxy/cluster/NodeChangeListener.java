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
 * A a listener for events relating nodes joining and leaving the cluster.
 */
public interface NodeChangeListener {
    /**
     * Invoked when a new node (group) has joined the cluster.
     * @param id The node (group) ID.
     */
    void nodeAdded(short id);

    /**
     * Invoked when a node group's master has changed.
     * @param id The node (group) ID.
     */
    void nodeSwitched(short id);

    /**
     * Invoked when a node (group) has left the cluster.
     * @param id The node (group) ID.
     */
    void nodeRemoved(short id);
}
