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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.collection.TLongAbstractCollection;
import co.paralleluniverse.common.collection.TLongCompoundCollection;
import co.paralleluniverse.galaxy.StoreTransaction;
import gnu.trove.TLongCollection;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongObjectProcedure;
import gnu.trove.set.hash.TLongHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author pron
 */
public class Transaction extends StoreTransaction {
    private TLongCollection lines;
    private TLongHashSet ls;
    private List<Op> ops;
    private final TLongObjectHashMap<RollbackInfo> rollbackLog;

    Transaction(boolean rollback) {
        rollbackLog = rollback ? new TLongObjectHashMap<RollbackInfo>() : null;
    }

    synchronized void add(long id) {
        if (ls == null)
            ls = new TLongHashSet();
        ls.add(id);
        if (lines == null)
            lines = ls;
        else if (lines != ls)
            ((TLongCompoundCollection) lines).addCollection(ls);
    }

    synchronized void add(TLongCollection c) {
        if (lines == null)
            lines = new TLongCompoundCollection();
        else if (lines == ls) {
            lines = new TLongCompoundCollection();
            ((TLongCompoundCollection) lines).addCollection(ls);
        }
        ((TLongCompoundCollection) lines).addCollection(c);
    }

    synchronized void add(Op op) {
        if (ops == null)
            ops = new ArrayList<Op>();
        ops.add(op);
    }
    synchronized boolean isRecorded(long id) {
        return rollbackLog.contains(id);
    }

    synchronized void recordRollback(long id, long version, boolean modified, byte[] data) {
        RollbackInfo prev = rollbackLog.putIfAbsent(id, new RollbackInfo(version, modified, data));
        assert prev == null;
    }

    synchronized TLongCollection getLines() {
        return lines != null ? lines : TLongAbstractCollection.EMPTY_COLLECTION;
    }

    synchronized List<Op> getOps() {
        return ops == null ? Collections.<Op>emptyList() : ops;
    }

    synchronized void forEachRollback(TLongObjectProcedure<RollbackInfo> proc) {
        rollbackLog.forEachEntry(proc);
    }

    @Override
    public synchronized Iterator<Long> iterator() {
        if (lines == null)
            return Collections.emptyIterator();
        final TLongIterator it = lines.iterator();
        return new Iterator<Long>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Long next() {
                return it.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public synchronized boolean contains(long id) {
        if (lines == null)
            return false;
        return lines.contains(id);
    }

    public static class RollbackInfo {
        public final long version;
        public final boolean modified;
        public final byte[] data;

        public RollbackInfo(long version, boolean modified, byte[] data) {
            this.version = version;
            this.modified = modified;
            this.data = data;
        }
    }
}
