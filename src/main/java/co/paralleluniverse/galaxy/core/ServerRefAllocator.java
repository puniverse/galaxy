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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import co.paralleluniverse.galaxy.core.Message.ALLOCED_REF;
import java.util.Collection;

/**
 *
 * @author pron
 */
public class ServerRefAllocator implements RefAllocator, MessageReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(ServerRefAllocator.class);
    private final Comm comm;
    private final RefAllocatorSupport support = new RefAllocatorSupport();

    public ServerRefAllocator(Comm comm) {
        this.comm = comm;
        support.fireCounterReady();
    }

    @Override
    public void allocateRefs(int count) {
        try {
            comm.send(Message.ALLOC_REF(Comm.SERVER, count));
        } catch (NodeNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void receive(Message message) {
        if (message instanceof ALLOCED_REF) {
            final ALLOCED_REF m = (ALLOCED_REF) message;
            support.fireRefsAllocated(m.getStart(), m.getNum());
        } else {
            LOG.error("Unexpected message: {}", message);
            throw new AssertionError();
        }
    }

    @Override
    public void addRefAllocationsListener(RefAllocationsListener listener) {
        listener.counterReady();
        support.addRefAllocationsListener(listener);
    }

    @Override
    public void removeRefAllocationsListener(RefAllocationsListener listener) {
        support.removeRefAllocationsListener(listener);
    }

    @Override
    public Collection<RefAllocationsListener> getRefAllocationsListeners() {
        return support.getRefAllocationListeners();
    }
}
