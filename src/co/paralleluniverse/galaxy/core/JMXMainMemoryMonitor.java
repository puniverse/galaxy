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

import co.paralleluniverse.common.monitoring.PeriodicMonitor;
import co.paralleluniverse.galaxy.monitoring.Counter;
import co.paralleluniverse.galaxy.monitoring.MainMemoryMXBean;
import java.beans.ConstructorProperties;

/**
 *
 * @author pron
 */
class JMXMainMemoryMonitor extends PeriodicMonitor implements MainMemoryMonitor, MainMemoryMXBean {
    private final Counter writesCounter = new Counter();
    private final Counter transactionsCounter = new Counter();
    private final Counter objectsServedCounter = new Counter();
    private final Counter ownerWritesCounter = new Counter();
    private final Counter ownersServedCounter = new Counter();
    
    private long writes;
    private long transactions;
    private long objectsServed;
    private long ownerWrites;
    private long ownersServed;

    @ConstructorProperties({"name"})
    public JMXMainMemoryMonitor(String name) {
        super(MainMemoryMXBean.class, "co.paralleluniverse.galaxy.core:type=MainMemory");
    }

    @Override
    protected void collectAndResetCounters() {
        writes = writesCounter.get();
        transactions = transactionsCounter.get();
        objectsServed = objectsServedCounter.get();
        ownerWrites = ownerWritesCounter.get();
        ownersServed = ownersServedCounter.get();
        resetCounters();
    }

    @Override
    protected void resetCounters() {
        writesCounter.reset();
        transactionsCounter.reset();
        objectsServedCounter.reset();
        ownerWritesCounter.reset();
        ownersServedCounter.reset();
    }

    @Override
    public void addTransaction(int numWrites) {
        transactionsCounter.inc();
        writesCounter.add(numWrites);
    }
    
    @Override
    public void addObjectServed() {
        objectsServedCounter.inc();
    }
    
    @Override
    public void addOwnerWrite() {
        ownerWritesCounter.inc();
    }
    
    @Override
    public void addOwnerServed() {
        ownersServedCounter.inc();
    }
    
    @Override
    public int getObjectsServed() {
        return (int)objectsServed;
    }

    @Override
    public int getOwnerWrites() {
        return (int)ownerWrites;
    }

    @Override
    public int getOwnersServed() {
        return (int)ownersServed;
    }

    @Override
    public int getTransactions() {
        return (int)transactions;
    }

    @Override
    public int getWrites() {
        return (int)writes;
    }
}
