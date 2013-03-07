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
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
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
        allocated = Metrics.newHistogram(CacheStorage.class, "allocated", name, true);
        deallocated = Metrics.newHistogram(CacheStorage.class, "deallocated", name, true);
        final WeakReference<CacheStorage> _localStorage = new WeakReference<CacheStorage>(localStorage);
        totalSize = Metrics.newGauge(CacheStorage.class, "totalSize", name, new Gauge<Long>() {
            @Override
            public Long value() {
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
