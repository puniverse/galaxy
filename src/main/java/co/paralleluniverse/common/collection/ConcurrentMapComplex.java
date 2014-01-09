/*
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
