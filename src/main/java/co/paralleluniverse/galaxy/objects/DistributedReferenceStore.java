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

import co.paralleluniverse.galaxy.CacheListener;
import co.paralleluniverse.galaxy.Store;

/**
 * Wraps T and implements Distributed interface
 *
 * @author eitan
 * @param <T>
 */
public class DistributedReferenceStore<R extends DistributedReference<T>, T> {
    private final Store store;

    public DistributedReferenceStore(Store store) {
        this.store = store;
    }

    public R getOrCreateRef(long lineId) {
        if (lineId <= 0)
            return null;
        CacheListener ref = store.getListener(lineId);
        return (R) (ref != null ? ref : store.setListenerIfAbsent(lineId, createRef(lineId, null)));
    }

    public R newRef(long lineId, T obj) {
        if (lineId <= 0)
            return null;
        assert store.getListener(lineId) == null;
        R ref = createRef(lineId, obj);
        store.setListener(lineId, ref);
        return ref;
    }

    protected R createRef(long id, T obj) {
        return (R)new DistributedReference<T>(id, obj);
    }
}
