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

import gnu.trove.iterator.TLongIterator;

/**
 *
 * @author pron
 */
public class TLongRange extends TLongAbstractCollection {
    final long start;
    final int length;

    public TLongRange(long start, int length) {
        this.start = start;
        this.length = length;
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public TLongIterator iterator() {
        return new TLongIterator() {
            private int nextIndex = 0;

            @Override
            public boolean hasNext() {
                return nextIndex < length;
            }

            @Override
            public long next() {
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
    public boolean remove(long entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }
}
