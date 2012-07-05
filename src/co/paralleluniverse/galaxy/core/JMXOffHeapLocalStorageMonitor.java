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
