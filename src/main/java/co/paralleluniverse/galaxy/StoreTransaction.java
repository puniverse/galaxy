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

import java.util.Iterator;

/**
 * Represents a grid data-store transaction.
 * @see Store#beginTransaction() 
 */
public abstract class StoreTransaction implements Iterable<Long> {
    /**
     * Returns an iterator over all item IDs participating in the transaction.
     * @return An iterator over all item IDs participating in the transaction.
     */
    @Override
    public abstract Iterator<Long> iterator();
    
    /**
     * Tests whether a given item participates in this transaction.
     * @param id The item ID.
     * @return {@code true} id the item participates in the transaction; {@code false} otherwise.
     */
    public abstract boolean contains(long id);
}
