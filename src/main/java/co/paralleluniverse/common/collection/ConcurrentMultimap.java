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
