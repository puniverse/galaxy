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
