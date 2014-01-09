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
 * A listener for events relating to this master-slaves node group configuration.
 */
public interface SlaveConfigurationListener {
    /**
     * Invoked when this (slave) node has got a new master.
     * @param node The new master node.
     */
    void newMaster(NodeInfo node);

    /**
     * Invoked when a new slave was added to this (master) node's group.
     * @param node The new slave.
     */
    void slaveAdded(NodeInfo node);

    /**
     * Invoked when a slave has left this (master) node's group (it's gone offline).
     * @param node The offline slave.
     */
    void slaveRemoved(NodeInfo node);
}
