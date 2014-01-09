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
package co.paralleluniverse.galaxy;

/**
 * Represents the state of an item in the Galaxy local store.
 */
public enum ItemState {
    /**
     * Item is not found in this node, or may be stale.
     */
    INVALID,
    
    /**
     * Item is shared by this node, but is owned by another.
     */
    SHARED,
    
    /**
     * Item is owned by this node.
     */
    OWNED; 

    public boolean isLessThan(ItemState other) {
        return compareTo(other) < 0;
    }
}
