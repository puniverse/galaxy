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

import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides utility methods for working with {@link Streamable}s and/or data streams.
 * 
 * @author pron
 */
public final class Streamables {
    /**
     * Serializes a {@link Streamable} into a byte array.
     * @param streamable The object to serialize.
     * @return A byte array containing the serialized object.
     */
    public static byte[] toByteArray(Streamable streamable) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(streamable.size());
            final DataOutputStream dos = new DataOutputStream(baos);
            streamable.write(dos);
            dos.flush();
            baos.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads a {@link Streamable}'s serialized contents from a byte array.
     * The serialized form begins in the beginning of the array and extends to the end of the array.
     * <p>
     * Same as calling {@link #fromByteArray(co.paralleluniverse.common.io.Streamable, byte[], int, int) fromByteArray(streamable, array, 0, array.length)}.
     * 
     * @param streamable The object whose contents are to be read.
     * @param array The array from which to read the serialized form.
     */
    public static void fromByteArray(Streamable streamable, byte[] array) {
        fromByteArray(streamable, array, 0, array.length);
    }

    /**
     * Reads a {@link Streamable}'s serialized contents from a byte array.
     * The serialized form begins in the given offset and extends to the end of the array.
     * <p>
     * Same as calling {@link #fromByteArray(co.paralleluniverse.common.io.Streamable, byte[], int, int) fromByteArray(streamable, array, offset, array.length - offset)}.
     * 
     * @param streamable The object whose contents are to be read.
     * @param array The array from which to read the serialized form.
     * @param offset The offset into the array from which to start reading the serialized form.
     */
    public static void fromByteArray(Streamable streamable, byte[] array, int offset) {
        fromByteArray(streamable, array, offset, array.length - offset);
    }

    /**
     * Reads a {@link Streamable}'s serialized contents from a byte array.
     * 
     * @param streamable The object whose contents are to be read.
     * @param array The array from which to read the serialized form.
     * @param offset The offset into the array from which to start reading the serialized form.
     * @param length The length of the serialized form to read from the array.
     */
    public static void fromByteArray(Streamable streamable, byte[] array, int offset, int length) {
        try {
            final ByteArrayInputStream bais = new ByteArrayInputStream(array, offset, length);
            final DataInputStream dis = new DataInputStream(bais);
            streamable.read(dis);
            bais.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given {@link ByteBuffer} into the given {@link DataOutput}.
     * 
     * @param out The {@link DataOutput} into which the buffer will be written.
     * @param buffer The buffer to write into the {@link DataOutput}.
     * @throws IOException
     */
    public static void writeBuffer(DataOutput out, ByteBuffer buffer) throws IOException {
        if (buffer.hasArray()) {
            out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
            buffer.position(buffer.limit());
        } else {
            final byte[] array = new byte[buffer.remaining()];
            buffer.get(array);
            assert buffer.remaining() == 0 && buffer.position() == buffer.limit();
            out.write(array);
        }
    }

    /**
     * Returns the length in bytes of a string's UTF-8 encoding.
     * 
     * @param str The string to measure.
     * @return The length in bytes of a string's UTF-8 encoding.
     */
    public static int calcUtfLength(String str) {
        final int strlen = str.length();
        int utflen = 0;
        for (int i = 0; i < strlen; i++) {
            int c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utflen++;
            } else if (c > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }
        return utflen;
    }
    //////////////////////
    private final ConcurrentMap<Byte, ClassInfo> types = new ConcurrentHashMap<Byte, ClassInfo>();

    public <T extends Streamable> void register(byte qualifier, Class<T> clazz) {
        ClassInfo old = types.putIfAbsent(qualifier, new ClassInfo(clazz));
        if (old != null) {
            throw new RuntimeException("Qualifier " + qualifier + " is already registered to class " + old.clazz.getName());
        }
    }

    public Object read(DataInput in) throws IOException {
        final byte qualifier = in.readByte();
        final ClassInfo ci = types.get(qualifier);
        if (ci == null)
            throw new IOException("Cannot read object. No class registered for qualifier " + qualifier);
        final Streamable obj = (Streamable) ci.construct(qualifier);
        obj.read(in);
        return obj;
    }

    public Object fromByteArray(byte[] array) throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(array);
        final DataInputStream dis = new DataInputStream(bais);
        final Object obj = read(dis);
        bais.close();
        return obj;
    }

    private static class ClassInfo<T> {
        public final Class<T> clazz;
        private final boolean constructorWithQualifier;
        private final Constructor<T> constructor;

        public ClassInfo(Class<T> clazz) {
            try {
                boolean _constructorWithQualifier = false;
                Constructor<T> _constructor = null;
                try {
                    _constructor = clazz.getDeclaredConstructor(byte.class);
                    _constructorWithQualifier = true;
                } catch (NoSuchMethodException ex) {
                }
                if (_constructor == null) {
                    try {
                        _constructor = clazz.getDeclaredConstructor();
                        _constructorWithQualifier = false;
                    } catch (NoSuchMethodException ex) {
                    }
                }
                if (_constructor == null)
                    throw new IllegalArgumentException("Class " + clazz.getName() + " does not have a default constructor or one that takes a byte as parameter");

                this.clazz = clazz;
                this.constructorWithQualifier = _constructorWithQualifier;
                this.constructor = _constructor;
            } catch (SecurityException ex) {
                throw new RuntimeException(ex);
            }
        }

        public T construct(byte type) {
            try {
                if (constructorWithQualifier)
                    return constructor.newInstance(type);
                else
                    return constructor.newInstance();
            } catch (InstantiationException ex) {
                throw Throwables.propagate(ex);
            } catch (IllegalAccessException ex) {
                throw Throwables.propagate(ex);
            } catch (IllegalArgumentException ex) {
                throw Throwables.propagate(ex);
            } catch (InvocationTargetException ex) {
                throw Throwables.propagate(ex);
            }

        }
    }
}
