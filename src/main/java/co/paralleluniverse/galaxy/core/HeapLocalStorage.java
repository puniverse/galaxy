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
