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
import com.yammer.metrics.core.Meter;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
class MetricsMainMemoryMonitor implements MainMemoryMonitor {
    private final Meter writes = Metrics.newMeter(MainMemory.class, "writes", "writes", TimeUnit.SECONDS);
    private final Meter transactions = Metrics.newMeter(MainMemory.class, "transactions", "transactions", TimeUnit.SECONDS);
    private final Meter objectsServed = Metrics.newMeter(MainMemory.class, "objectsServed", "objectsServed", TimeUnit.SECONDS);
    private final Meter ownerWrites = Metrics.newMeter(MainMemory.class, "ownerWrites", "ownerWrites", TimeUnit.SECONDS);
    private final Meter ownersServed = Metrics.newMeter(MainMemory.class, "ownersServed", "ownersServed", TimeUnit.SECONDS);
    
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
