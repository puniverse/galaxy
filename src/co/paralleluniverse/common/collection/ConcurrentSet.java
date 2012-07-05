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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author pron
 */
public class ConcurrentSet<E> extends AbstractSet<E> implements Set<E> {
    public static <E> Set<E> make(ConcurrentMap<E, Object> map) {
        return new ConcurrentSet<E>(map);
    }
    
    private final ConcurrentMap<E, Object> map;
    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();

    public ConcurrentSet(ConcurrentMap<E, Object> map) {
        this.map = map;
    }

    public ConcurrentSet(ConcurrentMap<E, Object> map, Collection<E> elements) {
        for(E elem : elements)
            map.put(elem, PRESENT);
        this.map = map;
    }

    @Override
    public boolean add(E e) {
        return map.putIfAbsent(e, PRESENT) != PRESENT;
    }

    @Override
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    public int size() {
        return map.size();
    }
}
