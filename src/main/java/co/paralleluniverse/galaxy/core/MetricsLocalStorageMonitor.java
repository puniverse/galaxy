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
