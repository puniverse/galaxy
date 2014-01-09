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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * @author pron
 */
public class ByteBufferInputStream extends InputStream implements DataInput {
    private final ByteBuffer buffer;
    /**
     * @param buffer
     */
    public ByteBufferInputStream(ByteBuffer buffer) {
	this.buffer = buffer;
    }
    
    @Override
    public boolean readBoolean() throws IOException {
	try {
	    return buffer.get() != 0;
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public byte readByte() throws IOException {
	try {
	    return buffer.get();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public char readChar() throws IOException {
	try {
	    return buffer.getChar();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public double readDouble() throws IOException {
	try {
	    return buffer.getDouble();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public float readFloat() throws IOException {
	try {
	    return buffer.getFloat();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
	try {
	    buffer.get(b, off, len);
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}

    }

    @Override
    public void readFully(byte[] b) throws IOException {
	try {
	    buffer.get(b);
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}

    }

    @Override
    public int readInt() throws IOException {
	try {
	    return buffer.getInt();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public String readLine() throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public long readLong() throws IOException {
	try {
	    return buffer.getLong();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public short readShort() throws IOException {
	try {
	    return buffer.getShort();
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public int readUnsignedByte() throws IOException {
	try {
	    return unsignedByte(buffer.get());
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public int readUnsignedShort() throws IOException {
	try {
	    return unsignedShort(buffer.getShort());
	} catch (BufferUnderflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public String readUTF() throws IOException {
	throw new UnsupportedOperationException();
    }

    @Override
    public int skipBytes(int n) throws IOException {
	n = Math.min(n, available());
	buffer.position(buffer.position() + n);
	return n;
    }

    private static short unsignedByte(byte n) {
	return (short)((n << 8) >>> 8);
    }

    private static int unsignedShort(short n) {
	return ((n << 16) >>> 16);
    }

    @Override
    public int available() throws IOException {
	return buffer.limit() - buffer.position();
    }

    @Override
    public int read() throws IOException {
	try {
	    return unsignedByte(buffer.get());
	} catch (BufferUnderflowException e) {
	    return -1;
	}
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
	if (len == 0)
	    return 0;

        final int rem = available();
        assert rem >= 0;
	if (rem == 0)
	    return -1;

	final int n = Math.min(len, rem);
	buffer.get(b, off, n);
	return n;
    }

    @Override
    public long skip(long n) throws IOException {
	return skipBytes((int)n);
    }

    @Override
    public boolean markSupported() {
	return true;
    }

    @Override
    public synchronized void mark(int readlimit) {
	buffer.mark();
    }

    @Override
    public synchronized void reset() throws IOException {
	buffer.reset();
    }

}
