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
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import static com.codahale.metrics.MetricRegistry.name;
import java.lang.ref.WeakReference;

/**
 *
 * @author pron
 */
class MetricsLocalStorageMonitor implements LocalStorageMonitor {
    final Histogram allocated;
    final Histogram deallocated;
    final Gauge<Long> totalSize;
    //final Counter totalSize;

    public MetricsLocalStorageMonitor(String name, CacheStorage localStorage) {

        allocated = Metrics.histogram(name("co.paralleluniverse", "galaxy", "CacheStorage", "allocated", name));
        deallocated = Metrics.histogram(name("co.paralleluniverse", "galaxy", "CacheStorage", "deallocated", name));
        final WeakReference<CacheStorage> _localStorage = new WeakReference<CacheStorage>(localStorage);
        totalSize = Metrics.register(name("co.paralleluniverse", "galaxy", "CacheStorage", "totalSize", name),
                new Gauge<Long>() {
                    @Override
                    public Long getValue() {
                        final CacheStorage localStorage = _localStorage.get();
                        return localStorage != null ? localStorage.getTotalAllocatedSize() : 0L;
                    }
                });
        //totalSize = Metrics.newCounter(CacheStorage.class, "totalSize", name);
    }

    @Override
    public void allocated(int size) {
        //totalSize.inc(size);
        allocated.update(size);
    }

    @Override
    public void deallocated(int size) {
        //totalSize.dec(size);
        deallocated.update(size);
    }
}
