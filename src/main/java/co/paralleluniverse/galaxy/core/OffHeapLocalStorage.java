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

import co.paralleluniverse.common.MonitoringType;
import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.common.util.DegenerateInvocationHandler;
import java.beans.ConstructorProperties;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Off-heap (direct ByteBuffer) allocator.
 *
 * @author pron
 */
class OffHeapLocalStorage extends Component implements CacheStorage {
    private static final Logger LOG = LoggerFactory.getLogger(OffHeapLocalStorage.class);
    private static final int MIN_POWER = 3; // min size = 1 << 3 = 8
    private static final Field VIEWD_BUFFER_FIELD;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0);

    static {
        try {
            VIEWD_BUFFER_FIELD = Class.forName("java.nio.DirectByteBuffer").getDeclaredField("att"); // "viewedBuffer" in JDK6
            VIEWD_BUFFER_FIELD.setAccessible(true);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
    private final int pageSize; // in KBs
    private final int maxItemSize; // in bytes
    private final AtomicLong totalSize = new AtomicLong();
    private final PageGroup[] pageGroups;
    private int maxPagesForConcurrency = Runtime.getRuntime().availableProcessors() * 2;
    private final OffHeapLocalStorageMonitor monitor;

    @ConstructorProperties({"name", "pageSize", "maxItemSize", "monitoringType"})
    public OffHeapLocalStorage(String name, int pageSize, int maxItemSize, MonitoringType monitoringType) {
        super(name);
        this.pageSize = pageSize;
        this.maxItemSize = nextPowerOfTwo(maxItemSize);

        int numGroups = 0;
        int tmpSize = 1 << MIN_POWER;
        while (tmpSize <= this.maxItemSize) {
            numGroups++;
            tmpSize <<= 1;
        }
        int[] sizes = new int[numGroups];
        this.pageGroups = new PageGroup[numGroups];
        for (int i = 0; i < pageGroups.length; i++) {
            final int size = 1 << (MIN_POWER + i);
            pageGroups[i] = new PageGroup(i, size);
            sizes[i] = size;
        }

        this.monitor = createMonitor(monitoringType, name, sizes);
    }

    private OffHeapLocalStorageMonitor createMonitor(MonitoringType monitoringType, String name, int[] sizes) {
        if (monitoringType == null)
            return (OffHeapLocalStorageMonitor) Proxy.newProxyInstance(OffHeapLocalStorage.class.getClassLoader(), new Class<?>[]{OffHeapLocalStorageMonitor.class}, DegenerateInvocationHandler.INSTANCE);
        else
            switch (monitoringType) {
                case JMX:
                    return new JMXOffHeapLocalStorageMonitor(name, this, sizes);
                case METRICS:
                    return new MetricsOffHeapLocalStorageMonitor(name, this, sizes);
            }
        throw new IllegalArgumentException("Unknown MonitoringType " + monitoringType);
    }

    public void setMaxPagesForConcurrency(int maxPagesForConcurrency) {
        assertDuringInitialization();
        this.maxPagesForConcurrency = maxPagesForConcurrency;
    }

    @Override
    public ByteBuffer allocateStorage(int size) {
        if (size == 0)
            return EMPTY_BUFFER;
        if (size > maxItemSize)
            throw new IllegalArgumentException("Size " + size + " is larger than maximum size: " + maxItemSize);
        final int bin = getSizeIndex(size);
        size = pageGroups[bin].cellSize;
        monitor.allocated(bin, size);
        totalSize.addAndGet(size);
        final ByteBuffer buffer = pageGroups[bin].allocate();
        buffer.position(0);
        return buffer;
    }

    @Override
    public void deallocateStorage(long id, ByteBuffer buffer) {
        if (buffer == EMPTY_BUFFER)
            return;
        final Page page = getPage(buffer);
        monitor.deallocated(page.getGroup().groupIndex, buffer.limit());
        totalSize.addAndGet(-buffer.limit());
        page.deallocate(buffer);
    }

    @Override
    public long getTotalAllocatedSize() {
        return totalSize.get();
    }

    private class PageGroup {
        public final int groupIndex;
        public final int cellSize;
        private final List<Page> pages = new CopyOnWriteArrayList<Page>();
        private final Lock allocationLock = new ReentrantLock();
        private volatile int numPages = 0;

        public PageGroup(int index, int cellSize) {
            this.groupIndex = index;
            this.cellSize = cellSize;
        }

        ByteBuffer allocate() {
            final int threadHash = Thread.currentThread().hashCode();
            ByteBuffer buffer = null;
            if (numPages > 0)
                buffer = allocate(threadHash % numPages);
            if (buffer == null) {
                allocationLock.lock();
                try {
                    if (numPages > 0)
                        buffer = allocate(threadHash % numPages);
                    if (buffer == null) {
                        if (LOG.isDebugEnabled())
                            LOG.debug("Allocating a direct-memory page of size {} bytes. (totalSize: {} bytes)", pageSize * 1024, totalSize.get());
                        Page newPage = new Page(this, pageSize, cellSize, MIN_POWER + groupIndex);
                        buffer = newPage.allocate(true);
                        pages.add(newPage);
                        numPages++;
                    }
                } finally {
                    allocationLock.unlock();
                }
            }
            assert buffer != null;
            return buffer;
        }

        private ByteBuffer allocate(int start) {
            final int _numPages = numPages; // read at the beginning 'cause this may change
            boolean canGrowForConcurrency = _numPages < maxPagesForConcurrency;
            for (int i = 0; i < _numPages; i++) {
                final Page page = pages.get((start + i) % _numPages);
                final ByteBuffer buffer = page.allocate(!canGrowForConcurrency);
                if (buffer != null)
                    return buffer;
            }
            return null;
        }
    }

    private static class Page {
        private final PageGroup group;
        private final int cellSize; // in bytes
        private final ByteBuffer buffer;
        private int head;
        private int freeCells;
        private final Lock lock = new ReentrantLock();

        public Page(PageGroup group, int bufferKbSize, int cellSize, int power) {
            this.group = group;
            buffer = ByteBuffer.allocateDirect(bufferKbSize * 1024);
            buffer.order(ByteOrder.nativeOrder());
            setViewed(buffer, this);
            this.cellSize = cellSize;

            this.freeCells = (bufferKbSize * 1024) >> power;

            // initialize free-list
            int prev = -1;
            for (int i = freeCells - 1; i >= 0; i--) {
                final int ptr = i << power;
                buffer.putInt(ptr, prev);
                prev = ptr;
            }
            this.head = 0;
        }

        public PageGroup getGroup() {
            return group;
        }

        ByteBuffer allocate(boolean doIt) {
            if (doIt)
                lock.lock();
            else if (!lock.tryLock())
                return null; // contention - return null and allocate a new page to reduce contention
            final int ptr;
            final ByteBuffer slice;
            try {
                ptr = head;
                if (ptr == -1) {
                    assert freeCells == 0;
                    return null;
                }
                head = buffer.getInt(ptr);
                freeCells--;
                slice = slice(buffer, ptr, cellSize); // we must slice inside the lock b/c slice modifies buffer's fields
            } finally {
                lock.unlock();
            }
            slice.putInt(0, 0);
            return slice;
        }

        void deallocate(ByteBuffer slice) {
            assert getPage(slice) == this;
            final int ptr = getOffset(slice);

            lock.lock();
            try {
                buffer.putInt(ptr, head);
                head = ptr;
                freeCells++;
            } finally {
                lock.unlock();
            }
        }
    }

    private int getSizeIndex(int size) {
        for (int i = 0; i < pageGroups.length; i++) {
            if (size <= pageGroups[i].cellSize)
                return i;
        }
        throw new RuntimeException("Value " + size + " is too large! Must be smaller than " + pageGroups[pageGroups.length - 1].cellSize);
    }

    // taken from http://graphics.stanford.edu/~seander/bithacks.html#RoundUpPowerOf2
    private static int nextPowerOfTwo(int v) {
        assert v >= 0;
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }

    private static ByteBuffer slice(ByteBuffer buffer, int start, int length) {
        buffer.limit(start + length);
        buffer.position(start);
        final ByteBuffer slice = buffer.slice();
        buffer.clear();
        return slice;
    }

    private static Page getPage(ByteBuffer buffer) {
        return (Page) getViewed((ByteBuffer) getViewed(buffer));
    }

    private static Object getViewed(ByteBuffer buffer) {
        return ((sun.nio.ch.DirectBuffer) buffer).attachment(); // java<7: viewedBuffer();
    }

    private static int getOffset(ByteBuffer slice) {
        final sun.nio.ch.DirectBuffer _slice = (sun.nio.ch.DirectBuffer) slice;
        final sun.nio.ch.DirectBuffer parent = (sun.nio.ch.DirectBuffer) _slice.attachment(); // java<7: viewedBuffer();
        return (int) (_slice.address() - parent.address());
    }

    private static void setViewed(ByteBuffer buffer, Object object) {
        try {
            VIEWD_BUFFER_FIELD.set(buffer, object);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
