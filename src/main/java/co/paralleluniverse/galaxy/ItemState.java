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
