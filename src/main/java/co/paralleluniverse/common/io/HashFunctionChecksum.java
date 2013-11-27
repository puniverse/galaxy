/*
 * Galaxy
 * Copyright (C) 2012-2013 Parallel Universe Software Co.
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

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import java.nio.ByteBuffer;

/**
 *
 * @author pron
 */
public class HashFunctionChecksum implements Checksum {
    private final HashFunction hf;
    private Hasher hasher;

    public HashFunctionChecksum(HashFunction hf) {
        this.hf = hf;
        reset();
    }

    @Override
    public void reset() {
        this.hasher = hf.newHasher();
    }

    @Override
    public void update(byte b) {
        hasher.putByte(b);
    }

    @Override
    public void update(byte[] array) {
        hasher.putBytes(array);
    }

    @Override
    public void update(ByteBuffer buffer) {
        hasher.putBytes(ByteBufferUtil.toByteArray(buffer));
    }

    @Override
    public byte[] getChecksum() {
        return hasher.hash().asBytes();
    }
}
