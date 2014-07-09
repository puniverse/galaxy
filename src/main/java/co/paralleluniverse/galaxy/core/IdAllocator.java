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

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author pron
 */
class IdAllocator implements RefAllocator.RefAllocationsListener {
    private final Cache cache;
    private final RefAllocator refAllocator;
    private final static int REFS_TO_ALLOCATE = 10000; // TODO: dynamic
    private List<Op> pendingOps = new ArrayList<Op>();
    private long nextId = -1;
    private long minId = -1;
    private long maxId = -1;
    private boolean requestedMoreIds;
    private volatile long nextMinId = -1;
    private volatile long nextMaxId = -1;
    private boolean ready;
    
    public IdAllocator(Cache cache, RefAllocator refAllocator) {
        this.cache = cache;
        this.refAllocator = refAllocator;
        refAllocator.addRefAllocationsListener(this);
    }

    public RefAllocator getRefAllocator() {
        return refAllocator;
    }

    @Override
    public synchronized void counterReady() {
        ready = true;
        cache.allocatorReady();
    }

    public synchronized boolean isReady() {
        return ready;
    }

    private synchronized long allocateIds(int count) {
        if (nextId + count > maxId && nextMinId > minId) {
            minId = nextMinId;
            maxId = nextMaxId;
            nextId = minId;
        }

        final long id;
        if (nextId + count - 1 <= maxId) {
            id = nextId;
            nextId += count;
        } else
            id = -1;

        if (!requestedMoreIds && (id == -1 || shouldAllocateMoreIds())) {
            allocateMoreRefs(count);
            requestedMoreIds = true;
        }
        return id;
    }

    public synchronized long allocateIds(Op op, int count) {
        final long id = allocateIds(count);
        if (id == -1) {
            if (!op.hasFuture())
                op.createFuture();
            pendingOps.add(op);
        }

        return id;
    }

    private boolean shouldAllocateMoreIds() {
        return nextId > (minId + (maxId - minId) / 2);
    }

    void allocateMoreRefs(int count) {
        refAllocator.allocateRefs(Math.max(2 * count, REFS_TO_ALLOCATE));
    }

    @Override
    public void refsAllocated(long start, int num) {
        List<Op> pending;
        synchronized (this) {
            assert start > nextId || (start == minId);
            assert start + num > maxId;

            if (start == minId) { // allow for repeated calls, esp. in tests
                assert start + num > maxId;

                maxId = start + num - 1;
            } else {
                assert start > nextId;

                requestedMoreIds = false;
                this.nextMinId = start;
                this.nextMaxId = start + num - 1;
            }

            pending = pendingOps;
            pendingOps = new ArrayList<Op>();
        }

        for (Op op : pending)
            cache.runOp(op);
    }
}
