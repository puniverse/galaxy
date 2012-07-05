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

import java.nio.ByteBuffer;

/**
 *
 * @author pron
 */
public class DoubleHasher implements Checksum {
    private final Hasher hasher1 = new Hasher();
    private final Hasher hasher2 = new Hasher();
    private int counter = 0;

    @Override
    public void reset() {
        counter = 0;
        hasher1.reset();
        hasher2.reset();
    }

    @Override
    public void update(byte b) {
        hasher1.update(b);
        if (counter % 2 == 0)
            hasher2.update(b);
        counter++;
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
    
    @Override
    public byte[] getChecksum() {
        final byte[] array = new byte[16];
        System.arraycopy(hasher1.getChecksum(), 0, array, 0, 8);
        System.arraycopy(hasher2.getChecksum(), 0, array, 8, 8);
        return array;
    }
}
