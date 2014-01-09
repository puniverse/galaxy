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

import co.paralleluniverse.galaxy.monitoring.Counter;
import co.paralleluniverse.galaxy.monitoring.OffHeapLocalStorageMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author pron
 */
class JMXOffHeapLocalStorageMonitor extends JMXLocalStorageMonitor implements OffHeapLocalStorageMonitor, OffHeapLocalStorageMXBean {
    private final Integer[] bins;
    private final BinCounters[] binCounters;

    public JMXOffHeapLocalStorageMonitor(String name, CacheStorage storage, int[] bins) {
        super(OffHeapLocalStorageMXBean.class, name, storage);
        this.bins = new Integer[bins.length];
        this.binCounters = new BinCounters[bins.length];
        for (int i = 0; i < bins.length; i++) {
            this.bins[i] = bins[i];
            this.binCounters[i] = new BinCounters();
        }
    }

    @Override
    protected void collectAndResetCounters() {
        super.collectAndResetCounters();
        for (BinCounters bc : binCounters)
            bc.collectAndReset();
    }

    @Override
    protected void resetCounters() {
        super.resetCounters();
        for (BinCounters bc : binCounters)
            bc.reset();
    }

    @Override
    public void allocated(int bin, int size) {
        super.allocated(size);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deallocated(int bin, int size) {
        super.deallocated(size);
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Map<Integer, Integer> getBinsAllocated() {
        Map<Integer, Integer> table = new HashMap<Integer, Integer>(binCounters.length);
        for (int i = 0; i < binCounters.length; i++)
            table.put(bins[i], binCounters[i].allocated);
        return table;
    }

    @Override
    public Map<Integer, Integer> getBinsDeallocated() {
        Map<Integer, Integer> table = new HashMap<Integer, Integer>(binCounters.length);
        for (int i = 0; i < binCounters.length; i++)
            table.put(bins[i], binCounters[i].deallocated);
        return table;
    }

    @Override
    public Map<Integer, Long> getBinsTotalMemory() {
        Map<Integer, Long> table = new HashMap<Integer, Long>(binCounters.length);
        for (int i = 0; i < binCounters.length; i++)
            table.put(bins[i], binCounters[i].totalSize.get());
        return table;
    }

    private static class BinCounters {
        public final AtomicLong totalSize = new AtomicLong();
        public final Counter allocatedCounter = new Counter();
        public final Counter deallocatedCounter = new Counter();
        public int allocated;
        public int deallocated;

        public void collectAndReset() {
            allocated = (int) allocatedCounter.get();
            deallocated = (int) deallocatedCounter.get();

            allocatedCounter.reset();
            deallocatedCounter.reset();
        }

        public void reset() {
            allocatedCounter.reset();
            deallocatedCounter.reset();
        }
    }
}
