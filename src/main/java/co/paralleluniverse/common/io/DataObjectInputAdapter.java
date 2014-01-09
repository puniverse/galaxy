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
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;

/**
 *
 * @author pron
 */
public class DataObjectInputAdapter implements ObjectInput {

    private final DataInput in;
    private final InputStream is;

    public DataObjectInputAdapter(DataInput in) {
        this.in = in;
        if (in instanceof InputStream)
            is = (InputStream) in;
        else
            is = null;
    }

    @Override
    public Object readObject() throws ClassNotFoundException, IOException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int available() throws IOException {
        if (is != null)
            return is.available();
        else
            throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void close() throws IOException {
        if (is != null)
            is.close();
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public long skip(long n) throws IOException {
        if (is != null) {
            return is.skip(n);
        }
        else
            throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int read() throws IOException {
        return readUnsignedByte();
    }

    @Override
    public int read(byte[] b) throws IOException {
        readFully(b);
        return b.length;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        readFully(b, off, len);
        return len;
    }

    public int skipBytes(int n) throws IOException {
        return in.skipBytes(n);
    }

    public int readUnsignedShort() throws IOException {
        return in.readUnsignedShort();
    }

    public int readUnsignedByte() throws IOException {
        return in.readUnsignedByte();
    }

    public String readUTF() throws IOException {
        return in.readUTF();
    }

    public short readShort() throws IOException {
        return in.readShort();
    }

    public long readLong() throws IOException {
        return in.readLong();
    }

    public String readLine() throws IOException {
        return in.readLine();
    }

    public int readInt() throws IOException {
        return in.readInt();
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        in.readFully(b, off, len);
    }

    public void readFully(byte[] b) throws IOException {
        in.readFully(b);
    }

    public float readFloat() throws IOException {
        return in.readFloat();
    }

    public double readDouble() throws IOException {
        return in.readDouble();
    }

    public char readChar() throws IOException {
        return in.readChar();
    }

    public byte readByte() throws IOException {
        return in.readByte();
    }

    public boolean readBoolean() throws IOException {
        return in.readBoolean();
    }
}
