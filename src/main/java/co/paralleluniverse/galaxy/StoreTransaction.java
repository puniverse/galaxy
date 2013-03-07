/*
 * Galaxy
 * Copyright (C) 2012 Parallel Universe Software Co.
 * 
 * This file is part of Galaxy.
 *
 * Galaxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * Galaxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.
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
