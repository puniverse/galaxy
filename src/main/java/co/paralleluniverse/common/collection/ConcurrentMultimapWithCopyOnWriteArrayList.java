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
