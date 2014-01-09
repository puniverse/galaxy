/*
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
