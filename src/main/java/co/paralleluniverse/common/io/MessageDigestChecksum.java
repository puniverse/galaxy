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

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author pron
 */
public class MessageDigestChecksum implements Checksum {
    final MessageDigest md;

    public MessageDigestChecksum(String algorithm) {
        try {
            md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void reset() {
        md.reset();
    }

    @Override
    public void update(byte b) {
        md.update(b);
    }

    @Override
    public void update(byte[] array) {
        md.update(array);
    }

    @Override
    public void update(ByteBuffer buffer) {
        md.update(buffer);
    }

    @Override
    public byte[] getChecksum() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
