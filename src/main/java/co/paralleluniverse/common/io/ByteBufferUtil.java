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

import co.paralleluniverse.common.util.UtilUnsafe;
import java.lang.reflect.Array;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

/**
 *
 * @author pron
 */
public final class ByteBufferUtil {
    public static byte[] toByteArray(ByteBuffer bb) {
        if (bb.hasArray() && bb.arrayOffset() == 0 && bb.position() == 0) {
            return bb.array();
        } else {
            byte[] arr = new byte[bb.remaining()];
            int p = bb.position();
            bb.get(arr);
            bb.position(p);
            return arr;
        }
    }

    public static ByteBuffer putArray(ByteBuffer bb, int position, Object array) {
        putArray0(bb, position, array);
        return bb;
    }

    public static ByteBuffer putArray(ByteBuffer bb, Object array) {
        int p = bb.position();
        p += putArray0(bb, p, array);
        if (p > bb.limit())
            bb.limit(p);
        bb.position(p);
        return bb;
    }

    public static ByteBuffer getArray(ByteBuffer bb, int position, Object array) {
        getArray0(bb, position, array);
        return bb;
    }

    public static ByteBuffer getArray(ByteBuffer bb, Object array) {
        int p = bb.position();
        p += getArray0(bb, p, array);
        bb.position(p);
        return bb;
    }

    private static int putArray0(ByteBuffer bb, int position, Object array) {
        final int size = getArraySize(array);
        if (bb.capacity() - position < size)
            throw new BufferOverflowException();

        if (!bb.isDirect())
            unsafe.copyMemory(array, getArrayBase(array), bb.array(), base + bb.arrayOffset() + position, size);
        else
            unsafe.copyMemory(array, getArrayBase(array), null, ((DirectBuffer) bb).address() + position, size);
        return size;
    }

    private static int getArray0(ByteBuffer bb, int position, Object array) {
        final int size = getArraySize(array);
        if (bb.limit() - position < size)
            throw new BufferUnderflowException();

        if (!bb.isDirect())
            unsafe.copyMemory(bb.array(), base + bb.arrayOffset() + position, array, getArrayBase(array), size);
        else
            unsafe.copyMemory(null, ((DirectBuffer) bb).address() + position, array, getArrayBase(array), size);
        return size;
    }

    private static int getArrayBase(Object array) {
        return unsafe.arrayBaseOffset(array.getClass());
    }

    private static int getArraySize(Object array) {
        return Array.getLength(array) * getArrayScale(array);
    }

    private static int getArrayScale(Object array) {
        return unsafe.arrayIndexScale(array.getClass());
//        if(array instanceof byte[])
//            return 1;
//        if(array instanceof double[])
//            return 8;
//        if(array instanceof float[])
//            return 4;
//        if(array instanceof boolean[])
//            return 1;
//        if(array instanceof short[])
//            return 2;
//        if(array instanceof int[])
//            return 4;
//        if(array instanceof long[])
//            return 8;
//        if(array instanceof char[])
//            return 2;
//        throw new AssertionError();
    }

    private ByteBufferUtil() {
    }
    static final Unsafe unsafe = UtilUnsafe.getUnsafe();
    private static final int base;
    private static final int baseLong;
    private static final int shift;

    static {
        try {

            if (unsafe.arrayIndexScale(boolean[].class) != 1)
                throw new AssertionError("Strange boolean array scale: " + unsafe.arrayIndexScale(boolean[].class));
            if (unsafe.arrayIndexScale(byte[].class) != 1)
                throw new AssertionError("Strange byte array scale: " + unsafe.arrayIndexScale(byte[].class));
            if (unsafe.arrayIndexScale(short[].class) != 2)
                throw new AssertionError("Strange short array scale: " + unsafe.arrayIndexScale(short[].class));
            if (unsafe.arrayIndexScale(char[].class) != 2)
                throw new AssertionError("Strange char array scale: " + unsafe.arrayIndexScale(char[].class));
            if (unsafe.arrayIndexScale(int[].class) != 4)
                throw new AssertionError("Strange int array scale: " + unsafe.arrayIndexScale(int[].class));
            if (unsafe.arrayIndexScale(float[].class) != 4)
                throw new AssertionError("Strange float array scale: " + unsafe.arrayIndexScale(float[].class));
            if (unsafe.arrayIndexScale(long[].class) != 8)
                throw new AssertionError("Strange long array scale: " + unsafe.arrayIndexScale(long[].class));
            if (unsafe.arrayIndexScale(double[].class) != 8)
                throw new AssertionError("Strange double array scale: " + unsafe.arrayIndexScale(double[].class));

            base = unsafe.arrayBaseOffset(byte[].class);
            baseLong = unsafe.arrayBaseOffset(long[].class);

            if (unsafe.arrayBaseOffset(boolean[].class) != base)
                throw new AssertionError("different array base");
            if (unsafe.arrayBaseOffset(short[].class) != base)
                throw new AssertionError("different array base");
            if (unsafe.arrayBaseOffset(char[].class) != base)
                throw new AssertionError("different array base");
            if (unsafe.arrayBaseOffset(int[].class) != base)
                throw new AssertionError("different array base");
            if (unsafe.arrayBaseOffset(float[].class) != base)
                throw new AssertionError("different array base");
            if (unsafe.arrayBaseOffset(long[].class) != baseLong)
                throw new AssertionError("different array base");
            if (unsafe.arrayBaseOffset(double[].class) != baseLong)
                throw new AssertionError("different array base");

            int scale = unsafe.arrayIndexScale(byte[].class);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            shift = 31 - Integer.numberOfLeadingZeros(scale);
            if (scale != 1 || shift != 0)
                throw new AssertionError("Strange byte array alignment");
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }
}
