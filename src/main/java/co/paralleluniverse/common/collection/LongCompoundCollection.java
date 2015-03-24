/*
 * Copyright (c) 2012-2015, Parallel Universe Software Co. All rights reserved.
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
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 *
 * @author pron
 */
public class LongCompoundCollection extends AbstractLongCollection {
    private final Collection<LongCollection> collections;

    public LongCompoundCollection() {
        collections = new ArrayList<LongCollection>();
    }

    public void addCollection(LongCollection c) {
        collections.add(c);
    }

    public void removeCollection(LongCollection c) {
        collections.remove(c);
    }

    @Override
    public int size() {
        int size = 0;
        for(LongCollection c : collections)
            size += c.size();
        return size;
    }

    @Override
    public boolean isEmpty() {
        for(LongCollection c : collections) {
            if(!c.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public LongIterator iterator() {
        return new AbstractLongIterator() {
            private final Iterator<LongCollection> ce = collections.iterator();
            private LongIterator i = null;

            @Override
            public boolean hasNext() {
                setIterator();
                if (i == null)
                    return false;
                return i.hasNext();
            }

            @Override
            public long nextLong() {
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
        for (LongCollection c : collections)
            c.clear();
    }

    @Override
    public boolean contains(long value) {
        for (LongCollection c : collections) {
            if (c.contains(value))
                return true;
        }
        return false;
    }

    @Override
    public boolean rem(long value) {
        boolean retValue = false;
        for (LongCollection c : collections)
            retValue |= c.remove(value);
        return retValue;
    }
}
