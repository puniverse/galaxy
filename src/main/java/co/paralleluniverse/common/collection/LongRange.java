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

import it.unimi.dsi.fastutil.longs.AbstractLongCollection;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 *
 * @author pron
 */
public class LongRange extends AbstractLongCollection {
    final long start;
    final int length;

    public LongRange(long start, int length) {
        this.start = start;
        this.length = length;
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public LongIterator iterator() {
        return new AbstractLongIterator() {
            private int nextIndex = 0;

            @Override
            public boolean hasNext() {
                return nextIndex < length;
            }

            @Override
            public long nextLong() {
                return start + (nextIndex++);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public boolean contains(long entry) {
        return (entry >= start && entry < (start + length));
    }

    @Override
    public boolean rem(long entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
