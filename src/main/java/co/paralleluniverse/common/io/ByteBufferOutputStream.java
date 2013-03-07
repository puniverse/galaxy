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

import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * @author Ron Pressler
 */
public class ByteBufferOutputStream extends OutputStream implements DataOutput, ObjectOutput {
    private final ByteBuffer buffer;

    /**
     * @param buffer
     */
    public ByteBufferOutputStream(ByteBuffer buffer) {
	this.buffer = buffer;
    }

    @Override
    public void writeObject(Object obj) {
        throw new UnsupportedOperationException("Not supported.");
    }
    
    @Override
    public void write(int b) throws IOException {
	try {
	    buffer.put((byte)b);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void write(byte[] b) throws IOException {
	try {
	    buffer.put(b);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
	try {
	    buffer.put(b, off, len);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void writeBoolean(boolean v) throws IOException {
	try {
	    buffer.put(v ? (byte)1 : (byte)0);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}

    }

    @Override
    public void writeByte(int v) throws IOException {
	write(v);
    }

    @Override
    public void writeBytes(String s) throws IOException {
	byte[] b = s.getBytes();
	write(b);

    }

    @Override
    public void writeChar(int v) throws IOException {
	try {
	    buffer.putChar((char)v);
	} catch (BufferUnderflowException e) {
	    throw new IOException(e);
	}

    }

    @Override
    public void writeChars(String s) throws IOException {
	try {
	    for (int i = 0; i < s.length(); i++)
		buffer.putChar(s.charAt(i));
	} catch (BufferOverflowException e) {
	    throw new EOFException();
	}
    }

    @Override
    public void writeDouble(double v) throws IOException {
	try {
	    buffer.putDouble(v);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void writeFloat(float v) throws IOException {
	try {
	    buffer.putFloat(v);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void writeInt(int v) throws IOException {
	try {
	    buffer.putInt(v);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void writeLong(long v) throws IOException {
	try {
	    buffer.putLong(v);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void writeShort(int v) throws IOException {
	try {
	    buffer.putShort((short)v);
	} catch (BufferOverflowException e) {
	    throw new IOException(e);
	}
    }

    @Override
    public void writeUTF(String s) throws IOException {
	throw new UnsupportedOperationException();
    }

}
