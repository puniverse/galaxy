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
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

/**
 *
 * @author pron
 */
class MetricsMainMemoryMonitor implements MainMemoryMonitor {
    private final Meter writes = Metrics.meter(metric("writes"));
    private final Meter transactions = Metrics.meter(metric("transactions"));
    private final Meter objectsServed = Metrics.meter(metric("objectsServed"));
    private final Meter ownerWrites = Metrics.meter(metric("ownerWrites"));
    private final Meter ownersServed = Metrics.meter(metric("ownersServed"));
    private final Meter allocations = Metrics.meter(metric("allocations"));

    protected final String metric(String name) {
        return MetricRegistry.name("co.paralleluniverse", "galaxy", "MainMemory", name);
    }

    @Override
    public void setMonitoredObject(Object obj) {
    }

    @Override
    public void addObjectServed() {
        objectsServed.mark();
    }

    @Override
    public void addOwnerServed() {
        ownersServed.mark();
    }

    @Override
    public void addOwnerWrite() {
        ownerWrites.mark();
    }

    @Override
    public void addTransaction(int numWrites) {
        transactions.mark();
        writes.mark(numWrites);
    }

    @Override
    public void addAllocation(int count) {
        allocations.mark(count);
    }
}
