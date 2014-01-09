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

import java.util.Set;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

/**
 *
 * @author pron
 */
public class ConcurrentLongSet {
    private static final Object VALUE = new Object();
    private final NonBlockingHashMapLong<Object> map = new NonBlockingHashMapLong<Object>();

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean contains(long obj) {
        return map.containsKey(obj);
    }

    public void clear() {
        map.clear();
    }

    public boolean add(long e) {
        return map.put(e, VALUE) != null;
    }

    public boolean remove(long o) {
        return map.remove(o) != null;
    }
    
    public long[] getSnapshotAndClear() {
        final Set<Long> set = map.keySet();
        final long[] array = new long[set.size()];
        int i=0;
        for(NonBlockingHashMapLong.IteratorLong it = (NonBlockingHashMapLong.IteratorLong)set.iterator(); it.hasNext(); ) {
            array[i] = it.nextLong();
            it.remove();
            i++;
        }
        assert i == array.length;
        return array;
    }
}
