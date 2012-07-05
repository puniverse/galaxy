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

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Meter;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
class MetricsOffHeapLocalStorageMonitor extends MetricsLocalStorageMonitor implements OffHeapLocalStorageMonitor {
    private final BinMetrics[] binMetrics;

    public MetricsOffHeapLocalStorageMonitor(String name, CacheStorage storage, int[] bins) {
        super(name, storage);
        this.binMetrics = new BinMetrics[bins.length];
        for (int i = 0; i < bins.length; i++)
            this.binMetrics[i] = new BinMetrics(name, bins[i]);
    }

    @Override
    public void allocated(int bin, int size) {
        super.allocated(size);
        binMetrics[bin].allocated(size);
    }

    @Override
    public void deallocated(int bin, int size) {
        super.deallocated(size);
        binMetrics[bin].deallocated(size);
    }

    private static class BinMetrics {
        private final Counter totalSize;
        private final Meter allocated;
        private final Meter deallocated;

        public BinMetrics(String name, int bin) {
            totalSize = Metrics.newCounter(CacheStorage.class, "totalSize", name + '[' + bin + ']');
            allocated = Metrics.newMeter(CacheStorage.class, "allocated", name + '[' + bin + ']', "allocation", TimeUnit.SECONDS);
            deallocated = Metrics.newMeter(CacheStorage.class, "deallocated", name + '[' + bin + ']', "deallocation", TimeUnit.SECONDS);
//            allocated = Metrics.newHistogram(CacheStorage.class, "allocated", name + '[' + bin + ']', true);
//            deallocated = Metrics.newHistogram(CacheStorage.class, "deallocated", name + '[' + bin + ']', true);
        }

        public void allocated(int size) {
            totalSize.inc(size);
            allocated.mark();
        }

        public void deallocated(int size) {
            totalSize.dec(size);
            deallocated.mark();
        }
    }
}
