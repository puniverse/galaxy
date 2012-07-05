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

import co.paralleluniverse.common.MonitoringType;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author pron
 */
public class OffHeapLocalStorageLoad implements Runnable {
    private static final int NUM_THREADS = 3;
    private static final int NUM_BUFFERS = 1000;
    private static final int MAX_POWER_TWO = 10;
    private static final OffHeapLocalStorage storage = new OffHeapLocalStorage("storage", 1 << (MAX_POWER_TWO - 10 + 2), 1 << MAX_POWER_TWO, MonitoringType.METRICS);

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < NUM_THREADS; i++)
            new Thread(new OffHeapLocalStorageLoad()).start();
        Thread.sleep(Long.MAX_VALUE);
    }
    private long totalSize;
    private final ByteBuffer[] buffers = new ByteBuffer[NUM_BUFFERS];

    @Override
    public void run() {
        System.out.println(thread() + " Starting thread");
        System.out.println(thread() + " Initial allocation...");
        for (int i = 0; i < buffers.length; i++) {
            final int size = randSize();
            //System.out.println("Allocating: " + size);
            buffers[i] = storage.allocateStorage(size);
            totalSize += buffers[i].limit();
            //System.out.println("Total size: " + totalSize);
        }
        System.out.println(thread() +  " Total size: " + totalSize);
        System.out.println(thread() +  " Done with initial allocation.");

        long counter = 0;
        for (;;) {
            final int i = random(0, buffers.length);
            totalSize -= buffers[i].limit();
            //System.out.println(thread() + " Dellocating: " + buffers[i].limit());
            storage.deallocateStorage(-1, buffers[i]);
            int size = randSize();
            //System.out.println(thread() + " Allocating: " + size);
            buffers[i] = storage.allocateStorage(size);
            totalSize += buffers[i].limit();
            if (counter % 10000 == 0)
                System.out.println(thread() + " " + counter + " Total size: " + totalSize);
            counter++;
        }
    }

    private String thread() {
        return Thread.currentThread().getName();
    }

    private int random(int least, int bound) {
        return ThreadLocalRandom.current().nextInt(least, bound);
    }

    private int randSize() {
        int n = 1 << ThreadLocalRandom.current().nextInt(0, MAX_POWER_TWO);
        int s = n + ThreadLocalRandom.current().nextInt(0, n);
        return s;
    }
}
