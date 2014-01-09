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
package co.paralleluniverse.galaxy.server;

import co.paralleluniverse.galaxy.berkeleydb.BerkeleyDB;
import com.google.common.primitives.Longs;

/**
 *
 * @author pron
 */
public class MainMemoryEntry {
    public final long version;
    public final byte[] data;

    public MainMemoryEntry(long version, byte[] data) {
        this.version = version;
        this.data = data;
    }

    public MainMemoryEntry(byte[] buffer) {
        this.version = Longs.fromByteArray(buffer);
        this.data = new byte[buffer.length - (Longs.BYTES)];
        System.arraycopy(buffer, Longs.BYTES, data, 0, data.length);
    }

    public byte[] toByteArray() {
        final byte[] buffer = new byte[Longs.BYTES + (data != null ? data.length : 0)];
        BerkeleyDB.toByteArray(version, buffer);
        if (data != null)
            System.arraycopy(data, 0, buffer, Longs.BYTES, data.length);
        return buffer;
    }
}
