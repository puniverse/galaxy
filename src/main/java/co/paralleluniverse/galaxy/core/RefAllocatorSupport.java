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

import co.paralleluniverse.galaxy.core.RefAllocator.RefAllocationsListener;
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

    public void fireCounterReady() {
        for (RefAllocationsListener listener : refAllocationListeners)
            listener.counterReady();
    }

    public void fireRefsAllocated(long start, int num) {
        for (RefAllocationsListener listener : refAllocationListeners)
            listener.refsAllocated(start, num);
    }

}
