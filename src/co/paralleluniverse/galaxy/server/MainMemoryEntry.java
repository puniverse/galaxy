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
