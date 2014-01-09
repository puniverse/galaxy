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
 * A listener for node-property change events.
 */
public interface NodePropertyListener {
    /**
     * Invoked when the node's property has been added, changed or removed.
     * @param node The node whose property has been modified.
     * @param property The name of the modified property.
     * @param value The new value of the property. If the property has been removed, this will be {@code null}.
     */
    void propertyChanged(NodeInfo node, String property, Object value);
}
