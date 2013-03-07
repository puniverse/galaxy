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
package co.paralleluniverse.common.collection;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author pron
 */
public abstract class ConcurrentMultimap<K, V, C extends Collection<V>> extends ConcurrentMapComplex<K, C> {
    private final C emptyCollection;

    public ConcurrentMultimap(ConcurrentMap<K, C> map, C emptyCollection) {
        super(map);
        this.emptyCollection = emptyCollection;
    }

    public ConcurrentMultimap(C emptyCollection) {
        this.emptyCollection = emptyCollection;
    }

    @Override
    protected C emptyElement() {
        return emptyCollection;
    }

    public void put(K key, V value) {
        getOrAllocate(key).add(value);
    }

    public void put(K key, Collection<V> values) {
        getOrAllocate(key).addAll(values);
    }

    public void remove(K key, V value) {
        final C coll = get(key);
        if (coll == null)
            return;
        coll.remove(value);
    }
    
    public Collection<C> values() {
        return map.values();
    }
    
    public Set<K> keySet() {
        return map.keySet();
    }
}
