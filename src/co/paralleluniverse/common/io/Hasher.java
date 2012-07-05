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
package co.paralleluniverse.common.io;

import com.google.common.primitives.Longs;
import java.nio.ByteBuffer;

/**
 * Based on Numerical Recipes 7.6.1
 *
 * @author pron
 */
class Hasher implements Checksum {
    private static final long START = 0xBB40E64DA205B064L;
    private static final long MULT = 7664345821815920749L;
    private static final long[] TABLE = createLookupTable();
    //
    private long value;

    public Hasher() {
        reset();
    }

    @Override
    public final void reset() {
        value = START;
    }

    @Override
    public byte[] getChecksum() {
        return Longs.toByteArray(value);
    }

    @Override
    public void update(byte b) {
        value = (value * MULT) ^ TABLE[(int)b & 0xff];
    }

    @Override
    public void update(byte[] array) {
        for(byte b : array)
            update(b);
    }

    @Override
    public void update(ByteBuffer buffer) {
        while(buffer.hasRemaining())
            update(buffer.get());
    }

    private static long[] createLookupTable() {
        long[] table = new long[256];
        long h = 0x544B2FBACAAF1684L;
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 31; j++) {
                h = (h >>> 7) ^ h;
                h = (h << 11) ^ h;
                h = (h >>> 10) ^ h;
            }
            table[i] = h;
        }
        return table;
    }
}
