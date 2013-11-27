/*
 * Copyright (C) 2013, Parallel Universe Software Co. All rights reserved.
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

/**
 *
 * @author pron
 */
public final class ByteBufferUtil {
    public static byte[] toByteArray(ByteBuffer bb) {
        if (bb.hasArray() && bb.arrayOffset() == 0 && bb.position() == 0) {
            return bb.array();
        } else {
            byte[] arr = new byte[bb.remaining()];
            int p = bb.position();
            bb.get(arr);
            bb.position(p);
            return arr;
        }
    }

    private ByteBufferUtil() {
    }
}
