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

import co.paralleluniverse.common.MonitoringType;
import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.common.util.DegenerateInvocationHandler;
import java.beans.ConstructorProperties;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author pron
 */
class HeapLocalStorage extends Component implements CacheStorage {
    private final AtomicLong totalSize = new AtomicLong();
    private final LocalStorageMonitor monitor;

    @ConstructorProperties({"name", "monitoringType"})
    public HeapLocalStorage(String name, MonitoringType monitoringType) {
        super(name);
        this.monitor = createMonitor(monitoringType, name);
    }
    @Override
    public ByteBuffer allocateStorage(int size) {
        monitor.allocated(size);
        totalSize.addAndGet(size);
        return ByteBuffer.allocate(size);
    }

    @Override
    public void deallocateStorage(long id, ByteBuffer buffer) {
        totalSize.addAndGet(-buffer.capacity());
        monitor.deallocated(buffer.capacity());
    }

    @Override
    public long getTotalAllocatedSize() {
        return totalSize.get();
    }

    private LocalStorageMonitor createMonitor(MonitoringType monitoringType, String name) {
        if (monitoringType == null)
            return (LocalStorageMonitor) Proxy.newProxyInstance(HeapLocalStorage.class.getClassLoader(), new Class<?>[]{LocalStorageMonitor.class}, DegenerateInvocationHandler.INSTANCE);
        else
            switch (monitoringType) {
                case JMX:
                    return new JMXLocalStorageMonitor(name, this);
                case METRICS:
                    return new MetricsLocalStorageMonitor(name, this);
            }
        throw new IllegalArgumentException("Unknown MonitoringType " + monitoringType);
    }
}
