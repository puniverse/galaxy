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

import co.paralleluniverse.common.io.Persistable;
import co.paralleluniverse.common.io.Streamables;
import static co.paralleluniverse.common.logging.LoggingUtils.hex;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.TimeoutException;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
class StringRootManager {
    private static final Logger LOG = LoggerFactory.getLogger(StringRootManager.class);
    private final StoreImpl store;
    private final Cluster cluster;
    private final RootLocker rootLocker;

    public StringRootManager(StoreImpl store, Cluster cluster) {
        this.store = store;
        this.cluster = cluster;
        this.rootLocker = (RootLocker) cluster;
    }

    public long get(String root, Transaction txn) throws TimeoutException {
        return new StringRootPageHandler(root).find(txn, -1);
    }

    public long get(String root, long ref, Transaction txn) throws TimeoutException {
        return new StringRootPageHandler(root).find(txn, ref);
    }

    private class StringRootPageHandler implements Persistable {
        private final String str;
        private short size;
        private long ref;
        private long result;

        public StringRootPageHandler(String str) {
            this.str = str;
        }

        public long find(Transaction txn, long rootRef) throws TimeoutException {
            ref = str.hashCode();
            result = -1;

            if (LOG.isDebugEnabled())
                LOG.debug("Base is {}", hex(ref));
            initialGet();
            while (result < 0 && ref >= 0)
                store.get1(ref, this);
            if (result >= 0)
                return result;

            // start over!
            if (LOG.isDebugEnabled())
                LOG.debug("Root for {} not found. Retrying.", str);

            final short mySize = (short) new Entry(str, -1).size();
            ref = str.hashCode();

            long prevRef = -1;

            while (ref != -1) {
                final long curRef = this.ref;
                if (LOG.isDebugEnabled())
                    LOG.debug("getx {}.", hex(ref));
                
                try {
                    store.getx1(curRef, this, null); // now this.ref is pointing to nextRef.
                } finally {
                    if (prevRef >= 0) {
                        store.release(prevRef);
                    }
                }
                
                if (result >= 0) {
                    store.release(curRef);
                    return result;
                } else if (this.size + mySize <= store.getMaxItemSize()) {
                    final long myRef = rootRef > 0 ? rootRef : store.put(new byte[0], null);
                    if (LOG.isDebugEnabled())
                        LOG.debug("New root for {} is {}. Writing into base {}.", new Object[]{str, hex(myRef), hex(curRef)});
                    final StringRootPage page = new StringRootPage();
                    store.getx1(curRef, page, null);
                    page.put(str, myRef);
                    store.set1(curRef, page, null);
                    store.release(curRef);
                    txn.add(myRef);
                    return myRef;
                }
                prevRef = curRef;
            }

            // no space!
            final long myRef = rootRef > 0 ? rootRef : store.put(new byte[0], null);
            final StringRootPage page = new StringRootPage();
            page.put(str, myRef);
            this.ref = store.put(page, null);
            if (LOG.isDebugEnabled())
                LOG.debug("New root for {} is {}. Writing into new base {}.", new Object[]{str, hex(myRef), hex(this.ref)});
            if (prevRef >= 0) {
                store.set1(prevRef, this, null); // writes only nextRef = this.ref NOTE: We assume the buffer is reused!
                store.release(prevRef);
            }
            store.release(ref);
            txn.add(myRef);
            return myRef;
        }

        private void initialGet() throws TimeoutException {
            if (cluster.hasServer()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Getting base ({}) from server.", hex(ref));
                store.get1(ref, Comm.SERVER, this);
            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("Locking base ({}) and broadcasting GET.", hex(ref));
                Object lock = rootLocker.lockRoot((int) ref);
                try {
                    store.get1(ref, this);
                } finally {
                    rootLocker.unlockRoot(lock);
                }
            }

        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void read(ByteBuffer buffer) {
            if (buffer == null || buffer.remaining() < 10) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Buffer {} is empty.", hex(ref));
                this.ref = -1; // look no further
                this.size = 0;
                return;
            }

            final long nextRef = buffer.getLong();
            if (LOG.isDebugEnabled())
                LOG.debug("Next is {}.", hex(nextRef));
            this.ref = nextRef;
            this.size = (short) buffer.limit();

            final short numEntries = buffer.getShort();
            for (int i = 0; i < numEntries; i++) {
                final Entry entry = new Entry();
                entry.read(buffer);
                if (entry.str.equals(this.str)) {
                    result = entry.ref;
                    if (LOG.isDebugEnabled())
                        LOG.debug("Found root for {}: {}", str, hex(result));
                    return;
                }
            }
        }

        @Override
        public void write(ByteBuffer buffer) {
            buffer.putLong(0, ref);
            buffer.position(buffer.limit()); // make sure that when we flip we don't discard the rest
        }
    }

    private static class StringRootPage implements Persistable {
        private final ArrayList<Entry> entries = new ArrayList<Entry>();
        private long nextRef = -1;

        public synchronized long get(String str) {
            final int index = Collections.binarySearch(entries, new Entry(str, -1));
            if (index >= 0)
                return entries.get(index).ref;
            else
                return -1;
        }

        public synchronized void put(String str, long ref) {
            final Entry entry = new Entry(str, ref);
            final int index = Collections.binarySearch(entries, entry);
            if (index < 0)
                entries.add(-index - 1, entry);
            else
                assert entries.get(index).ref == ref;
        }

        public synchronized void setNextRef(long ref) {
            this.nextRef = ref;
        }

        @Override
        public int size() {
            int size = 2 + 8;
            for (Entry entry : entries)
                size += entry.size();
            return size;
        }

        @Override
        public synchronized void write(ByteBuffer buffer) {
            buffer.putLong(nextRef);
            buffer.putShort((short) entries.size());
            for (Entry entry : entries)
                entry.write(buffer);
        }

        @Override
        public synchronized void read(ByteBuffer buffer) {
            if (buffer == null || buffer.remaining() < 10) {
                nextRef = -1;
                return;
            }
            nextRef = buffer.getLong();
            final short numEntries = buffer.getShort();
            entries.ensureCapacity(numEntries);
            for (int i = 0; i < numEntries; i++) {
                final Entry entry = new Entry();
                entry.read(buffer);
                entries.add(entry);
            }
        }
    }

    private static class Entry implements Comparable<Entry>, Persistable {
        String str;
        long ref;

        public Entry(String str, long ref) {
            this.str = str;
            this.ref = ref;
        }

        public Entry() {
            this(null, -1);
        }

        @Override
        public int compareTo(Entry o) {
            return str.compareTo(o.str);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final Entry other = (Entry) obj;
            if (!Objects.equals(this.str, other.str))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            return str.hashCode();
        }

        @Override
        public int size() {
            return 2 + Streamables.calcUtfLength(str) + 8;
        }

        @Override
        public void write(ByteBuffer buffer) {
            final byte[] chars = str.getBytes(Charsets.UTF_8);
            buffer.putShort((short) chars.length);
            buffer.put(chars);
            buffer.putLong(ref);
        }

        @Override
        public void read(ByteBuffer buffer) {
            final short length = buffer.getShort();
            final byte[] chars = new byte[length];
            buffer.get(chars);
            str = new String(chars, Charsets.UTF_8);
            ref = buffer.getLong();
        }
    }
}
