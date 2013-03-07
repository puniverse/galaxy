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

import co.paralleluniverse.galaxy.core.StringRootManager;
import co.paralleluniverse.galaxy.core.StoreImpl;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
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
import static org.mockito.Mockito.any;
import static org.mockito.Matchers.*;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;
import co.paralleluniverse.common.io.Persistable;
import co.paralleluniverse.galaxy.StoreTransaction;
import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

/**
 *
 * @author pron
 */
public class StringRootManagerTest {
    StringRootManager srm;
    StoreImpl store;
    FullCluster cluster;
    Transaction txn;
    
    private Map<Long, ByteBuffer> buffers;
    private static final String[] COLLIDERS = new String[]{"o1", "nP", "mo"}; // PE, Od

    @Before
    public void setUp() throws Exception {
        store = mock(StoreImpl.class);
        cluster = mock(FullCluster.class);
        txn = mock(Transaction.class);
        srm = new StringRootManager(store, cluster);
        buffers = new HashMap<Long, ByteBuffer>();
        when(cluster.hasServer()).thenReturn(true);

        when(store.getMaxItemSize()).thenReturn(1024);

        final Answer<Void> getAnswer = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                read((Long) invocation.getArguments()[0], (Persistable) invocation.getArguments()[1]);
                return null;
            }
        };

        doAnswer(getAnswer).when(store).get1(anyLong(), any(Persistable.class));
        doAnswer(getAnswer).when(store).getx1(anyLong(), any(Persistable.class), any(StoreTransaction.class));

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                write((Long) invocation.getArguments()[0], (Persistable) invocation.getArguments()[1]);
                return null;
            }
        }).when(store).set1(anyLong(), any(Persistable.class), any(StoreTransaction.class));
    }

    void newTransaction() {
        txn = mock(Transaction.class);
    }
    
    @After
    public void tearDown() {
    }

    //////////////////////////////////////////////////
    @Test
    public void whenNewRootCreateNewBuffer() throws Exception {
        final String str = "a";
        final long id = str.hashCode();
        final long rootRef = 15;

        when(store.put(any(byte[].class), any(StoreTransaction.class))).thenReturn(rootRef).thenReturn(0L);

        long res = srm.get(str, txn);

        verify(store).release(id);
        verify(store, never()).release(rootRef);
        verify(txn).add(rootRef);
        
        assertThat(res, is(rootRef));

        ByteBuffer buffer = buffers.get(id);
        long nextRef = buffer.getLong();
        short numEntries = buffer.getShort();

        assertThat(nextRef, is(-1L));
        assertThat(numEntries, is((short) 1));

        String _str = readString(buffer);
        long _rootRef = buffer.getLong();
        buffer.rewind();

        assertThat(_str, equalTo(str));
        assertThat(_rootRef, is(rootRef));

        newTransaction();
        long resAgain = srm.get(str, txn);

        verify(txn, never()).add(anyLong());
        assertThat(resAgain, is(rootRef));
    }

    @Test
    public void whenCollidingRootsAppendToBuffer() throws Exception {
        final String str1 = COLLIDERS[0];
        final String str2 = COLLIDERS[1];
        assert str1.hashCode() == str2.hashCode();
        assert str1.compareTo(str2) > 0;

        final long id = str1.hashCode();
        final long rootRef1 = 184820302394032L;
        final long rootRef2 = 158973457L;

        when(store.put(any(byte[].class), any(StoreTransaction.class))).thenReturn(rootRef1).thenReturn(rootRef2).thenReturn(0L);

        long res1 = srm.get(str1, txn);

        verify(store).release(id);
        verify(store, never()).release(rootRef1);
        verify(txn).add(rootRef1);
        
        long res2 = srm.get(str2, txn);

        verify(store, times(2)).release(id);
        verify(store, never()).release(rootRef2);
        verify(txn).add(rootRef2);

        ByteBuffer buffer = buffers.get(id);
        long nextRef = buffer.getLong();
        short numEntries = buffer.getShort();

        assertThat(nextRef, is(-1L));
        assertThat(numEntries, is((short) 2));

        // str2 < str1 lexicographically
        String _str2 = readString(buffer);
        long _rootRef2 = buffer.getLong();

        String _str1 = readString(buffer);
        long _rootRef1 = buffer.getLong();

        buffer.rewind();

        assertThat(_str1, equalTo(str1));
        assertThat(_rootRef1, is(rootRef1));
        assertThat(_str2, equalTo(str2));
        assertThat(_rootRef2, is(rootRef2));

        assertThat(res1, is(rootRef1));
        assertThat(res2, is(rootRef2));

        newTransaction();
        long resAgain1 = srm.get(str1, txn);
        long resAgain2 = srm.get(str2, txn);

        verify(txn, never()).add(anyLong());
        
        assertThat(resAgain1, is(rootRef1));
        assertThat(resAgain2, is(rootRef2));
    }

    @Test
    public void testRootFind() throws Exception {
        final String str = "abc";
        final long id = str.hashCode();
        assert id != 1 && id != 12;

        ByteBuffer buffer;

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(1); // next buffer
        buffer.putShort(sh(3)); // num of entries
        writeEntry(buffer, "abc1", 0);
        writeEntry(buffer, "1abc", 0);
        writeEntry(buffer, "abcabc", 0);
        buffer.flip();
        buffers.put(id, buffer);

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(12); // next buffer
        buffer.putShort(sh(1)); // num of entries
        writeEntry(buffer, "", 0);
        buffer.flip();
        buffers.put(1L, buffer);

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(10); // next buffer
        buffer.putShort(sh(5)); // num of entries
        writeEntry(buffer, "abc1", 0);
        writeEntry(buffer, "1abc", 0);
        writeEntry(buffer, "2abc", 0);
        writeEntry(buffer, "abc", 1234);
        writeEntry(buffer, "abcabc", 0);
        buffer.flip();
        buffers.put(12L, buffer);

        long res = srm.get(str, txn);
        
        assertThat(res, is(1234L));
        verify(store, never()).getx1(anyLong(), any(Persistable.class), any(StoreTransaction.class));
        verify(store, never()).getx1(anyLong(), anyShort(), any(Persistable.class), any(StoreTransaction.class));
        verify(txn, never()).add(anyLong());
    }

    @Test
    public void testInsertEntryIntoPage() throws Exception {
        final int pageSize = 50;
        when(store.getMaxItemSize()).thenReturn(pageSize);
        when(store.put(any(byte[].class), any(StoreTransaction.class))).thenReturn(787878L).thenReturn(0L);

        final String str = "abc";
        final long id = str.hashCode();
        assert id != 1 && id != 12;

        ByteBuffer buffer;

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(1); // next buffer
        buffer.putShort(sh(3)); // num of entries
        writeEntry(buffer, "abc1", 0);
        writeEntry(buffer, "1abc", 0);
        writeEntry(buffer, "abcabc", 0);
        writeEntry(buffer, "abcsdfsdfabc", 0);
        buffer.flip();
        assert buffer.remaining() > pageSize;
        buffers.put(id, buffer);

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(12); // next buffer
        buffer.putShort(sh(2)); // num of entries
        writeEntry(buffer, "aa", 0);
        writeEntry(buffer, "bb", 0);
        buffer.flip();
        assert buffer.remaining() < pageSize - 15;
        buffers.put(1L, buffer);

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(-1); // next buffer
        buffer.putShort(sh(5)); // num of entries
        writeEntry(buffer, "abc1", 0);
        writeEntry(buffer, "1abc", 0);
        writeEntry(buffer, "2abc", 0);
        writeEntry(buffer, "abc0", 0);
        writeEntry(buffer, "abcabc", 0);
        buffer.flip();
        buffers.put(12L, buffer);

        long res = srm.get(str, txn);

        verify(store).release(id);
        verify(store).release(1L);
        verify(store, never()).release(787878L);
        verify(txn).add(787878L);
        
        assertThat(res, is(787878L));

        buffer = buffers.get(1L);

        long nextRef = buffer.getLong();
        short numEntries = buffer.getShort();

        assertThat(nextRef, is(12L));
        assertThat(numEntries, is((short) 3));

        assertThat(readString(buffer), is("aa"));
        assertThat(buffer.getLong(), is(0L));
        assertThat(readString(buffer), is("abc"));
        assertThat(buffer.getLong(), is(787878L));
        assertThat(readString(buffer), is("bb"));
        assertThat(buffer.getLong(), is(0L));
    }

    @Test
    public void whenOverflowThenLinkNewBuffer() throws Exception {
        final int pageSize = 50;
        when(store.getMaxItemSize()).thenReturn(pageSize);
        when(store.put(any(byte[].class), any(StoreTransaction.class))).thenReturn(787878L).thenReturn(0L); // for root ref
        when(store.put(any(Persistable.class), any(StoreTransaction.class))).thenAnswer(new Answer<Long>() {
            @Override
            public Long answer(InvocationOnMock invocation) throws Throwable {
                write(30L, (Persistable) invocation.getArguments()[0]);
                return 30L;
            }
        }).thenReturn(0L); // for page

        final String str = "abc";
        final long id = str.hashCode();
        assert id != 1 && id != 12;

        ByteBuffer buffer;

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(1); // next buffer
        buffer.putShort(sh(3)); // num of entries
        writeEntry(buffer, "abc1", 0);
        writeEntry(buffer, "1abc", 0);
        writeEntry(buffer, "abcabc", 0);
        writeEntry(buffer, "abcsdfsdfabc", 0);
        buffer.flip();
        assert buffer.remaining() > pageSize;
        buffers.put(id, buffer);

        buffer = ByteBuffer.allocate(200);
        buffer.putLong(-1); // next buffer
        buffer.putShort(sh(5)); // num of entries
        writeEntry(buffer, "abc1", 0);
        writeEntry(buffer, "1abc", 0);
        writeEntry(buffer, "2abc", 0);
        writeEntry(buffer, "abc0", 0);
        writeEntry(buffer, "abcabc", 0);
        buffer.flip();
        assert buffer.remaining() > pageSize;
        buffers.put(1L, buffer);

        long res = srm.get(str, txn);

        assertThat(res, is(787878L));

        verify(store).release(id);
        verify(store).release(1L);
        verify(store).release(30L);
        verify(store, never()).release(787878L);
        verify(txn).add(787878L);

        newTransaction();
        
        long resAgain = srm.get(str, txn);

        verify(txn, never()).add(anyLong());
        assertThat(resAgain, is(787878L));

        buffer = buffers.get(30L);

        long nextRef = buffer.getLong();
        short numEntries = buffer.getShort();

        assertThat(nextRef, is(-1L));
        assertThat(numEntries, is((short) 1));

        assertThat(readString(buffer), is("abc"));
        assertThat(buffer.getLong(), is(787878L));
    }

    @Test
    public void whenNoServerThenLockRootRefWithClusterManager() throws Exception {
        final String str = "a";
        final long id = "a".hashCode();
        final Object lock = new Object();

        when(cluster.hasServer()).thenReturn(false);
        when(cluster.lockRoot((int) id)).thenReturn(lock);

        srm.get(str, txn);

        InOrder inOrder = inOrder(cluster, store);
        inOrder.verify(cluster).lockRoot((int) id);
        inOrder.verify(store).get1(eq(id), any(Persistable.class));
        inOrder.verify(cluster).unlockRoot(lock);
    }

    /////////////////////////////////////////////////
    private static short sh(int x) {
        return (short) x;
    }

    private static void writeEntry(ByteBuffer buffer, String str, long ref) {
        final byte[] chars = str.getBytes(Charsets.UTF_8);
        buffer.putShort((short) chars.length);
        buffer.put(chars);
        buffer.putLong(ref);
    }

    private static String readString(ByteBuffer buffer) {
        short strLength = buffer.getShort();
        byte[] chars = new byte[strLength];
        buffer.get(chars);
        String str = new String(chars, Charsets.UTF_8);
        return str;
    }

    private void read(long id, Persistable p) {
        final ByteBuffer buffer = buffers.get(id);
        p.read(buffer);
        if (buffer != null)
            buffer.rewind();
    }

    private void write(long id, Persistable p) {
        ByteBuffer buffer;
        if (p == null)
            buffer = null;
        else {
            buffer = buffers.get(id);
            if (buffer == null || buffer.remaining() < p.size())
                buffer = ByteBuffer.allocate(p.size());
            p.write(buffer);
            buffer.flip();
        }
        buffers.put(id, buffer);
    }
}
