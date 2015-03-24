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
package co.paralleluniverse.galaxy.objects;

import co.paralleluniverse.galaxy.Cache;
import co.paralleluniverse.galaxy.CacheListener;

/**
 * Wraps T and implements Distributed interface
 *
 * @author eitan
 * @param <T>
 */
public class DistributedReferenceStore<R extends DistributedReference<T>, T> {
    private final Cache cache;

    public DistributedReferenceStore(Cache cache) {
        this.cache = cache;
    }

    public R newRef(long id, T obj) {
        if (id <= 0)
            return null;
        assert cache.getListener(id) == null;
        final R ref = createRef(id, obj);
        cache.setListener(id, ref);
        return ref;
    }

    public R getOrCreateRef(long id) {
        if (id <= 0)
            return null;
        final CacheListener ref = cache.getListener(id);
        return (R) (ref != null ? ref : cache.setListenerIfAbsent(id, createRef(id, null)));
    }

    protected R createRef(long id, T obj) {
        return (R) new DistributedReference<T>(id, obj);
    }

    public static <T> DistributedReference<T> newRef(Cache cache, long id, T obj) {
        if (id <= 0)
            return null;
        assert cache.getListener(id) == null;
        final DistributedReference<T> ref = new DistributedReference<T>(id, obj);
        cache.setListener(id, ref);
        return ref;
    }

    public static <T> DistributedReference<T> getOrCreateRef(Cache cache, long id) {
        if (id <= 0)
            return null;
        final CacheListener ref = cache.getListener(id);
        return (DistributedReference<T>) (ref != null ? ref : cache.setListenerIfAbsent(id, new DistributedReference<T>(id, null)));
    }
}
