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
 *
 * @author pron
 */
public interface Cache {
    /**
     * Sets a listener listening for local cache events on the given item.
     *
     * @param id       The item's ID.
     * @param listener The listener.
     */
    void setListener(long id, CacheListener listener);

    /**
     * Sets a listener listening for local cache events on the given item if absent.
     *
     * @param id       The item's ID.
     * @param listener The listener.
     * @return The given listener if it was set or the existing one otherwise.
     */
    CacheListener setListenerIfAbsent(long id, CacheListener listener);

    /**
     * @param id The item's ID.
     * @return The cacheListener of this line
     */
    CacheListener getListener(long id);
}
