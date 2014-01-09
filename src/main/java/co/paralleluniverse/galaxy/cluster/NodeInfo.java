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

import java.util.Collection;

/**
 * Provides information about a cluster node.
 */
public interface NodeInfo {
    /**
     * Returns the node's cluster-wide unique name.
     * @return The node's name.
     */
    String getName();

    /**
     * Returns the node's ID. ID's are a master and all its slave nodes share the same ID.
     * @return The node's ID.
     */
    short getNodeId();

    /**
     * Returns a given property of the node.
     * @param property The name of the property whose value we want.
     * @return The property's value.
     */
    Object get(String property);

    /**
     * Returns a collection of all of this node's property names.
     * @return A collection of all of this node's property names.
     */
    Collection<String> getProperties();
}
