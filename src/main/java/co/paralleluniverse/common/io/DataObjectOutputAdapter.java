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

import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;

/**
 *
 * @author pron
 */
public class DataObjectOutputAdapter implements ObjectOutput {
    private final DataOutput out;

    public DataObjectOutputAdapter(DataOutput out) {
        this.out = out;
    }
    
    @Override
    public void writeObject(Object obj) throws IOException {
        throw new UnsupportedOperationException("Not supported.");
    }
    
    @Override
    public void close() throws IOException {
        if(out instanceof OutputStream)
            ((OutputStream)out).close();
    }

    @Override
    public void flush() throws IOException {
        if(out instanceof OutputStream)
            ((OutputStream)out).flush();
    }
    
    public void writeUTF(String s) throws IOException {
        out.writeUTF(s);
    }

    public void writeShort(int v) throws IOException {
        out.writeShort(v);
    }

    public void writeLong(long v) throws IOException {
        out.writeLong(v);
    }

    public void writeInt(int v) throws IOException {
        out.writeInt(v);
    }

    public void writeFloat(float v) throws IOException {
        out.writeFloat(v);
    }

    public void writeDouble(double v) throws IOException {
        out.writeDouble(v);
    }

    public void writeChars(String s) throws IOException {
        out.writeChars(s);
    }

    public void writeChar(int v) throws IOException {
        out.writeChar(v);
    }

    public void writeBytes(String s) throws IOException {
        out.writeBytes(s);
    }

    public void writeByte(int v) throws IOException {
        out.writeByte(v);
    }

    public void writeBoolean(boolean v) throws IOException {
        out.writeBoolean(v);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    public void write(int b) throws IOException {
        out.write(b);
    }
    
    
}
