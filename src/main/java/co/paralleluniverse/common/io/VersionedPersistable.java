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
 * A {@link Persistable} that is used by some persistence service that provides automatic versioning.
 */
public interface VersionedPersistable extends Persistable {
    /**
     * Reads the object from the given {@link ByteBuffer}.<br>
     * Upon return from this method, the buffer's {@link ByteBuffer#position() position} will have advanced past the read data.
     * 
     * @param version The version of the object's persisted data.
     * @param buffer The buffer from which the object is to be read.
     */
    void read(long version, ByteBuffer buffer);
}
