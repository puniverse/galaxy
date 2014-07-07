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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Provides utility methods for working with {@link Persistable}s and/or {@link ByteBuffer}s.
 *
 * @author pron
 */
public final class Persistables {

    /**
     * Converts a {@link Streamable} into a {@link Persistable}.
     *
     * @param streamable A {@link Streamable} object.
     * @return A {@link Persistable} representation of the given object.
     */
    public static Persistable persistable(final Streamable streamable) {
        return new Persistable() {

            @Override
            public int size() {
                return streamable.size();
            }

            @Override
            public void write(ByteBuffer buffer) {
                try {
                    streamable.write(new ByteBufferOutputStream(buffer));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public void read(ByteBuffer buffer) {
                try {
                    streamable.read(new ByteBufferInputStream(buffer));
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

        };
    }

    /**
     * Converts a {@link ByteBuffer} into a {@link Persistable}.
     *
     * @param buffer The buffer
     * @return A Persistable wrapper for the buffer.
     */
    public static Persistable persistable(final ByteBuffer buffer) {
        return new Persistable() {

            @Override
            public int size() {
                return buffer.limit();
            }

            @Override
            public void write(ByteBuffer buffer1) {
                buffer1.put(buffer);
                buffer1.flip();
            }

            @Override
            public void read(ByteBuffer buffer1) {
                buffer.rewind();
                buffer.put(buffer1);
                buffer.rewind();
            }

        };
    }

    /**
     * Returns an array containing a {@link ByteBuffer}'s {@link ByteBuffer#remaining() remaining} contents. <br> Upon returning
     * from this method, the buffer's {@link ByteBuffer#position() position} will remain unchanged.
     *
     * @param buffer The buffer to write into the array.
     * @return A newly allocated array of size {@link ByteBuffer#remaining() buffer.remaining()} containing the buffer's contents.
     */
    public static byte[] toByteArray(ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0
                && buffer.position() == 0 && buffer.limit() == buffer.capacity()
                && buffer.array().length == buffer.capacity()) {
            final byte[] array = buffer.array();
            return array;
        }

        final int p = buffer.position();
        final byte[] array = new byte[buffer.remaining()];
        buffer.get(array);
        buffer.position(p);
        return array;
    }

    /**
     * Returns a newly-allocated copy of a given {@link ByteBuffer}'s {@link ByteBuffer#remaining() remaining} contents. <br>
     * Upon return from this method, the original buffer's {@link ByteBuffer#position() position} will be unchanged, and the
     * returned buffer's position will be 0.
     * <p>
     * This method is not thread-safe.
     *
     * @param buffer The buffer to copy.
     * @return A copy of the buffer's {@link ByteBuffer#remaining() remaining} contents.
     */
    public static ByteBuffer copyOf(ByteBuffer buffer) {
        final int n = buffer.capacity();
        final ByteBuffer buffer2 = ByteBuffer.allocate(n);
        final int p = buffer.position();
        buffer.position(0);
        final int l = buffer.limit();
        buffer.limit(n);
        buffer2.put(buffer);
        buffer2.flip();
        buffer.position(p);
        buffer.limit(l);
        return buffer2;
    }

    /**
     * Returns a newly allocated {@link ByteBuffer#slice()} of a given {@link ByteBuffer}. The slice and the original buffer will
     * share their contents. <br> Upon return from this method, the original buffer's {@link ByteBuffer#position() position} will
     * be unchanged, and the returned buffer's position will be 0.
     * <p>
     * This method is not thread-safe.
     *
     * @param buffer The buffer to slice.
     * @param start The position of the slice's start in the given buffer.
     * @param length The length in bytes of the slice.
     * @return A slice of the buffer.
     * @see ByteBuffer#slice()
     */
    public static ByteBuffer slice(ByteBuffer buffer, int start, int length) {
        final int l = buffer.limit();
        final int p = buffer.position();
        buffer.limit(start + length);
        buffer.position(start);
        final ByteBuffer slice = buffer.slice();
        buffer.limit(l);
        buffer.position(p);
        return slice;
    }

    /**
     * Returns a newly allocated {@link ByteBuffer#slice()}, starting from the current {@link ByteBuffer#position() position}, of
     * a given {@link ByteBuffer}. The slice and the original buffer will share their contents. <br> Upon return from this
     * method, the original buffer's {@link ByteBuffer#position() position} will have advanced by {@code length}, and the returned
     * buffer's position will be 0.
     * <p>
     * This method is not thread-safe.
     *
     * @param buffer The buffer to slice.
     * @param length The length in bytes of the slice.
     * @return A slice of the buffer.
     * @see ByteBuffer#slice()
     */
    public static ByteBuffer slice(ByteBuffer buffer, int length) {
        final int l = buffer.limit();
        buffer.limit(buffer.position() + length);
        final ByteBuffer slice = buffer.slice();
        buffer.limit(l);
        buffer.position(buffer.position() + length);
        return slice;
    }

    private Persistables() {
    }

}
