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

import co.paralleluniverse.common.monitoring.Metrics;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import static com.codahale.metrics.MetricRegistry.name;

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
            
            totalSize = Metrics.counter(name("co.paralleluniverse", "galaxy", "CacheStorage", "totalSize", name + '[' + bin + ']'));
            allocated = Metrics.meter(name("co.paralleluniverse", "galaxy", "CacheStorage", "allocated", name + '[' + bin + ']'));
            deallocated = Metrics.meter(name("co.paralleluniverse", "galaxy", "CacheStorage", "deallocated", name + '[' + bin + ']'));
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
