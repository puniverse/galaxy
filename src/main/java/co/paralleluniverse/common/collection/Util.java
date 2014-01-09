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

import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 *
 * @author pron
 */
public class Util {
    public static Object addToSingleOrSet(Object obj, Object singleOrSet) {
        if (singleOrSet == null)
            return obj;
        else if (singleOrSet instanceof Set)
            return ((Set) singleOrSet).add(obj);
        else {
            final Set set = new HashSet();
            set.add(singleOrSet);
            set.add(obj);
            return set;
        }
    }

    public static Object removeFromSingleOrSet(Object obj, Object singleOrSet) {
        if (singleOrSet == null)
            return null;
        if (singleOrSet instanceof Set) {
            Set set = (Set) singleOrSet;
            set.remove(obj);
            if (set.isEmpty())
                return null;
            else
                return set;
        } else {
            assert obj == singleOrSet;
            return null;
        }
    }

    public static Set getSingleOrSet(Object singleOrSet) {
        if (singleOrSet instanceof Set)
            return (Set) singleOrSet;
        return Collections.singleton(singleOrSet);
    }

    public static <E> Iterable<E> reverse(final Deque<E> deque) {
        return new Iterable<E>() {
            @Override
            public Iterator<E> iterator() {
                return deque.descendingIterator();
            }
        };
    }

    public static <E> Iterable<E> reverse(final List<E> list) {
        return new Iterable<E>() {
            @Override
            public Iterator<E> iterator() {
                final ListIterator<E> it = list.listIterator(list.size());
                return new Iterator<E>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasPrevious();
                    }

                    @Override
                    public E next() {
                        return it.previous();
                    }

                    @Override
                    public void remove() {
                        it.remove();
                    }
                };
            }
        };
    }

    private Util() {
    }
}
