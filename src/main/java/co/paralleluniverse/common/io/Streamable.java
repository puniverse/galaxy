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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * This interface marks an object that can be serialized to a data stream. It is similar in function to {@link java.io.Serializable},
 * except that the serialization is handled explicitely by the object.
 */
public interface Streamable {
    /**
     * Returns the size in bytes of the object's serialized form.
     * @return The size in bytes of the object's serialized form.
     */
    int size();

    /**
     * Serializes the object into the given {@link DataOutput},
     * @param out A {@link DataOutput} into which the object is to be serialized.
     * @throws IOException 
     */
    void write(DataOutput out) throws IOException;

    /**
     * Reads the object's contents from the given {@link DataInput}.
     * @param in A {@link DataInput} from which the object's contents are to be read.
     * @throws IOException 
     */
    void read(DataInput in) throws IOException;
}
