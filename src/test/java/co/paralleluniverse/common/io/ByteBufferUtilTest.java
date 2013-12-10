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

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import static org.hamcrest.CoreMatchers.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 *
 * @author pron
 */
@RunWith(Parameterized.class)
public class ByteBufferUtilTest {
    Random r = new Random();
    boolean direct;
    ByteBuffer bb;

    public ByteBufferUtilTest(boolean direct) {
        this.direct = direct;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                    {false},
                    {true},});
    }

    @Before
    public void setUp() {
        bb = direct ? ByteBuffer.allocateDirect(500) : ByteBuffer.allocate(500);
    }

    @Test
    public void testByteArray() {
        byte[] array = new byte[20];
        r.nextBytes(array);

        bb.putInt(3);
        ByteBufferUtil.putArray(bb, array);
        bb.putInt(45);

        bb.flip();

        byte[] array2 = new byte[array.length];
        assertThat(bb.getInt(), is(3));
        ByteBufferUtil.getArray(bb, array2);
        assertThat(array2, equalTo(array));
        assertThat(bb.getInt(), is(45));
    }

    @Test
    public void testBooleanArray() {
        boolean[] array = new boolean[20];
        for (int i = 0; i < array.length; i++)
            array[i] = r.nextBoolean();

        bb.putInt(3);
        ByteBufferUtil.putArray(bb, array);
        bb.putInt(45);

        bb.flip();

        boolean[] array2 = new boolean[array.length];
        assertThat(bb.getInt(), is(3));
        ByteBufferUtil.getArray(bb, array2);

        assertThat(array2, equalTo(array));
        assertThat(bb.getInt(), is(45));
    }

    @Test
    public void testShortArray() {
        short[] array = new short[20];
        for (int i = 0; i < array.length; i++)
            array[i] = (short) r.nextInt();

        bb.putInt(3);
        ByteBufferUtil.putArray(bb, array);
        bb.putInt(45);

        bb.flip();

        short[] array2 = new short[array.length];
        assertThat(bb.getInt(), is(3));
        ByteBufferUtil.getArray(bb, array2);

        assertThat(array2, equalTo(array));
        assertThat(bb.getInt(), is(45));
    }

    @Test
    public void testIntArray() {
        int[] array = new int[20];
        for (int i = 0; i < array.length; i++)
            array[i] = r.nextInt();

        bb.putInt(3);
        ByteBufferUtil.putArray(bb, array);
        bb.putInt(45);

        bb.flip();

        int[] array2 = new int[array.length];
        assertThat(bb.getInt(), is(3));
        ByteBufferUtil.getArray(bb, array2);

        assertThat(array2, equalTo(array));
        assertThat(bb.getInt(), is(45));
    }

    @Test
    public void testLongArray() {
        long[] array = new long[20];
        for (int i = 0; i < array.length; i++)
            array[i] = r.nextLong();

        bb.putInt(3);
        ByteBufferUtil.putArray(bb, array);
        bb.putInt(45);

        bb.flip();

        long[] array2 = new long[array.length];
        assertThat(bb.getInt(), is(3));
        ByteBufferUtil.getArray(bb, array2);

        assertThat(array2, equalTo(array));
        assertThat(bb.getInt(), is(45));
    }

    @Test
    public void testDoubleArray() {
        double[] array = new double[20];
        for (int i = 0; i < array.length; i++)
            array[i] = r.nextDouble();

        bb.putInt(3);
        ByteBufferUtil.putArray(bb, array);
        bb.putInt(45);

        bb.flip();

        double[] array2 = new double[array.length];
        assertThat(bb.getInt(), is(3));
        ByteBufferUtil.getArray(bb, array2);

        assertThat(array2, equalTo(array));
        assertThat(bb.getInt(), is(45));
    }

    @Test(expected = BufferOverflowException.class)
    public void testOverflow() {
        long[] array = new long[64];
        ByteBufferUtil.putArray(bb, array);
    }

    @Test(expected = BufferUnderflowException.class)
    public void testUnderflow() {
        int[] array = new int[20];
        for (int i = 0; i < array.length; i++)
            array[i] = r.nextInt();

        ByteBufferUtil.putArray(bb, array);
        
        bb.flip();

        int[] array2 = new int[21];
        ByteBufferUtil.getArray(bb, array2);
    }
}
