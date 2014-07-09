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
    private final Counter allocationCounter = new Counter();
    
    private long writes;
    private long transactions;
    private long objectsServed;
    private long ownerWrites;
    private long ownersServed;
    private long allocations;

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
        allocations = allocationCounter.get();
        resetCounters();
    }

    @Override
    protected void resetCounters() {
        writesCounter.reset();
        transactionsCounter.reset();
        objectsServedCounter.reset();
        ownerWritesCounter.reset();
        ownersServedCounter.reset();
        allocationCounter.reset();
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
    public void addAllocation(int count) {
        allocationCounter.add(count);
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

    @Override
    public int getAllocations() {
        return (int)allocations;
    }
}
