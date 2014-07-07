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

import java.nio.ByteBuffer;

/**
 * A listener for local cache events.
 */
public interface CacheListener {
    /**
     * The data item has been invalidated - probably requested exclusively by another node, or, possibly, deleted.
     * @param id The item's ID.
     */
    void invalidated(Cache cache, long id);

    /**
     * The data item has been updated by another node. This is the opposite of the {@link #invalidated(Cache, long) invalidated} event. This method is not called when the item is 
     * modified locally by a {@link Store#set(long, byte[], StoreTransaction) set()}.
     * 
     * @param id The item's ID.
     * @param version The item's version.
     * @param data The item's contents.
     */
    void received(Cache cache, long id, long version, ByteBuffer data);

    /**
     * The data item has been evicted from the local cache. Only items not owned by the local node can be evicted.
     * @param id The item's ID.
     */
    void evicted(Cache cache, long id);
    
    /**
     * The data item belonged to a node that has died (without slave replacement)
     * @param id  The item's ID.
     */
    void killed(Cache cache, long id);
    
    /**
     * Called when a message has been received.
     *
     * @param message  The message's contents.
     */
    void messageReceived(byte[] message);
}
