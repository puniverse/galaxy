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

import gnu.trove.TLongCollection;
import gnu.trove.iterator.TLongIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author pron
 */
public class TLongCompoundCollection extends TLongAbstractCollection {
    private Collection<TLongCollection> collections;

    public TLongCompoundCollection() {
        collections = new ArrayList<TLongCollection>();
    }

    public void addCollection(TLongCollection c) {
        collections.add(c);
    }

    public void removeCollection(TLongCollection c) {
        collections.remove(c);
    }

    @Override
    public int size() {
        int size = 0;
        for(TLongCollection c : collections)
            size += c.size();
        return size;
    }

    @Override
    public boolean isEmpty() {
        for(TLongCollection c : collections) {
            if(!c.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public TLongIterator iterator() {
        return new TLongIterator() {
            private Iterator<TLongCollection> ce = collections.iterator();
            private TLongIterator i = null;

            @Override
            public boolean hasNext() {
                setIterator();
                if (i == null)
                    return false;
                return i.hasNext();
            }

            @Override
            public long next() {
                setIterator();
                if (i == null)
                    throw new NoSuchElementException();
                return i.next();
            }

            @Override
            public void remove() {
                i.remove();
            }

            private void setIterator() {
                if (i == null || !i.hasNext()) {
                    if (ce.hasNext())
                        i = ce.next().iterator();
                }
            }
        };
    }

    @Override
    public void clear() {
        for (TLongCollection c : collections)
            c.clear();
    }

    @Override
    public boolean contains(long value) {
        for (TLongCollection c : collections) {
            if (c.contains(value))
                return true;
        }
        return false;
    }

    @Override
    public boolean remove(long value) {
        boolean retValue = false;
        for (TLongCollection c : collections)
            retValue |= c.remove(value);
        return retValue;
    }
}
