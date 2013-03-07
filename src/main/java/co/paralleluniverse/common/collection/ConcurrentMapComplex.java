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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author pron
 */
public abstract class ConcurrentMapComplex<K, V> {
    protected final ConcurrentMap<K, V> map;

    public ConcurrentMapComplex(ConcurrentMap<K, V> map) {
        this.map = map;
    }

    public ConcurrentMapComplex() {
        this.map = new ConcurrentHashMap<K, V>();
    }

    public V getOrAllocate(K key) {
        V coll = map.get(key);
        if (coll == null) {
            coll = allocateElement();
            V tmp = map.putIfAbsent(key, coll);
            if (tmp != null)
                coll = tmp;
        }
        return coll;
    }

    public V get(K key) {
        final V value = map.get(key);
        return value != null ? value : emptyElement();
    }

    public void remove(K key) {
        map.remove(key);
    }

    protected abstract V allocateElement();

    protected abstract V emptyElement();
}
