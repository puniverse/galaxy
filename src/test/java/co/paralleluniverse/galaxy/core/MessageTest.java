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
package co.paralleluniverse.galaxy.core;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import co.paralleluniverse.galaxy.core.Message.BACKUP;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKET;
import co.paralleluniverse.galaxy.core.Message.INV;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.Message.MSG;
import co.paralleluniverse.galaxy.core.Message.ALLOC_REF;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static co.paralleluniverse.galaxy.core.MessageMatchers.*;

/**
 *
 * @author pron
 */
public class MessageTest {
    private Random rand = new Random();

    /////////////////////////////////////////////////////////////
    @Test
    public void testeResponseEquality() {
        final Message msg1 = Message.GETX((short) 76, 45687645L);
        msg1.setMessageId(90458608L);

        final Message rsp1 = new Message(msg1, null);
        rsp1.setIncoming();
        assertTrue(rsp1.equals(msg1));
        assertTrue(rsp1.hashCode() == msg1.hashCode());

        final Message rsp2 = new Message(msg1, null);
        assertTrue(!rsp2.equals(msg1));

        final Message rsp3 = new Message(msg1, null);
        rsp3.setIncoming();
        rsp3.setMessageId(45646346L);
        assertTrue(!rsp3.equals(msg1));
        assertTrue(rsp3.hashCode() != msg1.hashCode());

        final Message rsp4 = new Message(msg1, null);
        rsp4.setIncoming();
        rsp4.setNode((short) 2);
        assertTrue(!rsp4.equals(msg1));

//        final Message msg2 = Message.INV(new short[]{1, 3, 5}, 826284L);
//        msg2.setMessageId(587345L);
//
//        final Message rsp5 = new Message(msg2, null);
//        rsp5.setIncoming();
//        rsp5.setNode((short) 3);
//        assertTrue(rsp5.equals(msg2));
//        assertTrue(rsp5.hashCode() == msg2.hashCode());
//
//        final Message rsp6 = new Message(msg2, null);
//        rsp6.setIncoming();
//        rsp6.setNode((short) 2);
//        assertTrue(!rsp6.equals(msg2));
    }

    /////////////////////////////////////////////////////////////
    @Test
    public void testGETSer() {
        testSerialize(Message.GET((short) rand.nextInt(), rand.nextLong()));
    }

    @Test
    public void testGETXSer() {
        testSerialize(Message.GETX((short) rand.nextInt(), rand.nextLong()));
    }

    @Test
    public void testPUTSer() {
        final long line = rand.nextLong();
        final LineMessage m = new LineMessage((short) rand.nextInt(), Message.Type.GET, line);
        testSerialize(Message.PUT(m, line, rand.nextLong(), randomBuffer(150)));

        testSerialize(Message.PUT((short) rand.nextInt(), rand.nextLong(), rand.nextLong(), randomBuffer(100)));

        testSerialize(Message.PUT(randomShortArray(4), rand.nextLong(), rand.nextLong(), randomBuffer(100)));
    }

    @Test
    public void testPUTXSer() {
        final long line = rand.nextLong();
        final LineMessage m = new LineMessage((short) rand.nextInt(), Message.Type.GET, line);
        testSerialize(Message.PUTX(m, line, randomShortArray(5), rand.nextInt(1000), rand.nextLong(), randomBuffer(100)));
    }

    @Test
    public void testINVSer() {
        testSerialize(Message.INV((short) rand.nextInt(), rand.nextLong(), (short) rand.nextInt()));
        //testSerialize(Message.INV(randomShortArray(10), rand.nextLong()));
    }

    @Test
    public void testINVACKSer() {
        final long line = rand.nextLong();
        final INV m = Message.INV((short) rand.nextInt(), line, (short) rand.nextInt());
        testSerialize(Message.INVACK(m));

        testSerialize(Message.INVACK((short) rand.nextInt(), rand.nextLong()));
    }

    @Test
    public void testCHNGD_OWNRSer() {
        final long line = rand.nextLong();
        final LineMessage m = new LineMessage((short) rand.nextInt(), Message.Type.GET, line);
        testSerialize(Message.CHNGD_OWNR(m, line, (short) rand.nextInt(), rand.nextBoolean()));

        testSerialize(Message.CHNGD_OWNR((short) rand.nextInt(), rand.nextLong(), (short) rand.nextInt(), rand.nextBoolean()));
    }

    @Test
    public void testNOT_FOUNDSer() {
        final LineMessage m = new LineMessage((short) rand.nextInt(), Message.Type.GET, rand.nextLong());
        testSerialize(Message.NOT_FOUND(m));
    }

    @Test
    public void testBACKUPSer() {
        testSerialize(Message.BACKUP(rand.nextLong(), rand.nextLong(), randomBuffer(100)));
    }

    @Test
    public void testBACKUPACKSer() {
        testSerialize(Message.BACKUPACK((short) rand.nextInt(), rand.nextLong(), rand.nextLong()));
    }

    @Test
    public void testBACKUPACK_PACKETSer() {
        final BACKUP_PACKET m1 = Message.BACKUP_PACKET(rand.nextLong(), Arrays.asList(
                Message.BACKUP(rand.nextLong(), rand.nextLong(), randomBuffer(100)),
                Message.BACKUP(rand.nextLong(), rand.nextLong(), randomBuffer(45)),
                Message.BACKUP(rand.nextLong(), rand.nextLong(), randomBuffer(70))));

        byte[] array = m1.toByteArray();
        final BACKUP_PACKET m2 = (BACKUP_PACKET) Message.fromByteArray(array);

        assertThat(m2.getId(), equalTo(m1.getId()));
        assertThat(m2.getBackups().size(), equalTo(m1.getBackups().size()));
        for (int i = 0; i < m1.getBackups().size(); i++)
            assertThat(m2.getBackups().get(i), deepEqualTo(m1.getBackups().get(i)));

        final ByteBuffer[] buffers = m1.toByteBuffers();
        final BACKUP_PACKET m3 = (BACKUP_PACKET) Message.fromByteBuffer(combine(buffers));

        assertThat(m3.getId(), equalTo(m1.getId()));
        assertThat(m3.getBackups().size(), equalTo(m1.getBackups().size()));
        for (int i = 0; i < m1.getBackups().size(); i++)
            assertThat(m3.getBackups().get(i), deepEqualTo(m1.getBackups().get(i)));
    }

    @Test
    public void testBACKUPACK_PACKETACKSer() {
        testSerialize(Message.BACKUP_PACKET(rand.nextLong(), Collections.EMPTY_LIST));
    }

    @Test
    public void testALLOC_REFSer() {
        testSerialize(Message.ALLOC_REF((short) rand.nextInt(), rand.nextInt()));
    }

    @Test
    public void testALLOCED_REFSer() {
        final ALLOC_REF m = Message.ALLOC_REF((short) rand.nextInt(), rand.nextInt());
        testSerialize(Message.ALLOCED_REF(m, rand.nextLong(), rand.nextInt()));
    }

    @Test
    public void testMSGSer() {
        final MSG msg1 = Message.MSG((short) rand.nextInt(), rand.nextLong(), rand.nextBoolean(), randomArray(50));
        testSerialize(Message.MSG(msg1, randomArray(80)));

        testSerialize(Message.MSG((short) rand.nextInt(), rand.nextLong(), rand.nextBoolean(), randomArray(80)));

        testSerialize(Message.MSG(randomShortArray(2), rand.nextLong(), rand.nextBoolean(), randomArray(80)));
    }

    @Test
    public void testMSGACKSer() {
        final MSG msg1 = Message.MSG((short) rand.nextInt(), rand.nextLong(), rand.nextBoolean(), randomArray(50));
        testSerialize(Message.MSGACK(msg1));
    }

    /////////////////////////////////////////////////////////////
    private void testSerialize(Message message) {
        testArraySerialize(message);
        testByteBufferSerialize(message);
    }

    private void testArraySerialize(Message message) {
        byte[] array = message.toByteArray();
        final Message message2 = Message.fromByteArray(array);
        assertThat(message2, deepEqualTo(message));
    }

    private void testByteBufferSerialize(Message message) {
        final ByteBuffer[] buffers = message.toByteBuffers();
        final Message message2 = Message.fromByteBuffer(combine(buffers));
        assertThat(message2, deepEqualTo(message));
    }

    private ByteBuffer randomBuffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++)
            buffer.put((byte) rand.nextInt());
        buffer.flip();
        return buffer;
    }

    private byte[] randomArray(int size) {
        byte[] array = new byte[size];
        for (int i = 0; i < size; i++)
            array[i] = (byte) rand.nextInt();
        return array;
    }

    private short[] randomShortArray(int size) {
        short[] array = new short[size];
        for (int i = 0; i < size; i++)
            array[i] = (short) rand.nextInt();
        return array;
    }

    private ByteBuffer combine(ByteBuffer[] buffers) {
        int size = 0;
        for (ByteBuffer b : buffers)
            size += b.remaining();

        final ByteBuffer buffer = ByteBuffer.allocate(size);

        for (ByteBuffer b : buffers) {
            buffer.put(b);
            b.rewind();
        }

        buffer.flip();
        assertThat(buffer.remaining(), is(size));
        return buffer;
    }
}
