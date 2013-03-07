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
