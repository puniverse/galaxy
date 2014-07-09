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

import co.paralleluniverse.galaxy.core.RefAllocator.RefAllocationsListener;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author pron
 */
public class RefAllocatorSupport {
    private final List<RefAllocationsListener> refAllocationListeners = new CopyOnWriteArrayList<RefAllocationsListener>();

    public void addRefAllocationsListener(RefAllocationsListener listener) {
        refAllocationListeners.add(listener);
    }

    public void removeRefAllocationsListener(RefAllocationsListener listener) {
        refAllocationListeners.remove(listener);
    }

    public Collection<RefAllocationsListener> getRefAllocationListeners() {
        return refAllocationListeners;
    }
    
    public void fireCounterReady() {
        for (RefAllocationsListener listener : refAllocationListeners)
            listener.counterReady();
    }

    public void fireRefsAllocated(long start, int num) {
        for (RefAllocationsListener listener : refAllocationListeners)
            listener.refsAllocated(start, num);
    }
}
