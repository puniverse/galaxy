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
