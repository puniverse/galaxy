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
