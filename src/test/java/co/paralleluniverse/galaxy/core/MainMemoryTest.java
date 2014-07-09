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

import co.paralleluniverse.galaxy.server.MainMemoryEntry;
import co.paralleluniverse.galaxy.server.MainMemoryDB;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;

import static co.paralleluniverse.galaxy.core.MessageMatchers.*;
import static co.paralleluniverse.galaxy.core.MessageMatchers.equalTo;
import static co.paralleluniverse.galaxy.core.Cache.MAX_RESERVED_REF_ID;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKET;
import co.paralleluniverse.common.io.Persistables;
import co.paralleluniverse.galaxy.Cluster;
import org.mockito.InOrder;

/**
 *
 * @author pron
 */
public class MainMemoryTest {
    MainMemory mm;
    MainMemoryDB store;
    Cluster cluster;
    Comm comm;
    MainMemoryMonitor monitor;
    private Random rand = new Random();

    public MainMemoryTest() {
    }

    @Before
    public void setUp() {
        cluster = mock(Cluster.class);
        when(cluster.hasServer()).thenReturn(true);

        store = mock(MainMemoryDB.class);
        comm = mock(Comm.class);
        monitor = mock(MainMemoryMonitor.class);

        mm = new MainMemory("test", cluster, store, comm, monitor);

        verify(monitor).setMonitoredObject(mm);
    }

    @After
    public void tearDown() {
    }

    ///////////////////////////////////////////////////////////////////////
    /**
     * When INV is received, the owner is registered in the store as the owner of the line, and reply INVACK.
     */
    @Test
    public void whenINVThenMarkOwnerAndINVACK() throws Exception {
        when(store.casOwner(id(154), sh(5), sh(10))).thenReturn(sh(10));
        final Message.INV inv = Message.INV(sh(10), id(154), sh(5));
        mm.receive(inv);

        verify(store).casOwner(id(154), sh(5), sh(10));
        verify(comm).send(argThat(equalTo(Message.INVACK(inv))));
        verify(monitor).addOwnerWrite();
        verifyNoMoreInteractions(monitor);
    }

    /**
     * When INV is received but previous owner is wrong, don't change the owner and reply INV.
     */
    @Test
    public void whenINVAndWrongPreviousOwnerThenINV() throws Exception {
        when(store.casOwner(id(154), sh(5), sh(10))).thenReturn(sh(20));
        final Message.INV inv = Message.INV(sh(10), id(154), sh(5));
        mm.receive(inv);

        verify(store).casOwner(id(154), sh(5), sh(10));
        verify(comm).send(argThat(equalTo(Message.INV(inv, id(154), sh(20)))));
        verify(monitor).addOwnerServed();
        verifyNoMoreInteractions(monitor);
    }

    /**
     * When GET is received and owner is a node other than server, reply with a CHNGD_OWNR
     */
    @Test
    public void whenGETAndHasOwnerThenReplyCHNGD_OWNR() throws Exception {
        when(store.casOwner(id(154), sh(0), sh(10))).thenReturn(sh(3));

        final LineMessage get = Message.GET(sh(10), id(154));
        mm.receive(get);

        verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(get, id(154), sh(3), true))));
        verify(monitor).addOwnerServed();
        verifyNoMoreInteractions(monitor);
    }

    /**
     * When GET is received and owner is not found, then find allocation
     */
    @Test
    public void whenGETAndNoOwnerThenFindAllocation() throws Exception {
        when(store.casOwner(id(154), sh(0), sh(10))).thenReturn(sh(-1));
        when(store.findAllocation(id(154))).thenReturn(sh(3));

        final LineMessage get = Message.GET(sh(10), id(154));
        mm.receive(get);

        verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(get, id(154), sh(3), true))));
    }

    /**
     * When GET is received and owner is not found, reply with NOT_FOUND
     */
    @Test
    public void whenGETAndNoOwnerThenReplyNOT_FOUND() throws Exception {
        when(store.casOwner(id(154), sh(0), sh(10))).thenReturn(sh(-1));
        when(store.findAllocation(id(154))).thenReturn(sh(-1));

        final LineMessage get = Message.GET(sh(10), id(154));
        mm.receive(get);

        verify(comm).send(argThat(equalTo(Message.NOT_FOUND(get))));
    }

    /**
     * When GET is received and server is owner then reply with PUTX
     */
    @Test
    public void whenGETAndServerIsOwnerThenReplyPUTX() throws Exception {
        when(store.casOwner(id(154), sh(0), sh(10))).thenReturn(sh(10));
        when(store.read(id(154))).thenReturn(new MainMemoryEntry(1234, new byte[]{3, 4, 5}));
        final LineMessage get = Message.GET(sh(10), id(154));
        mm.receive(get);

        verify(store).casOwner(id(154), sh(0), sh(10));
        verify(store).read(id(154));
        verify(comm).send(argThat(equalTo(Message.PUTX(get, id(154), new short[0], 0, 1234, ByteBuffer.wrap(new byte[]{3, 4, 5})))));
        verify(monitor).addObjectServed();
        verify(monitor).addOwnerWrite();
        verifyNoMoreInteractions(monitor);
    }

    /**
     * When GET is received and owner is not found but line is reserved, then create line and reply with PUTX
     */
    @Test
    public void whenGETReservedAndNoOwnerThenCreateAndReplyPUTX() throws Exception {
        when(store.casOwner(15, sh(-1), sh(10))).thenReturn(sh(10));

        final LineMessage get = Message.GET(sh(10), 15);
        mm.receive(get);

        verify(store).casOwner(15, sh(-1), sh(10));
        verify(store).write(15, sh(10), 1, new byte[0], null);
        verify(comm).send(argThat(equalTo(Message.PUTX(get, 15, new short[0], 0, 1, null))));
        verify(monitor).addObjectServed();
        verify(monitor).addOwnerWrite();
        verifyNoMoreInteractions(monitor);
    }

    /**
     * When nodeRemoved is fired, store.removeOwner is called
     */
    @Test
    public void whenNodeRemovedThenRemoveOwner() throws Exception {
        mm.nodeRemoved(sh(10));

        verify(store).removeOwner(sh(10));
    }

    /**
     * When BACKUP_PACKET is received, updates are written to store
     */
    @Test
    public void whenBACKUP_PACKETThenWriteUpdates() throws Exception {
        final ByteBuffer buffer1 = randomBuffer(50);
        final ByteBuffer buffer2 = randomBuffer(50);
        final ByteBuffer buffer3 = randomBuffer(50);

        final BACKUP_PACKET bp = Message.BACKUP_PACKET(7, Arrays.asList(
                Message.BACKUP(id(1), 4, buffer1),
                Message.BACKUP(id(2), 5, buffer2),
                Message.BACKUP(id(3), 6, buffer3)));
        bp.setNode(sh(10));
        mm.receive(bp);

        InOrder inOrder = inOrder(store, comm);
        inOrder.verify(store).beginTransaction();
        inOrder.verify(store).write(eq(id(1)), eq(sh(10)), eq(4L), eq(Persistables.toByteArray(buffer1)), anyObject());
        inOrder.verify(store).write(eq(id(2)), eq(sh(10)), eq(5L), eq(Persistables.toByteArray(buffer2)), anyObject());
        inOrder.verify(store).write(eq(id(3)), eq(sh(10)), eq(6L), eq(Persistables.toByteArray(buffer3)), anyObject());
        inOrder.verify(store).commit(anyObject());
        inOrder.verify(comm).send(argThat(equalTo(Message.BACKUP_PACKETACK(bp))));
        verify(monitor).addTransaction(3);
        verifyNoMoreInteractions(monitor);
    }

    /////////////////////////////////////////////////////////////////////////////////
    short sh(int x) {
        return (short) x;
    }

    short[] sh(int... args) {
        final short[] array = new short[args.length];
        for (int i = 0; i < args.length; i++)
            array[i] = (short) args[i];
        return array;
    }

    private long id(long id) {
        return MAX_RESERVED_REF_ID + id;
    }

    private ByteBuffer randomBuffer(int size) {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++)
            buffer.put((byte) rand.nextInt());
        buffer.flip();
        return buffer;
    }
}
