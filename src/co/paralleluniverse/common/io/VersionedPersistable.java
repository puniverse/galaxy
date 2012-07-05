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
 * A {@link Persistable} that is used by some persistence service that provides automatic versioning.
 */
public interface VersionedPersistable extends Persistable {
    /**
     * Reads the object from the given {@link ByteBuffer}.<br/>
     * Upon return from this method, the buffer's {@link ByteBuffer#position() position} will have advanced past the read data.
     * 
     * @param version The version of the object's persisted data.
     * @param buffer The buffer from which the object is to be read.
     */
    void read(long version, ByteBuffer buffer);
}
