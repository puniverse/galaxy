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
}
