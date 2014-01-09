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

import co.paralleluniverse.common.monitoring.PeriodicMonitor;
import co.paralleluniverse.galaxy.monitoring.Counter;
import co.paralleluniverse.galaxy.monitoring.LocalStorageMXBean;
import java.lang.ref.WeakReference;

/**
 *
 * @author pron
 */
class JMXLocalStorageMonitor extends PeriodicMonitor implements LocalStorageMonitor, LocalStorageMXBean {
    private final WeakReference<CacheStorage> localStorage;
    //private final AtomicLong totalSize = new AtomicLong();
    private final Counter allocatedCounter = new Counter();
    private final Counter deallocatedCounter = new Counter();
    private int allocated;
    private int deallocated;
    
    public JMXLocalStorageMonitor(String name, CacheStorage localStorage) {
        this(LocalStorageMXBean.class, name, localStorage);
    }

    protected JMXLocalStorageMonitor(Class mbean, String name, CacheStorage localStorage) {
        super(mbean, "co.paralleluniverse.galaxy.core:type=LocalStorage");
        this.localStorage = new WeakReference<CacheStorage>(localStorage);
        setMonitoredObject(localStorage);
    }
    
    @Override
    protected void initCounters() {
    }

    @Override
    protected void collectAndResetCounters() {
        allocated = (int)allocatedCounter.get();
        deallocated = (int)deallocatedCounter.get();
        
        allocatedCounter.reset();
        deallocatedCounter.reset();
    }

    @Override
    protected void resetCounters() {
        allocatedCounter.reset();
        deallocatedCounter.reset();
    }

    @Override
    public void allocated(int size) {
        //totalSize.addAndGet(size);
        allocatedCounter.add(size);
    }

    @Override
    public void deallocated(int size) {
        //totalSize.addAndGet(-size);
        deallocatedCounter.add(size);
    }

    @Override
    public int getAllocated() {
        return allocated;
    }

    @Override
    public int getDeallocated() {
        return deallocated;
    }

    @Override
    public long getTotalMemory() {
        final CacheStorage _localStorage = localStorage.get();
        return _localStorage != null ? _localStorage.getTotalAllocatedSize() : 0L;
        //return totalSize.get();
    }
}
