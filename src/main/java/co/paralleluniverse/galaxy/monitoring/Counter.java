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
package co.paralleluniverse.galaxy.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import org.cliffc.high_scale_lib.ConcurrentAutoTable;

/**
 *
 * @author pron
 */
public class Counter {
    private final ConcurrentAutoTable cat = new ConcurrentAutoTable();
    //private final AtomicLong al = new AtomicLong();

    public void reset() {
        cat.set(0);
        //al.set(0);
    }

    public void inc() {
        cat.increment();
        //al.incrementAndGet();
    }

    public void add(long val) {
        cat.add(val);
        //al.addAndGet(val);
    }

    public long get() {
        return cat.get();
        //return al.get();
    }
}
