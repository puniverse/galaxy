/*
 * Galaxy
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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.collection.LongCompoundCollection;
import co.paralleluniverse.common.collection.LongObjectProcedure;
import co.paralleluniverse.galaxy.StoreTransaction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author pron
 */
public class Transaction extends StoreTransaction {
    private LongCollection lines;
    private LongSet ls;
    private List<Op> ops;
    private final Long2ObjectOpenHashMap<RollbackInfo> rollbackLog;

    Transaction(boolean rollback) {
        rollbackLog = rollback ? new Long2ObjectOpenHashMap<RollbackInfo>() : null;
    }

    synchronized void add(long id) {
        if (ls == null)
            ls = new LongOpenHashSet();
        ls.add(id);
        if (lines == null)
            lines = ls;
        else if (lines != ls)
            ((LongCompoundCollection) lines).addCollection(ls);
    }

    synchronized void add(LongCollection c) {
        if (lines == null)
            lines = new LongCompoundCollection();
        else if (lines == ls) {
            lines = new LongCompoundCollection();
            ((LongCompoundCollection) lines).addCollection(ls);
        }
        ((LongCompoundCollection) lines).addCollection(c);
    }

    synchronized void add(Op op) {
        if (ops == null)
            ops = new ArrayList<Op>();
        ops.add(op);
    }

    synchronized boolean isRecorded(long id) {
        return rollbackLog.containsKey(id);
    }

    synchronized void recordRollback(long id, long version, boolean modified, byte[] data) {
        assert rollbackLog.get(id) == null;
        rollbackLog.put(id, new RollbackInfo(version, modified, data));
    }

    synchronized LongCollection getLines() {
        return lines != null ? lines : LongLists.EMPTY_LIST;
    }

    synchronized List<Op> getOps() {
        return ops == null ? Collections.<Op>emptyList() : ops;
    }

    synchronized void forEachRollback(LongObjectProcedure<RollbackInfo> proc) {
        for (Long2ObjectMap.Entry<RollbackInfo> entry : rollbackLog.long2ObjectEntrySet())
            proc.execute(entry.getLongKey(), entry.getValue());
    }

    @Override
    public synchronized Iterator<Long> iterator() {
        if (lines == null)
            return Collections.emptyIterator();
        final LongIterator it = lines.iterator();
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
