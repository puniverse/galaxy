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

/**
 * This interface marks an object that can be persisted to a {@link ByteBuffer}.
 */
public interface Persistable {
    /**
     * Returns the size in bytes of the object's persisted form.
     * @return The size in bytes of the object's persisted form.
     */
    int size();

    /**
     * Writes the object to the given {@link ByteBuffer}.<br>
     * Upon return from this method, the buffer's {@link ByteBuffer#position() position} will have advanced past the written data.
     * @param buffer The buffer to which the object is to be written.
     */
    void write(ByteBuffer buffer);

    /**
     * Reads the object from the given {@link ByteBuffer}.<br>
     * Upon return from this method, the buffer's {@link ByteBuffer#position() position} will have advanced past the read data.
     * @param buffer The buffer from which the object is to be read.
     */
    void read(ByteBuffer buffer);
}
