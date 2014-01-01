/*
 * Galaxy
 * Copyright (C) 2012-2013 Parallel Universe Software Co.
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
        R ref = createRef(id, obj);
        cache.setListener(id, ref);
        return ref;
    }

    public R getOrCreateRef(long id) {
        if (id <= 0)
            return null;
        CacheListener ref = cache.getListener(id);
        return (R) (ref != null ? ref : cache.setListenerIfAbsent(id, createRef(id, null)));
    }

    protected R createRef(long id, T obj) {
        return (R) new DistributedReference<T>(id, obj);
    }

    public static <T> DistributedReference<T> newRef(Cache cache, long id, T obj) {
        if (id <= 0)
            return null;
        assert cache.getListener(id) == null;
        DistributedReference<T> ref = new DistributedReference<T>(id, obj);
        cache.setListener(id, ref);
        return ref;
    }

    public static <T> DistributedReference<T> getOrCreateRef(Cache cache, long id) {
        if (id <= 0)
            return null;
        CacheListener ref = cache.getListener(id);
        return (DistributedReference<T>) (ref != null ? ref : cache.setListenerIfAbsent(id, new DistributedReference<T>(id, null)));
    }
}
