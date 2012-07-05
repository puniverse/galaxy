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

import java.nio.ByteBuffer;

/**
 * A listener for local cache events.
 */
public interface CacheListener {
    /**
     * The data item has been invalidated - probably requested exclusively by another node, or, possibly, deleted.
     * @param id The item's ID.
     */
    void invalidated(long id);

    /**
     * The data item has been updated by another node. This is the opposite of the {@link #invalidated(long) invalidated} event. This method is not called when the item is 
     * modified locally by a {@link Store#set(long, byte[], StoreTransaction) set()}.
     * 
     * @param id The item's ID.
     * @param version The item's version.
     * @param data The item's contents.
     */
    void received(long id, long version, ByteBuffer data);

    /**
     * The data item has been evicted from the local cache. Only items not owned by the local node can be evicted.
     * @param id The item's ID.
     */
    void evicted(long id);
}
