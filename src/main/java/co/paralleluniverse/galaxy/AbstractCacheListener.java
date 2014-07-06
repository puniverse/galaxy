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
 * An empty (no-op) implementation of {@link CacheListener}.
 */
public abstract class AbstractCacheListener implements CacheListener {

    @Override
    public void invalidated(Cache cache, long id) {
    }

    @Override
    public void received(Cache cache, long id, long version, ByteBuffer data) {
    }

    @Override
    public void evicted(Cache cache, long id) {
    }

    @Override
    public void killed(Cache cache, long id) {
    }

    @Override
    public void messageReceived(byte[] message) {
        throw new RuntimeException("Received unexpected message (" + message.length + " bytes)");
    }
}
