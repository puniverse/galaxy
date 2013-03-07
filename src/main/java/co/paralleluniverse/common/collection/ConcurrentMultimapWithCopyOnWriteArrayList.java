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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 *
 * @author pron
 */
public class ConcurrentMultimapWithCopyOnWriteArrayList<K, V> extends ConcurrentMultimap<K, V, List<V>> {
    public ConcurrentMultimapWithCopyOnWriteArrayList() {
        super(new NonBlockingHashMap<K, List<V>>(), (List<V>) Collections.EMPTY_LIST);
    }

    @Override
    protected List<V> allocateElement() {
        return new CopyOnWriteArrayList<V>();
    }
}
