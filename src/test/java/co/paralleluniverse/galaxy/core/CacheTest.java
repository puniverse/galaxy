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

import co.paralleluniverse.galaxy.CacheListener;
import co.paralleluniverse.common.io.Persistable;
import co.paralleluniverse.galaxy.AbstractCacheListener;
import co.paralleluniverse.galaxy.LineFunction;
import co.paralleluniverse.galaxy.RefNotFoundException;
import co.paralleluniverse.galaxy.TimeoutException;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static org.mockito.Matchers.any;
import static co.paralleluniverse.galaxy.test.LogMock.startLogging;
import static co.paralleluniverse.galaxy.test.LogMock.stopLogging;
import static co.paralleluniverse.galaxy.test.LogMock.when;
import static co.paralleluniverse.galaxy.test.LogMock.doAnswer;
import static co.paralleluniverse.galaxy.test.LogMock.doNothing;
import static co.paralleluniverse.galaxy.test.LogMock.doReturn;
import static co.paralleluniverse.galaxy.test.LogMock.doThrow;
import static co.paralleluniverse.galaxy.test.LogMock.mock;
import static co.paralleluniverse.galaxy.test.LogMock.spy;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;

import static co.paralleluniverse.galaxy.core.MessageMatchers.*;
import static co.paralleluniverse.galaxy.core.Cache.CacheLine;
import static co.paralleluniverse.galaxy.core.Cache.State;
import static co.paralleluniverse.galaxy.core.Cache.State.*;
import static co.paralleluniverse.galaxy.core.Cache.PENDING;
import co.paralleluniverse.galaxy.core.RefAllocator.RefAllocationsListener;
import co.paralleluniverse.galaxy.core.Message.BACKUP;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.Message.MSG;
import co.paralleluniverse.galaxy.core.Message.Type;
import static co.paralleluniverse.galaxy.core.Op.Type.*;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 * @author pron
 */
@RunWith(Parameterized.class)
public class CacheTest {
    private static final int DEFAULT_ALLOC_COUNT = 10000;
    Cache cache;
    FullCluster cluster;
    AbstractComm comm;
    Backup backup;
    CacheStorage storage;
    CacheMonitor monitor;
    boolean hasServer;
    long messageId = 0;

//    public CacheTest() {
//        this.hasServer = true;
//    }
    public CacheTest(boolean hasServer) {
        this.hasServer = hasServer;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {true},
            {false},});
    }

    @Before
    public void setUp() throws Exception {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);

        cluster = mock(FullCluster.class);
        when(cluster.isMaster()).thenReturn(true);
        when(cluster.hasServer()).thenReturn(hasServer);
        when(cluster.getMyNodeId()).thenReturn(sh(5));
        comm = mock(AbstractComm.class);
        backup = mock(Backup.class);
        when(backup.inv(anyLong(), anyShort())).thenReturn(true);

        storage = spy(new HeapLocalStorage("test", null));
        monitor = mock(CacheMonitor.class);
        cache = makeCache(10000);
        cache.setReceiver(mock(MessageReceiver.class));

        messageId = 0;
    }

    Cache makeCache(int capacity) throws Exception {
        return makeCache(capacity, false);
    }

    Cache makeCache(boolean syncrhonous) throws Exception {
        return makeCache(1000, syncrhonous);
    }

    Cache makeCache(int capacity, boolean syncrhonous) throws Exception {
        Cache _cache = new Cache("test", cluster, comm, storage, backup, monitor, capacity);
        _cache.setReuseLines(false);
        _cache.setReuseSharerSets(false);
        _cache.setSynchronous(syncrhonous);
        _cache.init();
        // verify uninteresting interactions so that test can use verifyNoMoreInteractions().
        verify(monitor).setMonitoredObject(_cache);
        verify(comm).setReceiver(_cache);
        if (hasServer)
            verify(comm, atLeastOnce()).isSendToServerInsteadOfMulticast();
        return _cache;
    }

    private void reset() throws Exception {
        setUp();
    }

    @After
    public void tearDown() {
    }

    ///////////////////////////////////////////////////////////////////////
    /**
     * A get returns data after PUT has been received.
     */
    @Test
    public void whenPUTThenGetLine() throws Exception {
        PUT(1, sh(10), 1, "hello");

        byte[] res = (byte[]) doOp(GET, 1L);

        assertState(1, S, null);
        assertVersion(1, 1);
        assertThat(deserialize(res), is("hello"));

        verify(monitor).addMessageReceived(Type.PUT);
        verify(monitor).addOp(eq(GET), anyLong());
        verify(monitor).addHit();
        verify(monitor).addStalePurge(0);
        verifyNoMoreInteractions(monitor);
    }

    /**
     * A getx returns data after PUTX has been received.
     */
    @Test
    public void whenPUTXThenGetXLine() throws Exception {
        PUTX(1, sh(10), 1, "hello");

        byte[] res = (byte[]) doOp(GETX, 1L);

        assertState(1, E, null);
        assertVersion(1, 1);
        assertThat(deserialize(res), is("hello"));

        verify(monitor).addMessageReceived(Type.PUTX);
        if (hasServer())
            verify(monitor).addMessageReceived(Type.INVACK);
        verify(monitor).addOp(eq(GETX), anyLong());
        if (hasServer)
            verify(monitor).addMessageSent(Type.INV);
        verify(monitor).addHit();
    }

    /**
     * A GET/X message is broadcast when get/x a missing line.
     */
    @Test
    public void whenGetMissingLineThenBroadcastGET() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            Object res = cache.runOp(new Op(getType, 1L, null));

            assertThat(res, is(PENDING));
            assertState(1, I, getType == GETX ? O : S);
            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(-1), 1L))));

            verify(monitor).addMessageSent(matchingMessage(getType, sh(-1), 1L).getType());
            verify(monitor).addMiss();
            verifyNoMoreInteractions(monitor);

            reset();
        }
    }

    /**
     * A GETX message is broadcast when getting, and getting X a missing line at the same time.
     */
    @Test
    public void whenGetXAndGetMissingLineThenBroadcastGETX() throws Exception {
        Object res = cache.runOp(new Op(GETX, 1L, null));
        assertThat(res, is(PENDING));

        res = cache.runOp(new Op(GET, 1L, null));

        assertThat(res, is(PENDING));
        assertState(1, I, O);
        assertVersion(1, 0);
        verify(comm).send(argThat(equalTo(Message.GETX(sh(-1), 1L))));
        verify(comm, never()).send(argThat(equalTo(Message.GET(sh(-1), 1L))));
    }

    /**
     * a getx of a shared line causes a GETX message is sent to owner.
     */
    @Test
    public void whenPUTAndGetXThenGETX() throws Exception {
        PUT(1, sh(10), 1, "hello");

        Object res = cache.runOp(new Op(GETX, 1L, null));

        assertThat(res, is(PENDING));
        assertState(1, S, O);
        assertVersion(1, 1);
        verify(comm).send(argThat(equalTo(Message.GETX(sh(10), 1L))));
    }

    /**
     * get/x + hint results in GET/X sent only to hinted line.
     */
    @Test
    public void whenGetWithHintThenGETFromHint() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            Object res = cache.runOp(new Op(getType, 1L, sh(15), null));

            assertThat(res, is(PENDING));
            assertState(1, I, getType == GETX ? O : S);
            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(15), 1L))));
            verify(comm, never()).send(argThat(equalTo(matchingMessage(getType, sh(-1), 1L))));

            reset();
        }
    }

    /**
     * get/x + hint of a shared line results in GET/X sent only to owner (and not hinted line).
     */
    @Test
    public void whenGetWithHintAndKnownOwnerThenGETFromOwner() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            PUT(1, sh(10), 1, "hello");
            makeInvalid(sh(10), 1L);

            cache.runOp(new Op(getType, 1L, sh(15), null));

            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(10), 1L))));
            verify(comm, never()).send(argThat(equalTo(matchingMessage(getType, sh(15), 1L))));
            verify(comm, never()).send(argThat(equalTo(matchingMessage(getType, sh(-1), 1L))));

            reset();
        }
    }

    /**
     * get/x from owner results in GET/X sent only owner of the requested line line.
     */
    @Test
    public void whenGetFromOwnerThenGETFromOwner() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            PUT(2, sh(10), 1, "hello");

            cache.runOp(new Op(GET_FROM_OWNER, 2, new Op(getType, 1, null), null));

            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(10), 1L))));
            verify(comm, never()).send(argThat(equalTo(matchingMessage(getType, sh(-1), 1L))));

            reset();
        }
    }

    /**
     * get/x from owner broadcasts GET/X if owner line does not exist.
     */
    @Test
    public void whenGetFromOwnerAndNoLineThenBroadcastGET() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            Op get = (Op) cache.runOp(new Op(GET_FROM_OWNER, 2, new Op(getType, 1, null), null));
            cache.runOp(get);

            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(-1), 1L))));
            verify(comm, never()).send(argThat(equalTo(matchingMessage(getType, sh(10), 1L))));

            reset();
        }
    }

    /**
     * get/x from owner sends GET/X to the actual owner of the requested line if it's known, not to the owner of the "owned" line.
     */
    @Test
    public void whenGetFromOwnerAndKnownLineThenGETFromActualOwner() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            PUT(2, sh(10), 1, "hello");
            PUT(1, sh(15), 1, "hello");
            makeInvalid(sh(15), 1);

            cache.runOp(new Op(GET_FROM_OWNER, 2, new Op(getType, 1, null), null));

            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(15), 1L))));
            verify(comm, never()).send(argThat(equalTo(matchingMessage(getType, sh(10), 1L))));

            reset();
        }
    }

    /**
     * When GET is received then PUT is sent
     */
    @Test
    public void whenGETThenPUT() throws Exception {
        PUTX(1234L, sh(10), 2, "hello", 20, 30, 40);
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));

        assertState(1234L, O, null);

        final LineMessage get1 = Message.GET(sh(50), 1234L);
        final LineMessage get2 = Message.GET(sh(60), 1234L);
        cache.receive(get1);
        cache.receive(get2);

        assertState(1234L, O, null);
        verify(comm).send(argThat(equalTo(Message.PUT(get1, 1234L, 2, toBuffer("hello")))));
        verify(comm).send(argThat(equalTo(Message.PUT(get2, 1234L, 2, toBuffer("hello")))));
    }

    public static LineFunction<Long> storefunc(final long set) {
        return new LineFunction<Long>() {
            @Override
            public Long invoke(LineAccess lineAccess) {
                try {
                    ByteBuffer get = lineAccess.getForRead();
                    if (!Charset.forName("ISO-8859-1").newDecoder().decode(get).toString().equals("hello"))
                        return 0L;
                    ByteBuffer bb = lineAccess.getForWrite(8);
                    bb.clear();
                    bb.putLong(set);
                    bb.flip();
                    return set;
                } catch (CharacterCodingException ex) {
                    throw new AssertionError(ex);
                }
            }
        };
    }

//    @Test
    public void whenInvokeLocalAndOThenInvokeAndSendINVAndBecomeE() throws Exception {
        PUTX(1234L, sh(10), 2, "hello", 20);
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));
        assertState(1234L, O, null);
        long l = (Long) cache.doOp(Op.Type.INVOKE, 1234L, storefunc(45L), null, null);
        assertModified(1234L, true);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1234L, sh(10)))));
        assertState(1234L, O, E);
        assertThat(l, equalTo(45L));
    }

    @Test
    public void testInvokeNotOwnerInI() throws Exception {
        setCommMsgCounter();
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));
        final LineFunction<Long> storefunc = storefunc(45L);
        ListenableFuture<Object> future = cache.doOpAsync(Op.Type.INVOKE, 1234L, storefunc, null, null);
        final Message.INVOKE msg = Message.INVOKE(sh(-1), 1234L, storefunc);
        verify(comm).send(argThat(equalTo(msg.setMessageId(1))));
        assertState(1234L, I, null);
        cache.receive(Message.INVRES(msg, 1234L, 45L));
        assertThat((long) future.get(), equalTo(45L));
    }

    @Test
    public void testInvokeNotOwnerInS() throws Exception {
        setCommMsgCounter();
        PUT(1234L, sh(10), 2, "hello");
        final LineFunction<Long> storefunc = storefunc(45L);
        assertState(1234L, S, null);
        ListenableFuture<Object> future = cache.doOpAsync(Op.Type.INVOKE, 1234L, storefunc, null, null);
        final Message.INVOKE msg = Message.INVOKE(sh(10), 1234L, storefunc);
        verify(comm).send(argThat(equalTo(msg.setMessageId(2))));
        assertState(1234L, S, null);
        cache.receive(Message.INVRES(msg, 1234L, 45L));
        assertThat((long) future.get(), equalTo(45L));
    }

    @Test
    public void testInvokeWhenServerIsOwner() throws Exception {
        setCommMsgCounter();
        PUT(1234L, sh(10), 2, "hello");
        final LineFunction<Long> storefunc = storefunc(45L);
        assertState(1234L, S, null);
        ListenableFuture<Object> future = cache.doOpAsync(Op.Type.INVOKE, 1234L, storefunc, null, null);
        final Message msg = Message.INVOKE(sh(10), 1234L, storefunc).setMessageId(2);
        verify(comm).send(argThat(equalTo(msg)));
        assertState(1234L, S, null);
        // Server reply with putx
        PUTX(1234L, sh(10), 2, "hello");
        assertState(1234L, E, null);
        assertThat((long) future.get(), equalTo(45L));
    }

    @Test
    public void testInvokeLocalWhenOwnerIsE() throws Exception {
        setCommMsgCounter();
        PUTX(1234L, sh(10), 2, "hello");
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));
        assertState(1234L, E, null);
        long l = (Long) cache.doOp(Op.Type.INVOKE, 1234L, storefunc(45L), null, null);
        assertState(1234L, E, null);
        assertThat(l, equalTo(45L));
    }

    @Test
    public void testHandleInvokeWhenE() throws Exception {
        PUTX(1234L, sh(10), 2, "hello");
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));
        assertState(1234L, E, null);
        final LineFunction<Long> storefunc = storefunc(45L);
        final Message.INVOKE msg = Message.INVOKE(sh(10), 1234L, storefunc);
        cache.receive(msg);
        verify(comm).send(argThat(equalTo(Message.INVRES(msg, 1234L, 45L))));
        assertModified(1234L, true);
    }

    @Ignore // not true since c62a579 when the optimization in transitionToE has been removed
    @Test
    public void testHandleInvokeWhenO() throws Exception {
        PUTX(1234L, sh(10), 2, "hello", 20);
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));
        assertState(1234L, O, null);
        final LineFunction<Long> storefunc = storefunc(45L);
        final Message.INVOKE msg = Message.INVOKE(sh(10), 1234L, storefunc);
        cache.receive(msg);
        assertModified(1234L, true);
        assertState(1234L, O, E);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1234L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INVRES(msg, 1234L, 45L))));
    }

    /**
     * When GETX is received then PUTX is sent
     */
    @Test
    public void whenGETXThenPUTX() throws Exception {
        PUTX(1234L, sh(10), 2, "hello", 20, 30, 40);
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))));

        assertState(1234L, O, null);

        final LineMessage get1 = Message.GET(sh(50), 1234L);
        final LineMessage get2 = Message.GET(sh(60), 1234L);
        cache.receive(get1);
        cache.receive(get2);

        assertState(1234L, O, null);
        verify(comm).send(argThat(equalTo(Message.PUT(get1, 1234L, 2, toBuffer("hello")))));
        verify(comm).send(argThat(equalTo(Message.PUT(get2, 1234L, 2, toBuffer("hello")))));

        final LineMessage getx = Message.GETX(sh(50), 1234L);
        cache.receive(getx);

        assertOwner(1234L, sh(50));

        assertState(1234L, I, null);
        verify(comm).send(argThat(equalTo(Message.PUTX(getx, 1234L, sh(20, 30, 40, 50, 60), 0, 2, toBuffer("hello")))));
    }

    /**
     * When GET/X is received and not broadcast and line is not found, send CHNGD_OWNER
     */
    @Test
    public void whenGETAndNoLineThenCHNGD_OWNER() throws Exception {
        for (Message.Type getType : new Message.Type[]{Message.Type.GET, Message.Type.GETX}) {
            final LineMessage get = new Message.GET(getType, sh(50), 1234L);
            cache.receive(get);

            verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(get, 1234L, sh(-1), false))));

            reset();
        }
    }

    /**
     * When GET/X broadcast is received and line is not found, send ACK
     */
    @Test
    public void whenGETBcastAndNoLineThenACK() throws Exception {
        for (Message.Type getType : new Message.Type[]{Message.Type.GET, Message.Type.GETX}) {
            final LineMessage get = new Message.GET(getType, sh(-1), 1234L);
            cache.receive(get);

            verify(comm).send(argThat(equalTo(Message.ACK(get))));

            reset();
        }
    }

    /**
     * When CHNGD_OWNR is received as a response to a GET, then re-send GET to the new owner.
     */
    @Test
    public void whenGetAndCHNGD_OWNRThenGETAgain() throws Exception {
        cache.runOp(new Op(GET, 1234L, null));

        when(cluster.getMaster(sh(20))).thenReturn(makeNodeInfo(sh(20)));

        LineMessage msg = (LineMessage) captureMessage();
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(20), true));

        verify(comm).send(argThat(equalTo(Message.GET(sh(20), 1234L))));
    }

    /**
     * When CHNGD_OWNR is received as a response to a GET, but new owner is not in cluster then re-send GET to the old owner.
     */
    @Test
    public void whenGetAndCHNGD_OWNRAndNoNodeThenGETAgainIgnoreChange() throws Exception {
        PUT(1234L, sh(10), 1, "x");
        INV(1234L, sh(10));
        Mockito.reset(comm); // to enable capture

        cache.runOp(new Op(GET, 1234L, null));

        when(cluster.getMaster(sh(20))).thenReturn(null);

        LineMessage msg = (LineMessage) captureMessage();
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(20), true));

        verify(comm).send(argThat(equalTo(Message.GET(sh(10), 1234L))));
    }

    /**
     * When CHNGD_OWNR is received as a response to a GETX, then re-send GETX to the new owner.
     */
    @Test
    public void whenGetxAndCHNGD_OWNRThenGETXAgain() throws Exception {
        PUT(1234L, sh(10), 1L, "foo");

        when(cluster.getMaster(sh(20))).thenReturn(makeNodeInfo(sh(20)));

        cache.runOp(new Op(GETX, 1234L, null));

        LineMessage msg = (LineMessage) captureMessage();
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(20), true));

        verify(comm).send(argThat(equalTo(Message.GETX(sh(20), 1234L))));
    }

    @Test
    public void whenGetxAndCHNGD_OWNRToYou() throws Exception {
        ListenableFuture<Object> future = cache.doOpAsync(GETX, 1234L, null, null, null);
        LineMessage msg = (LineMessage) captureMessage();
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(10), true));
        assertThat(future.isDone(), is(false));
        cache.receive(Message.PUTX(msg, 1234L, null, 0, 1L, toBuffer("foo")));
        if (hasServer())
            INVACK(1234L, sh(0));
        assertThat(future.isDone(), is(true));
        assertThat(deserialize(future.get()), equalTo("foo"));
    }

    @Test
    public void whenSendToOwnerOfAndCHNGD_OWNRToYou() throws Exception {
        /**
         * When CHNGD_OWNR is received, resend message to new owner
         */
        setCommMsgCounter();
        PUT(1234L, sh(10), 1L, "xxx");
        INV(1234L, sh(10));
        verify(comm).send(argThat(equalTo(Message.INVACK(Message.INV(sh(10), 1234L, sh(-1))).setMessageId(2))));

        MSG msg = Message.MSG(sh(-1), 1234L, true, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.MSG(sh(10), 1234L, true, serialize("foo")).setMessageId(3))));
        assertThat(send.getFuture().isDone(), is(false));

        //when(cluster.getMaster(sh(20))).thenReturn(makeNodeInfo(sh(20)));
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(10), true));
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(10), true)); // twice, but only resend once
        cache.receive(Message.TIMEOUT(msg));
        cache.receive(Message.PUTX(msg, 1234L, null, 0, 1L, toBuffer("xxx")));

        assertThat(send.getFuture().isDone(), is(true));

        try {
            send.getResult();
            fail("TimeoutException not thrown");
        } catch (InterruptedException | ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        }
    }

    /**
     * When INV is received as a response from server to an INV, then re-send GETX to the new owner.
     */
    @Test
    public void whenGetxAndINVFromServerThenGETXAgain() throws Exception {
        assumeTrue(hasServer);
        when(comm.isSendToServerInsteadOfMulticast()).thenReturn(true);
        cache = makeCache(10000);

        cache.runOp(new Op(GETX, 1234L, null));

        verify(comm).send(argThat(equalTo(Message.GETX(sh(-1), 1234L))));

        PUTX(1234L, sh(10), 1L, "foo", 20, 30);

        verify(comm).send(argThat(equalTo(Message.INV(sh(0), 1234L, sh(10)))));

        cache.receive(Message.INV(Message.INV(sh(0), 1234L, sh(10)), 1234, sh(20)));

        verify(comm).send(argThat(equalTo(Message.GETX(sh(20), 1234L))));
        verify(comm, never()).send(argThat(equalTo(Message.INVACK(Message.INV(sh(0), 1234L, sh(10))))));
    }

    /**
     * When NOT_FOUND is received (from peers) as a response to a GET, get from server.
     */
    @Test
    public void whenGetAndNOT_FOUNDAndHasServerThenGETFromServer() throws Exception {
        assumeTrue(hasServer);
        cache.runOp(new Op(GET, id(1234L), null));

        verify(comm).send(argThat(equalTo(Message.GET(sh(-1), id(1234)))));

        cache.receive(Message.NOT_FOUND(Message.GET(sh(-1), id(1234))));

        verify(comm).send(argThat(equalTo(Message.GET(sh(0), id(1234)))));
    }

    /**
     * When NOT_FOUND is received and no server, then throw exception.
     */
    @Test
    public void whenGetAndNOT_FOUNDAndNotHasServerThenThrowException() throws Exception {
        assumeTrue(!hasServer);
        final Op get = new Op(GET, id(1234), null);
        cache.runOp(get);

        cache.receive(Message.NOT_FOUND(Message.GET(sh(-1), id(1234))));

        try {
            get.getResult();
            fail("exception not thrown");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(RefNotFoundException.class)));
        }
    }

    /**
     * When NOT_FOUND is received from server then throw exception.
     */
    @Test
    public void whenGetAndNOT_FOUNDFromServerThenThrowException() throws Exception {
        assumeTrue(hasServer);
        final Op get = new Op(GET, id(1234), null);
        cache.runOp(get);

        cache.receive(Message.NOT_FOUND(Message.GET(sh(0), id(1234))));

        try {
            get.getResult();
            fail("exception not thrown");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(RefNotFoundException.class)));
        }
    }

    /**
     * When MSG is received and not broadcast and line is not found, send CHNGD_OWNER
     */
    @Test
    public void whenMSGAndNoLineThenCHNGD_OWNER() throws Exception {
        final LineMessage msg = Message.MSG(sh(10), 1234L, true, serialize("foo"));
        cache.receive(msg);

        verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(msg, 1234L, sh(-1), false))));
    }

    /**
     * When MSG broadcast is received and line is not found, send ACK
     */
    @Test
    public void whenMSGBcastAndNoLineThenACK() throws Exception {
        final LineMessage msg = Message.MSG(sh(-1), 1234L, true, serialize("foo"));
        cache.receive(msg);

        verify(comm).send(argThat(equalTo(Message.ACK(msg))));
    }

    /**
     * When GET/X is received and line is not owner, send CHNGD_OWNER
     */
    @Test
    public void whenGETAndNotOwnerThenCHNGD_OWNER() throws Exception {
        for (Message.Type getType : new Message.Type[]{Message.Type.GET, Message.Type.GETX}) {
            PUT(1234L, sh(10), 1L, "hello");

            final LineMessage get = new Message.GET(getType, sh(50), 1234L);
            cache.receive(get);

            verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(get, 1234L, sh(10), true))));

            reset();
        }
    }

    /**
     * When GET/X broadcast is received and line is not owner but owner is known, send CHNGD_OWNER
     */
    @Test
    public void whenGETBcastAndKnownOwnerThenCHNGD_OWNER() throws Exception {
        for (Message.Type getType : new Message.Type[]{Message.Type.GET, Message.Type.GETX}) {
            PUT(1234L, sh(10), 1L, "hello");

            final LineMessage get = new Message.GET(getType, sh(-1), 1234L);
            cache.receive(get);

            verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(get, 1234L, sh(10), true))));

            reset();
        }
    }

    /**
     * When GET/X broadcast is received and line owner is unknown, send ACK
     */
    @Test
    public void whenGETBcastAndUnknownOwnerThenACK() throws Exception {
        for (Message.Type getType : new Message.Type[]{Message.Type.GET, Message.Type.GETX}) {
            cache.runOp(new Op(GET, 1234L, null)); // create an I line with unknown owner

            final LineMessage get = new Message.GET(getType, sh(-1), 1234L);
            cache.receive(get);

            verify(comm).send(argThat(equalTo(Message.ACK(get))));

            reset();
        }
    }

    /**
     * When INV is received and line does not exist INVACK
     */
    @Test
    public void whenINVAndNoLineThenINVACK() throws Exception {
        Message.INV inv = Message.INV(sh(10), 1234L, sh(10));
        cache.receive(inv);

        verify(comm).send(argThat(equalTo(Message.INVACK(inv))));
    }

    /**
     * When GETX is receive, inv slaves before responding with PUTX
     */
    @Ignore
    @Test
    public void whenGetXAndHasSlavesThenWaitForSlavesBeforePUTX() throws Exception {
        when(backup.inv(anyLong(), anyShort())).thenReturn(false);

        PUTX(1234L, sh(10), 1L, "hello");

        set(1234L, "bye");
        cache.receive(Message.BACKUPACK(sh(0), 1234L, 2L));

        assertState(1234L, E, null);
        assertThat(cache.getLine(1234L).is(CacheLine.SLAVE), is(true));

        Message.GET getx = Message.GETX(sh(10), 1234L);
        cache.receive(getx);

        verify(backup).inv(1234L, sh(10));
        verify(comm, never()).send(argThat(equalTo(Message.PUTX(getx, 1234L, new short[0], 0, 2L, toBuffer("bye")))));

        cache.receive(Message.INVACK(sh(5), 1234L).setIncoming());

        assertThat(cache.getLine(1234L).is(CacheLine.SLAVE), is(false));
        verify(comm).send(argThat(equalTo(Message.PUTX(getx, 1234L, new short[0], 0, 2L, toBuffer("bye")))));
    }

    /**
     * When INV is receive, inv slaves before responding with INVACK
     */
//    @Test
//    public void whenInvAndHasSlavesThenWaitForSlavesBeforeINVACK() throws Exception {
//        when(backup.inv(anyLong(), anyShort())).thenReturn(false);
//
//        PUTX(1234L, sh(10), 1L, "hello");
//
//        set(1234L, "bye");
//        cache.receive(Message.BACKUPACK(sh(0), 1234L, 2L));
//        cache.receive(Message.GET(sh(20), 1234L));
//
//        assertState(1234L, S, null);
//        assertThat(cache.getLine(1234L).is(CacheLine.SLAVE), is(true));
//
//        Message.INV inv = Message.INV(sh(10), 1234L, sh(10));
//        cache.receive(inv);
//
//        verify(backup).inv(1234L, sh(10));
//        verify(comm, never()).send(argThat(equalTo(Message.INVACK(inv))));
//
//        cache.receive(Message.INVACK(sh(5), 1234L).setIncoming());
//
//        assertThat(cache.getLine(1234L).is(CacheLine.SLAVE), is(false));
//        verify(comm).send(argThat(equalTo(Message.INVACK(inv))));
//    }
    /**
     * A put allocates ID and line
     */
    @Test
    public void whenPutThenAllocateId() throws Exception {
        final RefAllocationsListener listener = hasServer ? null : getRefAllocationListener(cache.getRefAllocator());
        Object res;
        Op put;

        put = new Op(PUT, -1L, serialize("1111"), null);
        res = cache.runOp(put);

        assertThat(res, is(PENDING));
        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT))));
        else
            verify(cluster).allocateRefs(anyInt());

        if (hasServer)
            cache.receive(Message.ALLOCED_REF(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT), 100, 3));
        else
            listener.refsAllocated(100, 3);

        res = put.getResult();

        assertThat((Long) res, is(100L));

        res = get(100L);
        assertThat((String) res, is("1111"));
        assertModified(100L, true);
        assertThat(cache.getLine(100L).isLocked(), is(true));

        res = doOp(PUT, -1L, serialize("2222"));
        assertThat((Long) res, is(101L));
        assertModified(101L, true);
        assertThat(cache.getLine(101L).isLocked(), is(true));
        res = get(101L);
        assertThat((String) res, is("2222"));

        if (hasServer)
            verify(comm, times(2)).send(argThat(equalTo(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT))));
        else
            verify(cluster, times(2)).allocateRefs(anyInt()); // allocates after id > (min + max) / 2

        res = doOp(PUT, -1L, serialize("3333"));
        assertThat((Long) res, is(102L));
        assertModified(102L, true);
        assertThat(cache.getLine(102L).isLocked(), is(true));
        res = get(102);
        assertThat((String) res, is("3333"));

        put = new Op(PUT, -1L, serialize("4444"), null);
        res = cache.runOp(put);

        assertThat(res, is(PENDING));

        if (hasServer)
            cache.receive(Message.ALLOCED_REF(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT), 200, 3));
        else
            listener.refsAllocated(200, 3);

        res = put.getResult();

        assertThat((Long) res, is(200L));

        res = doOp(GET, 200L);
        assertThat(deserialize((byte[]) res), is("4444"));
        assertModified(200L, true);
        assertThat(cache.getLine(200L).isLocked(), is(true));

        res = doOp(PUT, -1L, serialize("5555"));
        assertThat((Long) res, is(201L));
        assertModified(201L, true);
        assertThat(cache.getLine(201L).isLocked(), is(true));
        res = get(201L);
        assertThat((String) res, is("5555"));

        if (hasServer)
            verify(comm, times(3)).send(argThat(equalTo(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT))));
        else
            verify(cluster, times(3)).allocateRefs(anyInt()); // allocates after id > (min + max) / 2

        res = doOp(PUT, -1L, serialize("6666"));
        assertThat((Long) res, is(202L));
        assertModified(202L, true);
        assertThat(cache.getLine(202L).isLocked(), is(true));
        res = get(202L);
        assertThat((String) res, is("6666"));
    }

    /**
     * When an owned line is getx, INV all sharers, including server if hasServer(), but if we have a server, we can get and even
     * set. We don't wait for ACKs to complete.
     */
    @Test
    public void whenPUTXAndGetXThenINV() throws Exception {
        PUTX(1, sh(10), 1, "hello", 20, 30);

        Object res = cache.runOp(new Op(GETX, 1L, null));

        assertState(1, O, E);
        assertVersion(1, 1);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 1L, sh(10)))));
        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.INV(Comm.SERVER, 1L, sh(10)))));

        // assertThat(deserialize((byte[]) res), is("hello")); // not true since c62a579 when the optimization in transitionToE has been removed
    }

    /**
     * When an owned line is getx, INV all sharers, including server if hasServer(), but we don't wait for ACKs to complete if
     * hasServer.
     */
    @Test
    @Ignore // not true since c62a579 when the optimization in transitionToE has been removed
    public void whenGetxAndPUTXThenDontWaitForINVACKIffServer() throws Exception {
        Op getx = new Op(GETX, 1L, null);
        Object res = cache.runOp(getx);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.GETX(sh(-1), 1L))));

        if (hasServer)
            PUTX(1L, sh(10), 1L, "hello", 20, 30);
        else
            PUTX(1L, sh(10), 1L, "hello", 10, 20, 30);

        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 1L, sh(10)))));
        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.INV(Comm.SERVER, 1L, sh(10)))));
        else
            verify(comm).send(argThat(equalTo(Message.INV(sh(10), 1L, sh(10)))));

        if (hasServer) {
            assertThat(getx.getFuture().isDone(), is(true));
            assertThat(deserialize((byte[]) getx.getResult()), is("hello"));
        } else {
            assertThat(getx.getFuture().isDone(), is(false));
        }
    }

    /**
     * When an owned line is getx and isSendToServerInsteadOfMulticast then wait for an INVACK from the server.
     */
    @Test
    public void whenGetxAndPUTXAndSendToServerThenDoWaitForINVACKFromServer() throws Exception {
        assumeTrue(hasServer);
        when(comm.isSendToServerInsteadOfMulticast()).thenReturn(true);
        cache = makeCache(10000);

        Op getx = new Op(GETX, 1L, null);
        Object res = cache.runOp(getx);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.GETX(sh(-1), 1L))));

        PUTX(1L, sh(10), 1L, "hello", 20, 30);

        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(Comm.SERVER, 1L, sh(10)))));

        assertThat(getx.getFuture().isDone(), is(false));

        // - since c62a579
        cache.receive(Message.INVACK(Message.INV(sh(20), 1L, sh(10))));
        cache.receive(Message.INVACK(Message.INV(sh(30), 1L, sh(10))));
        assertThat(getx.getFuture().isDone(), is(false));
        // - 

        cache.receive(Message.INVACK(Message.INV(Comm.SERVER, 1L, sh(10))));

        assertThat(getx.getFuture().isDone(), is(true));
        assertThat(deserialize((byte[]) getx.getResult()), is("hello"));
    }

    /**
     * When an owned line is getx and no server then wait for an INVACK from the previous owner (means that it inved its slaves).
     */
    @Test
    @Ignore // not true since c62a579
    public void whenGetxAndPUTXAndNoServerThenDoWaitForINVACKFromPreviousOwner() throws Exception {
        assumeTrue(!hasServer);

        Op getx = new Op(GETX, 1L, null);
        Object res = cache.runOp(getx);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.GETX(sh(-1), 1L))));

        PUTX(1L, sh(10), 1L, "hello", 10, 20, 30);

        verify(comm).send(argThat(equalTo(Message.INV(sh(10), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 1L, sh(10)))));

        assertThat(getx.getFuture().isDone(), is(false));

        cache.receive(Message.INVACK(sh(10), 1L));

        assertThat(getx.getFuture().isDone(), is(true));
        assertThat(deserialize((byte[]) getx.getResult()), is("hello"));
    }

    /**
     * When a line is S and we getx, the current node will be one of the sharers. It must not try to INV itself.
     */
    @Test
    public void whenSAndGetxDontINVSelf() throws Exception {
        PUT(1L, sh(10), 1, "hello");
        PUTX(1L, sh(10), 1, "hello", 5, 20);

        cache.runOp(new Op(GETX, 1L, null));
        assertState(1, O, E);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
    }

    /**
     * Don't respond to GET message if all INVACKs have not yet been received.
     */
    @Test
    public void whenPUTXAndGetXThenDontRespondToRequests() throws Exception {
        PUTX(1, sh(10), 1, "hello", 20, 30);

        Object res = cache.runOp(new Op(GETX, 1L, null));

//        if (hasServer()) {
//            assertThat(res, is(not(PENDING)));
//            cache.unlockLine(cache.getLine(1L), null);
//        }
        cache.receive(Message.GET(sh(100), 1));

        assertState(1, O, E);
        assertVersion(1, 1);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 1L, sh(10)))));
        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.INV(Comm.SERVER, 1L, sh(10)))));
        verify(comm, never()).send(argThat(equalTo(Message.PUT(sh(100), 1L, 1L, null))));
    }

    /**
     * Once all INVACKs have been received, respond to GET and set state from E to O.
     */
    @Test
    @Ignore // - not true since c62a579
    public void whenPUTXAndGetXAndAcksAndRequestsThenRespond() throws Exception {
        PUTX(1, sh(10), 1, "hello", 20, 30);

        Op getx = new Op(GETX, 1L, null);
        Object res = cache.runOp(getx);

        assertThat(res, is(not(PENDING))); // - not true since c62a579
        cache.unlockLine(cache.getLine(1L), null);

        cache.receive(Message.GET(sh(100), 1));
        cache.receive(Message.INVACK(Message.INV(sh(20), 1L, sh(10))));
        cache.receive(Message.INVACK(Message.INV(sh(30), 1L, sh(10))));
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1L, sh(10))));
        cache.receive(Message.BACKUPACK(sh(0), 1L, 1L));

        assertState(1, O, null); // after c62a579 this would be O -> E b/c op GETX is still pending
        assertVersion(1, 1);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 1L, sh(10)))));
        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(100), 1), 1L, 1L, toBuffer("hello")))));

        assertThat(deserialize(res), is("hello"));
    }

    /**
     * When PUT is received, the get op is completed
     */
    @Test
    public void whenPUTThenCompleteGet() throws Exception {
        final Op get = new Op(GET, 1L, null);
        Object res = cache.runOp(get);

        assertThat(res, is(PENDING));
        assertState(1, I, S);

        PUT(1, sh(10), 1, "hello");

        assertState(1, S, null);
        assertThat(deserialize(get.getResult()), is("hello"));
    }

    /**
     * When PUTX is received, the getx op is completed
     */
//    @Test
    public void whenPUTXThenCompleteGetx() throws Exception {
        final Op getx = new Op(GETX, 1L, null);
        Object res = cache.runOp(getx);

        assertThat(res, is(PENDING));
        assertState(1, I, O);

        PUTX(1, sh(10), 1, "hello", 20, 30);

        assertState(1, O, E);

        if (hasServer)
            assertThat(deserialize(getx.getResult()), is("hello"));

        cache.receive(Message.INVACK(Message.INV(sh(20), 1L, sh(10))));
        cache.receive(Message.INVACK(Message.INV(sh(30), 1L, sh(10))));
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), 1L, sh(10))));

        assertThat(deserialize(getx.getResult()), is("hello"));

        assertState(1, E, null);
        assertOwner(1, sh(5));
    }

    /**
     * Tests execution of pending messages on a locked line.
     */
    @Test
    public void testPendingMessages1() throws Exception {
        //public void whenLockedThenDontProecssMessagesUntilRelease() {
        for (Op.Type getType : new Op.Type[]{GETX, GETS}) {
            PUTX(1234L, sh(1), 1, "hello");
            cache.runOp(new Op(getType, 1234L, null));

            cache.receive(Message.GET(sh(10), 1234L));
            cache.receive(Message.GET(sh(20), 1234L));
            cache.receive(Message.GETX(sh(30), 1234L));

            if (hasServer)
                verify(comm).send(argThat(equalTo(Message.INV(sh(0), 1234L, sh(1)))));
            verifyNoMoreInteractions(comm);

            cache.release(1234);
            cache.receive(Message.BACKUPACK(sh(0), 1234L, 1L));

            verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(10), 1234L), 1234L, 1L, toBuffer("hello")))));
            verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(20), 1234L), 1234L, 1L, toBuffer("hello")))));
            verify(comm).send(argThat(equalTo(Message.PUTX(Message.GETX(sh(30), 1234L), 1234L, sh(10, 20), 0, 1L, toBuffer("hello")))));

            reset();
        }
    }

    /**
     * Tests execution of pending messages on a locked line.
     */
    @Test
    public void testPendingMessages2() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");
        cache.runOp(new Op(GETX, 1234L, null));

        cache.receive(Message.GET(sh(10), 1234L));
        cache.receive(Message.GETX(sh(20), 1234L));
        cache.receive(Message.GET(sh(30), 1234L));

        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.INV(sh(0), 1234L, sh(1)))));
        verifyNoMoreInteractions(comm);

        cache.release(1234);
        cache.receive(Message.BACKUPACK(sh(0), 1234L, 1L));

        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(10), 1234L), 1234L, 1L, toBuffer("hello")))));
        verify(comm).send(argThat(equalTo(Message.PUTX(Message.GETX(sh(20), 1234L), 1234L, new short[]{sh(10)}, 0, 1L, toBuffer("hello")))));
        verify(comm).send(argThat(equalTo(Message.CHNGD_OWNR(Message.GET(sh(30), 1234L), 1234L, sh(20), false)))); // b/c hasServer: I, no-server: S
    }

    /**
     * When there are messages waiting (b/c of MODIFIED) and the line isn't locked, new ops will wait as well and let the messages
     * go first to prevent starvation.
     */
    @Test
    public void testPendingOps() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");
        Mockito.reset(comm); // forget sends

        assertLocked(1234L, false);
        assertState(1234, E, null);

        set(1234L, "bye");
        set(1234L, "1234"); // this length must be less than the sets' buffer length to make sure a new buffer is allocated to them
        // otherwise, if this is longer, the sets will write over the buffer. This is OK in practice, because send will have been finished
        // by then, but here, mockito is capturing pointers to the buffers and verifying them after the set.

        assertModified(1234, true);
        assertVersion(1234, 3);

        assertThat(get(1234L), is("1234"));

        GET(1234L, sh(10)); // now get is waiting

        verify(comm, never()).send(any(Message.class));

        Object res;
        res = cache.runOp(new Op(SET, 1234L, serialize("why?????"), null)); // op is waiting because GET is waiting

        assertThat(res, is(PENDING));

        res = cache.runOp(new Op(SET, 1234L, serialize("because!!!!!"), null)); // op is waiting because GET is waiting

        assertThat(res, is(PENDING));

        cache.receive(Message.BACKUPACK(sh(0), 1234L, 3L));

        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(10), 1234L), 1234L, 3L, toBuffer("1234"))))); // handling the GET
        verify(comm).send(argThat(equalTo(Message.INV(sh(10), 1234L, sh(5))))); // for the sets

        INVACK(1234L, sh(10)); // added b/c of c62a579

        assertThat(get(1234L), is("because!!!!!"));
        assertModified(1234, true);
        assertVersion(1234, 5);

//        assertState(1234L, O, E); -- since c62a579
//        INVACK(1234L, sh(10));
        assertState(1234L, E, null);
    }

    /**
     * When there are messages waiting and the line isn't locked (b/c O -> E), new ops will execute
     */
//    @Test
    public void testPendingOps2() throws Exception {
        PUTX(1234L, sh(1), 1, "hello", 20);
        if (hasServer()) {
            verify(comm).send(argThat(equalTo(Message.INV(sh(0), 1234L, sh(1)))));
            cache.receive(Message.INVACK(Message.INV(sh(0), 1234L, sh(1))));
        }

        assertLocked(1234L, false);
        assertState(1234L, O, null);

        System.out.println("111: " + cache.getLine(1234L));

        set(1234L, "bye");
        set(1234L, "1234"); // this length must be less than the sets' buffer length to make sure a new buffer is allocated to them
        // otherwise, if this is longer, the sets will write over the buffer. This is OK in practice, because send will have been finished
        // by then, but here, mockito is capturing pointers to the buffers and verifying them after the set.

        assertState(1234L, O, E);
        assertModified(1234, true);
        assertVersion(1234, 3);
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 1234L, sh(1))))); // we verify just for the sake of verifyNoMoreInteractions(comm) later

        assertThat(get(1234L), is("1234"));

        GET(1234L, sh(10)); // now get is waiting

        verifyNoMoreInteractions(comm);

        Object res;
        res = cache.runOp(new Op(SET, 1234L, serialize("why?????"), null)); // op is waiting because GET is waiting

        assertThat(res, is(not(PENDING)));

        res = cache.runOp(new Op(SET, 1234L, serialize("because!!!!!"), null)); // op is waiting because GET is waiting

        assertThat(res, is(not(PENDING)));

        cache.receive(Message.BACKUPACK(sh(0), 1234L, 5L));

        verifyNoMoreInteractions(comm);

        cache.receive(Message.INVACK(Message.INV(sh(20), 1234L, sh(1))));

        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(10), 1234L), 1234L, 5L, toBuffer("because!!!!!"))))); // handling the GET

        assertThat(get(1234L), is("because!!!!!"));
        assertModified(1234, false);
        assertVersion(1234, 5);
        assertState(1234L, O, null);
    }

    @Test
    public void whenPushThenPUTtoTarget() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");

        cache.runOp(new Op(PUSH, 1234L, sh(20, 30), null));

        verify(comm).send(argThat(equalTo(Message.PUT(sh(20), 1234L, 1L, toBuffer("hello")))));
        verify(comm).send(argThat(equalTo(Message.PUT(sh(30), 1234L, 1L, toBuffer("hello")))));
    }

    @Test
    public void whenPushxThenPUTXtoTarget() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");

        cache.runOp(new Op(PUSHX, 1234L, sh(20), null));
        cache.runOp(new Op(PUSHX, 1234L, sh(30), null));

        verify(comm).send(argThat(equalTo(Message.PUTX(sh(20), 1234L, sh(), 0, 1L, toBuffer("hello")))));
        verify(comm, never()).send(argThat(equalTo(Message.PUTX(sh(30), 1234L, sh(), 0, 1L, toBuffer("hello")))));
    }

    @Test
    public void whenPushAndWrongStateThenIgnore() throws Exception {
        PUT(1234L, sh(1), 1, "hello");

        cache.runOp(new Op(PUSH, 1234L, sh(20, 30), null));
        cache.runOp(new Op(PUSH, 1111L, sh(10, 20), null));
        cache.runOp(new Op(PUSHX, 1234L, sh(20), null));
        cache.runOp(new Op(PUSHX, 1111L, sh(10), null));

        verify(comm, never()).send(any(Message.class));
    }

    /**
     * When line is M, allow local operations (get and set).
     */
    @Test
    public void whenSetThenAllowLocalOps() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");

        assert !cache.getLine(1234).isLocked();

        assertState(1234, E, null);

        set(1234L, "bye");

        assertModified(1234, true);
        assertVersion(1234, 2);

        assertThat(get(1234L), is("bye"));

        // b/c line was not locked, set's autorelease issues a backup
        verify(backup).backup(1234, 2);

        set(1234L, "woohoo");

        assertModified(1234, true);
        assertVersion(1234, 3);

        assertThat(get(1234L), is("woohoo"));

        verify(backup).backup(1234, 3);
    }

    /**
     * When line is M and and cache is in synchronous mode, don't allow local get until BACKUPACK
     */
    @Ignore
    @Test
    public void whenSetSetAndSynchronousDontGetUntilBACKUPACK() throws Exception {
        cache = makeCache(true);

        PUTX(1234L, sh(1), 1, "hello");

        assert !cache.getLine(1234).isLocked();

        assertState(1234, E, null);

        set(1234L, "bye");

        assertModified(1234, true);
        assertVersion(1234, 2);

        Op get = new Op(GET, 1234L, null);
        Object res = cache.runOp(get);

        assertThat(res, is(PENDING));
        verify(backup).backup(1234, 2); // b/c line was not locked, set's autorelease issues a backup

        set(1234L, "woohoo");

        verify(backup).backup(1234, 3); // b/c line was not locked, set's autorelease issues a backup

        assertModified(1234, true);
        assertVersion(1234, 3);

        cache.receive(Message.BACKUPACK(sh(0), 1234L, 3L));

        assertThat(get(1234L), is("woohoo"));

        assertThat(get.getFuture().isDone(), is(true));
        assertThat(deserialize((byte[]) get.getResult()), is("woohoo"));
    }

    /**
     * When updating a line, no messages can be processed until backup has completed.
     */
    @Test
    public void whenSetThenDontProecssMessagesUntilBACKUPACK() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");

        assert !cache.getLine(1234).isLocked();

        assertState(1234, E, null);

        set(1234L, "bye");

        assertModified(1234, true);
        assertVersion(1234, 2);

        cache.receive(Message.GET(sh(100), 1234));

        verify(backup).backup(1234, 2);
        verify(comm, never()).send(argThat(equalTo(Message.PUT(Message.GET(sh(100), 1234L), 1234L, 2L, toBuffer("bye")))));

        cache.receive(Message.BACKUPACK(sh(0), 1234L, 2L));

        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(100), 1234L), 1234L, 2L, toBuffer("bye")))));
    }

    /**
     * When line is M, hold push/x
     */
    @Test
    public void whenSetThenDontPushUntilBACKUPACK() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");
        assertState(1234, E, null);

        set(1234L, "bye");

        assert !cache.getLine(1234).isLocked();

        assertModified(1234, true);
        assertVersion(1234, 2);

        cache.runOp(new Op(PUSH, 1234L, sh(10, 20), null));
        GET(1234L, sh(100));
        //cache.runOp(new Op(PUSHX, 1234L, sh(30)));

        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.INV(sh(0), 1234L, sh(1)))));
        verifyNoMoreInteractions(comm);

        cache.receive(Message.BACKUPACK(sh(0), 1234L, 2L));

        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(100), 1234L), 1234L, 2L, toBuffer("bye")))));
        verify(comm).send(argThat(equalTo(Message.PUT(sh(10), 1234L, 2L, toBuffer("bye")))));
        verify(comm).send(argThat(equalTo(Message.PUT(sh(20), 1234L, 2L, toBuffer("bye")))));
        // By the time we get to pushx, we're not exclusive, so nothing will be sent
        verify(comm, never()).send(argThat(equalTo(Message.PUTX(sh(30), 1234L, hasServer() ? new short[]{sh(0)} : sh(), 0, 2L, toBuffer("bye")))));
    }

    /**
     * When putting a new line, no messages can be processed until backup has completed.
     */
    @Test
    public void whenPutThenDontProecssMessagesUntilBACKUPACK() throws Exception {
        long id = put("hello");

        cache.release(id);

        assert !cache.getLine(id).isLocked();

        assertState(id, E, null);

        assertModified(id, true);
        assertVersion(id, 1);

        cache.receive(Message.GET(sh(100), id));

        verify(backup).backup(id, 1);
        verify(comm, never()).send(argThat(equalTo(Message.PUT(Message.GET(sh(100), id), id, 1L, toBuffer("hello")))));

        cache.receive(Message.BACKUPACK(sh(0), id, 1L));

        verify(comm).send(argThat(equalTo(Message.PUT(Message.GET(sh(100), id), id, 1L, toBuffer("hello")))));
    }

    /**
     * When multiple sets are issued, only backup when releasing the line.
     */
    @Test
    public void onlyBackupUponEndTransaction() throws Exception {
        Transaction txn = cache.beginTransaction();

        cache.runOp(new Op(GETX, 1234L, (Persistable) null, null, txn));

        PUTX(1234L, sh(1), 1, "hello");

        assert cache.getLine(1234).isLocked();

        assertState(1234, E, null);

        set(1234L, "bye", txn);

        assertModified(1234, true);
        assertVersion(1234, 2);

        assertThat(get(1234L), is("bye"));

        // b/c line was locked, set does not autorelease and does not issue a backup
        verify(backup, never()).backup(anyLong(), anyLong());

        set(1234L, "woohoo", txn);

        assertModified(1234, true);
        assertVersion(1234, 3);

        assertThat(get(1234L), is("woohoo"));

        cache.endTransaction(txn, false);

        verify(backup).backup(1234, 3);
    }

    /**
     * When we receive GET or GETX, we flush the backups
     */
    @Test
    public void whenGETThenFlushBackups() {
        PUTX(1234L, sh(1), 1, "11");
        cache.runOp(new Op(SET, 1234L, serialize("22"), null));

        assertState(1234, E, null);
        assertModified(1234, true);

        verify(backup).backup(anyLong(), anyLong());
        verify(backup, never()).flush();

        cache.receive(Message.GET(sh(100), 1234));

        verify(backup).flush();
    }

    /**
     * Make sure we allow stale reads as long as there were no PUTs from the same owner.
     */
    @Test
    public void testStaleReads1() {
        PUT(101L, sh(10), 1L, "1");
        PUT(102L, sh(10), 1L, "2");
        PUT(103L, sh(10), 1L, "3");
        PUT(104L, sh(10), 1L, "4");
        PUT(201L, sh(20), 1L, "1");
        PUT(202L, sh(20), 1L, "2");

        assertThat(cache.runOp(new Op(GET, 101, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 103, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 201, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 202, null)), is(not(PENDING)));

        INV(101L, sh(10));
        INV(103L, sh(10));
        INV(104L, sh(10));
        INV(201L, sh(10));
        INV(202L, sh(20));

        assertThat(cache.runOp(new Op(GET, 101, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 103, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(not(PENDING)));

        PUT(103L, sh(10), 2L, "3");

        assertThat(cache.runOp(new Op(GET, 103, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 101, null)), is(PENDING));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(PENDING));

        assertThat(cache.runOp(new Op(GET, 201, null)), is(PENDING));
        assertThat(cache.runOp(new Op(GET, 202, null)), is(not(PENDING)));
    }

    /**
     * A PUT of a new line purges all I lines.
     */
    @Test
    public void testStaleReads2() {
        PUT(101L, sh(10), 1L, "1");
        PUT(102L, sh(10), 1L, "2");
        PUT(103L, sh(10), 1L, "3");
        PUT(104L, sh(10), 1L, "4");
        PUT(201L, sh(20), 1L, "1");
        PUT(202L, sh(20), 1L, "2");

        assertThat(cache.runOp(new Op(GET, 101, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 103, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 201, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 202, null)), is(not(PENDING)));

        INV(101L, sh(10));
        INV(103L, sh(10));
        INV(104L, sh(10));
        INV(201L, sh(20));

        assertThat(cache.runOp(new Op(GET, 101, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 103, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(not(PENDING)));

        PUT(105L, sh(30), 1L, "5");

        assertThat(cache.runOp(new Op(GET, 103, null)), is(PENDING));
        assertThat(cache.runOp(new Op(GET, 101, null)), is(PENDING));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(PENDING));

        assertThat(cache.runOp(new Op(GET, 201, null)), is(PENDING));
        assertThat(cache.runOp(new Op(GET, 202, null)), is(not(PENDING)));
    }

    /**
     * Eviction does not prevent stale reads.
     */
    @Test
    public void testStaleReads4() {
        PUT(101L, sh(10), 1L, "1");
        PUT(102L, sh(10), 1L, "2");
        PUT(103L, sh(10), 1L, "3");
        PUT(104L, sh(10), 1L, "4");

        assertThat(cache.runOp(new Op(GET, 101, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 102, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 103, null)), is(not(PENDING)));
        assertThat(cache.runOp(new Op(GET, 104, null)), is(not(PENDING)));

        evict(101, false);
        evict(102, false);

        PUT(103L, sh(10), 2L, "3");
        INV(104L, sh(10));

        assertThat(cache.runOp(new Op(GET, 104, null)), is(not(PENDING)));

        INV(103L, sh(10));
        PUT(103L, sh(10), 3L, "3");

        assertThat(cache.runOp(new Op(GET, 104, null)), is(PENDING));
    }

    @Test
    public void testCacheListeners() {
        CacheListener listener = mock(CacheListener.class);

        cache.addCacheListener(listener);

        PUT(100L, sh(10), 1L, "hello");

        verify(listener).received(cache, 100L, 1L, toBuffer("hello"));

        PUT(100L, sh(10), 2L, "bye");

        verify(listener).received(cache, 100L, 2L, toBuffer("bye"));

        INV(100L, sh(10));

        verify(listener).invalidated(cache, 100L);

        evict(100L, false);

        verify(listener).evicted(cache, 100L);
    }

    @Test
    public void testCacheLineListeners() throws Exception {
        CacheListener listener = mock(CacheListener.class);

        doOp(LSTN, 100L, listener);

        PUT(100L, sh(10), 1L, "hello");

        verify(listener).received(cache, 100L, 1L, toBuffer("hello"));

        PUT(100L, sh(10), 2L, "bye");

        verify(listener).received(cache, 100L, 2L, toBuffer("bye"));

        INV(100L, sh(10));

        verify(listener).invalidated(cache, 100L);

        evict(100L, false);

        verify(listener).evicted(cache, 100L);

        PUT(100L, sh(20), 3L, "xxx");

        verifyNoMoreInteractions(listener); // listener gone after eviction
    }

    /**
     * Local operations such as put and get do not notify listeners (but evict does)!
     */
    @Test
    public void whenLocalInteractionThenDontNotifyListeners() throws Exception {
        CacheListener listener1 = mock(CacheListener.class);
        CacheListener listener2 = mock(CacheListener.class);

        cache.addCacheListener(listener1);

        long id = put("hello");

        doOp(LSTN, id, listener2);

        set(id, "bye");
        assert get(id).equals("bye");
        set(id, "xxx");
        assert get(id).equals("xxx");

        del(id);
        //evict(id, false);

        verify(listener1).evicted(cache, id);
        verify(listener2).evicted(cache, id);

        verifyNoMoreInteractions(listener1);
        verifyNoMoreInteractions(listener2);
    }

    /**
     * A cache listener is able to set a line listener.
     */
    @Test
    public void testSetLineListenerInCacheListener() throws Exception {
        final CacheListener lineListener = mock(CacheListener.class);
        cache.addCacheListener(new AbstractCacheListener() {
            @Override
            public void received(co.paralleluniverse.galaxy.Cache cache, long id, long version, ByteBuffer data) {
                try {
                    doOp(LSTN, id, lineListener);
                } catch (TimeoutException e) {
                    throw new AssertionError(e);
                }
            }
        });

        PUT(100L, sh(10), 1L, "hello");

        PUT(100L, sh(10), 2L, "bye");

        verify(lineListener).received(cache, 100L, 2L, toBuffer("bye"));

        INV(100L, sh(10));

        verify(lineListener).invalidated(cache, 100L);

        evict(100L, false);

        verify(lineListener).evicted(cache, 100L);

        PUT(100L, sh(20), 3L, "xxx");

        verifyNoMoreInteractions(lineListener); // listener gone after eviction
    }

    @Test
    public void testDel() throws Exception {
        PUTX(1234L, sh(1), 1, "hello");

        del(1234L);

        assertThat(cache.getLine(1234L).is(CacheLine.DELETED), is(true));

        if (hasServer()) {
            assertLocked(1234L, false);
            verify(comm).send(argThat(equalTo(Message.DEL(Comm.SERVER, 1234L))));

            cache.receive(Message.INVACK(Comm.SERVER, 1234L));
        }

        assertState(1234L, I, null);
    }

    //@Test
    public void whenDeletedAndGETThenNOT_FOUND() {
        pending();
    }

    @Test
    public void testSend() throws Exception {
        MSG msg = Message.MSG(sh(15), -1L, true, serialize("hello"));
        cache.send(msg);

        verify(comm).send(argThat(equalTo(msg)));
    }

    /**
     * When I'm the owner, message is immediately received (through shortCircuitReceive)
     */
    @Test
    public void testSendToOwner1() {
        MessageReceiver receiver = mock(MessageReceiver.class);

        cache.setReceiver(receiver);

        PUTX(1234L, sh(1), 1, "hello");

        cache.runOp(new Op(SEND, 1234, Message.MSG(sh(-1), 1234L, true, serialize("foo")), null));

        verify(receiver).receive(argThat(equalTo(Message.MSG(sh(5), 1234L, true, serialize("foo")))));
    }

    /**
     * When line not found then broadcast message
     */
    @Test
    public void testSendToOwner2() throws Exception {
        setCommMsgCounter();
        MSG msg = Message.MSG(sh(-1), 1234L, true, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.MSG(sh(-1), 1234L, true, serialize("foo")).setMessageId(1))));
        assertThat(send.getFuture().isDone(), is(false));

        cache.receive(Message.MSGACK(msg));

        assertThat(send.getResult(), is(nullValue()));
    }

    /**
     * When INV is received, resend message to new owner
     */
    @Test
    public void testSendToOwner3() throws Exception {
        setCommMsgCounter();
        PUT(1234L, sh(10), 1L, "xxx");

        MSG msg = Message.MSG(sh(-1), 1234L, true, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.MSG(sh(10), 1234L, true, serialize("foo")).setMessageId(2))));
        assertThat(send.getFuture().isDone(), is(false));

        INV(1234L, sh(20));
        INV(1234L, sh(20)); // twice, but only resend once
        verify(comm).send(argThat(equalTo(Message.INVACK(Message.INV(sh(20), 1234L, sh(-1))).setMessageId(3))));
        verify(comm).send(argThat(equalTo(Message.MSG(sh(20), 1234L, true, serialize("foo")).setMessageId(4))));
        verify(comm).send(argThat(equalTo(Message.INVACK(Message.INV(sh(20), 1234L, sh(-1))).setMessageId(5))));
        assertThat(send.getFuture().isDone(), is(false));

        INV(1234L, sh(30));
        INV(1234L, sh(30)); // twice, but only resend once

        verify(comm).send(argThat(equalTo(Message.INVACK(Message.INV(sh(30), 1234L, sh(-1))).setMessageId(6))));
        verify(comm).send(argThat(equalTo(Message.MSG(sh(30), 1234L, true, serialize("foo")).setMessageId(7))));
        assertThat(send.getFuture().isDone(), is(false));

        cache.receive(Message.MSGACK(msg));

        assertThat(send.getResult(), is(nullValue()));
    }

    /**
     * When CHNGD_OWNR is received, resend message to new owner
     */
    @Test
    public void testSendToOwner4() throws Exception {
        setCommMsgCounter();
        PUT(1234L, sh(10), 1L, "xxx");
        INV(1234L, sh(10));
        verify(comm).send(argThat(equalTo(Message.INVACK(Message.INV(sh(10), 1234L, sh(-1))).setMessageId(2))));

        MSG msg = Message.MSG(sh(-1), 1234L, true, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(PENDING));
        verify(comm).send(argThat(equalTo(Message.MSG(sh(10), 1234L, true, serialize("foo")).setMessageId(3))));
        assertThat(send.getFuture().isDone(), is(false));

        when(cluster.getMaster(sh(20))).thenReturn(makeNodeInfo(sh(20)));

        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(20), true));
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(20), true)); // twice, but only resend once

        verify(comm).send(argThat(equalTo(Message.MSG(sh(20), 1234L, true, serialize("foo")).setMessageId(4))));
        assertThat(send.getFuture().isDone(), is(false));

        when(cluster.getMaster(sh(30))).thenReturn(makeNodeInfo(sh(30)));

        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(30), true));
        cache.receive(Message.CHNGD_OWNR(msg, 1234L, sh(30), true)); // twice, but only resend once

        verify(comm).send(argThat(equalTo(Message.MSG(sh(30), 1234L, true, serialize("foo")).setMessageId(5))));
        assertThat(send.getFuture().isDone(), is(false));

        cache.receive(Message.MSGACK(msg));

        assertThat(send.getResult(), is(nullValue()));
    }

    @Test
    public void testSend1() throws Exception {
        CacheListener listener = mock(CacheListener.class);

        doOp(LSTN, 1234L, listener);

        PUTX(1234L, sh(10), 1L, "xxx");

        MSG msg = Message.MSG(sh(-1), 1234L, false, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(not(PENDING)));

        verify(listener).messageReceived(serialize("foo"));
    }

    @Test
    public void testSend2() throws Exception {
        CacheListener listener = mock(CacheListener.class);

        PUTX(1234L, sh(10), 1L, "xxx");

        MSG msg = Message.MSG(sh(-1), 1234L, false, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(not(PENDING)));

        verify(listener, never()).messageReceived(any(byte[].class));

        doOp(LSTN, 1234L, listener);

        verify(listener).messageReceived(serialize("foo"));
    }

    @Test
    public void testSend3() throws Exception {
        setCommMsgCounter();
        CacheListener listener = mock(CacheListener.class);

        doOp(LSTN, 1234L, listener);

        PUT(1234L, sh(10), 1L, "xxx");

        MSG msg = Message.MSG(sh(-1), 1234L, false, serialize("foo"));
        Op send = new Op(SEND, 1234, msg, null);
        Object res = cache.runOp(send);

        assertThat(res, is(PENDING));

        verify(listener, never()).messageReceived(any(byte[].class));

        verify(comm).send(argThat(equalTo(Message.MSG(sh(10), 1234L, false, serialize("foo")).setMessageId(2))));
    }

    @Test
    public void whenPendingMSGsAndGETXThenSend() throws Exception {
        PUTX(1234L, sh(10), 1L, "xxx");

        cache.receive(Message.MSG(sh(20), 1234L, false, serialize("foo")));
        cache.receive(Message.MSG(sh(20), 1234L, false, serialize("bar")));

        GETX(1234, sh(30));

        InOrder inOrder = inOrder(comm);
        inOrder.verify(comm).send(argThat(equalTo(Message.PUTX(Message.GET(sh(30), 1234L), 1234L, new short[0], 2, 1, toBuffer("xxx")))));
        inOrder.verify(comm).send(argThat(equalTo(Message.MSG(sh(30), 1234L, false, true, serialize("foo")).setReplyRequired(false))));
        inOrder.verify(comm).send(argThat(equalTo(Message.MSG(sh(30), 1234L, false, true, serialize("bar")).setReplyRequired(false))));
    }

    @Test
    public void whenPUTXandPendingAndGETXThenSend() throws Exception {
        PUTX(1234L, sh(10), 2, 1L, "xxx");

        GETX(1234, sh(30));

        verify(comm, never()).send(argThat(equalTo(Message.PUTX(Message.GET(sh(30), 1234L), 1234L, new short[0], 2, 1, toBuffer("xxx")))));

        cache.receive(Message.MSG(sh(20), 1234L, false, true, serialize("foo")));

        verify(comm, never()).send(argThat(equalTo(Message.PUTX(Message.GET(sh(30), 1234L), 1234L, new short[0], 2, 1, toBuffer("xxx")))));

        cache.receive(Message.MSG(sh(20), 1234L, false, true, serialize("bar")));

        InOrder inOrder = inOrder(comm);
        inOrder.verify(comm).send(argThat(equalTo(Message.PUTX(Message.GET(sh(30), 1234L), 1234L, new short[0], 2, 1, toBuffer("xxx")))));
        inOrder.verify(comm).send(argThat(equalTo(Message.MSG(sh(30), 1234L, false, true, serialize("foo")).setReplyRequired(false))));
        inOrder.verify(comm).send(argThat(equalTo(Message.MSG(sh(30), 1234L, false, true, serialize("bar")).setReplyRequired(false))));
    }

    /**
     * When NodeNotFoundException is thrown during send INV, short-circuit an INVACK
     */
    @Test
    public void whenINVAndNodeNotFoundThenINVACKSelf() throws Exception {
        doThrow(new NodeNotFoundException(sh(20))).when(comm).send(argThat(equalTo(Message.INV(sh(20), 1234L, sh(10)))));
        doThrow(new NodeNotFoundException(sh(40))).when(comm).send(argThat(equalTo(Message.INV(sh(40), 1234L, sh(10)))));
        PUTX(1234L, sh(10), 1, "hello", 20, 30, 40);

        cache.runOp(new Op(GETX, 1234L, null));

        assertState(1234L, O, E);

        cache.receive(Message.INVACK(sh(30), 1234L));
        if (hasServer)
            cache.receive(Message.INVACK(sh(0), 1234L));

        assertState(1234L, E, null); // 20 and 40 were self-ACKed when the exception was thrown
    }

    /**
     * When NodeNotFoundException is thrown during send GET/X, short-circuit a CHNGD_OWNR
     */
    @Test
    public void whenGETAndNodeNotFoundThenCHNGD_OWNRSelf() throws Exception {
        for (Op.Type getType : new Op.Type[]{GET, GETX}) {
            PUT(1234L, sh(10), 1L, "hello");
            INV(1234L, sh(10));
            PUT(2222L, sh(10), 1L, "foo"); // prevent dirty read of 1234

            doThrow(new NodeNotFoundException(sh(10))).when(comm).send(argThat(equalTo(matchingMessage(getType, sh(10), 1234L))));

            cache.runOp(new Op(getType, 1234L, null));

            verify(comm).send(argThat(equalTo(matchingMessage(getType, sh(-1), 1234L)))); // a CHNGD_OWNR to -1 is received during get from 10

            reset();
        }
    }

    /**
     * When TIMEOUT is received, all pending ops should be interrupted with a TimeoutException.
     */
    @Test
    public void whenTimeoutThenInterruptPendingOps() throws Exception {
        setCommMsgCounter();
        final Op op1 = new Op(GET, 1L, null);
        final Op op2 = new Op(GETX, 1L, null);
        final Op op3 = new Op(SET, 1L, serialize("xxx"), null);
        final Op op4 = new Op(SEND, 1L, Message.MSG(sh(-1), 1234L, true, serialize("foo")), null);

        Object res1 = cache.runOp(op1);
        Object res2 = cache.runOp(op2);
        Object res3 = cache.runOp(op3);
        Object res4 = cache.runOp(op4);

        assertThat(res1, is(PENDING));
        assertThat(res2, is(PENDING));
        assertThat(res3, is(PENDING));
        assertThat(res4, is(PENDING));

        cache.receive(Message.TIMEOUT(new LineMessage(sh(1), Type.GET, 1L)));

        try {
            op1.getResult();
            fail("TimeoutException not thrown");
        } catch (Exception e) {
            assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        }
        try {
            op2.getResult();
            fail("TimeoutException not thrown");
        } catch (Exception e) {
            assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        }
        try {
            op3.getResult();
            fail("TimeoutException not thrown");
        } catch (Exception e) {
            assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        }
        try {
            op4.getResult();
            fail("TimeoutException not thrown");
        } catch (Exception e) {
            assertThat(e.getCause(), is(instanceOf(TimeoutException.class)));
        }
    }

    /**
     * Verify LRU eviction of shared/invalidated lines
     */
    @Test
    public void testEviction1() throws Exception {
        cache = makeCache(120);

        CacheListener listener = mock(CacheListener.class);
        cache.addCacheListener(listener);

        PUTX(101L, sh(10), 1L, "0123456789");
        PUT(201L, sh(10), 1L, "0123456789");
        PUTX(102L, sh(10), 1L, "0123456789");
        PUT(202L, sh(10), 1L, "0123456789");
        PUTX(13L, sh(10), 1L, "0123456789");
        PUT(203L, sh(10), 1L, "0123456789");
        PUTX(104L, sh(10), 1L, "0123456789");
        PUTX(105L, sh(10), 1L, "0123456789");
        PUT(204L, sh(10), 1L, "0123456789");
        PUTX(16L, sh(10), 1L, "0123456789");

        GETX(13, sh(10)); // -> I

        get(202);
        get(204);
        get(203);
        get(201);

        GETX(16, sh(10));

        for (long i = 107; i <= 200; i++)
            PUTX(i, sh(10), 1L, "0123456789");
        for (long i = 300; i <= 400; i++)
            PUT(i, sh(10), 1L, "0123456789");

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).evicted(cache, 13);
        inOrder.verify(listener).evicted(cache, 202);
        inOrder.verify(listener).evicted(cache, 204);
        inOrder.verify(listener).evicted(cache, 203);
        inOrder.verify(listener).evicted(cache, 201);
        inOrder.verify(listener).evicted(cache, 16);
        for (long i = 101; i <= 200; i++)
            verify(listener, never()).evicted(cache, i);
    }

    @Test
    public void whenNodeRemovedThenCleanUp() throws Exception {
        PUT(1L, sh(10), 1L, "11");
        PUT(2L, sh(10), 1L, "22");
        PUTX(3L, sh(20), 1L, "33", 10, 20, 30);
        PUT(4L, sh(20), 1L, "44");

        CacheListener listener = mock(CacheListener.class);
        cache.addCacheListener(listener);

        cache.nodeRemoved(sh(10));

        assertState(1L, I, null);
        assertState(2L, I, null);
        assertState(3L, O, null);
        assertState(4L, S, null);
//        verify(listener).evicted(1L);
//        verify(listener).evicted(2L);
        verify(listener, never()).evicted(cache, 3L);
        verify(listener, never()).evicted(cache, 4L);

//        assertThat(cache.getLine(1), is(nullValue()));
//        assertThat(cache.getLine(2), is(nullValue()));
//        assertThat(cache.getLine(3), is(not(nullValue())));
//        assertThat(cache.getLine(4), is(not(nullValue())));
        // check line 3's sharers by getx and verifying INV's
        cache.runOp(new Op(GETX, 3L, null));
        verify(comm).send(argThat(equalTo(Message.INV(sh(20), 3L, sh(20)))));
        verify(comm).send(argThat(equalTo(Message.INV(sh(30), 3L, sh(20)))));
        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.INV(Comm.SERVER, 3L, sh(20)))));
        verify(comm, never()).send(argThat(equalTo(Message.INV(sh(10), 3L, sh(20)))));
    }

    /**
     * Make sure an exception is thrown when putting or setting data larger than the maximum data item size.
     */
    @Test
    public void whenPutOrSetOverMaxItemSizeThenThrowException() throws Exception {
        cache.setMaxItemSize(7);

        long id = put("0123456");

        try {
            put("01234567");
            fail("Exception not thrown");
        } catch (RuntimeException e) {
        }

        set(id, "012345");

        try {
            set(id, "01234567");
            fail("Exception not thrown");
        } catch (RuntimeException e) {
        }
    }

    /**
     * It is possible to set data items to null.
     */
    @Test
    public void testNullData() throws Exception {
        long ref1 = put(null);
        assertThat(get(ref1), is(nullValue()));

        set(ref1, "foo");
        assertThat(get(ref1), is("foo"));

        long ref2 = put("foo");
        assertThat(get(ref2), is("foo"));

        set(ref2, null);
        assertThat(get(ref2), is(nullValue()));
    }

    /**
     *
     */
    @Test
    public void testAlloc() throws Exception {
        final RefAllocationsListener listener = hasServer ? null : getRefAllocationListener(cache.getRefAllocator());
        Object res;
        Op alloc;

        Transaction txn = cache.beginTransaction();
        alloc = new Op(ALLOC, -1L, 10, txn);
        res = cache.runOp(alloc);

        assertThat(res, is(PENDING));
        if (hasServer)
            verify(comm).send(argThat(equalTo(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT))));
        else
            verify(cluster).allocateRefs(anyInt());

        if (hasServer)
            cache.receive(Message.ALLOCED_REF(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT), 100, 3));
        else
            listener.refsAllocated(100, 3);

        assertThat(alloc.getFuture().isDone(), is(false));

        if (hasServer)
            cache.receive(Message.ALLOCED_REF(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT), 200, 30));
        else
            listener.refsAllocated(200, 30);

        res = alloc.getResult();

        assertThat((Long) res, is(200L));

        for (int i = 0; i < 10; i++) {
            res = get(200L + i);
            assertThat(res, is(nullValue()));
            assertModified(200L + i, true);
            assertThat(cache.getLine(200L + i).isLocked(), is(true));
        }

        res = doOp(ALLOC, -1L, 7, txn);
        assertThat((Long) res, is(210L));

        alloc = new Op(ALLOC, -1L, 20, txn);
        res = cache.runOp(alloc);

        assertThat(res, is(PENDING));
        if (hasServer)
            verify(comm, atLeastOnce()).send(argThat(equalTo(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT))));
        else
            verify(cluster, atLeastOnce()).allocateRefs(anyInt());

        if (hasServer)
            cache.receive(Message.ALLOCED_REF(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT), 300, 10));
        else
            listener.refsAllocated(300, 10);

        assertThat(alloc.getFuture().isDone(), is(false));

        if (hasServer)
            cache.receive(Message.ALLOCED_REF(Message.ALLOC_REF(Comm.SERVER, DEFAULT_ALLOC_COUNT), 400, 50));
        else
            listener.refsAllocated(400, 50);

        res = alloc.getResult();

        assertThat((Long) res, is(400L));
    }

    @Ignore
    @Test
    public void testTransactions() {
        pending();
    }

    /////////////////////////////////////////////////////////////////////////////////
    private boolean hasServer() {
        return hasServer; //cluster.hasServer();
    }

    private LineMessage matchingMessage(Op.Type type, short node, long line) {
        switch (type) {
            case GET:
            case GETS:
                return Message.GET(node, line);
            case GETX:
                return Message.GETX(node, line);
            default:
                throw new IllegalArgumentException("Type must be GET/GETS/GETX but is " + type);
        }
    }

    void makeShared(long id) {
        cache.receive(Message.GET(sh(10), id));
    }

    void makeInvalid(short owner, long id) {
        cache.receive(Message.INV(owner, id, owner).setMessageId(++messageId));
    }

    void PUT(long id, short owner, long version, String obj) {
        cache.receive(Message.PUT(owner, id, version, toBuffer(obj)).setMessageId(++messageId));
    }

    void PUTX(long id, short owner, long version, String obj, int... sharers) {
        short[] ssharers = new short[sharers.length];
        for (int i = 0; i < sharers.length; i++)
            ssharers[i] = (short) sharers[i];
        cache.receive(Message.PUTX(Message.GETX(owner, id), id, ssharers, 0, version, toBuffer(obj)).setMessageId(++messageId));
        //cache.receive(Message.BACKUPACK(sh(-1), id, version));
    }

    void INVOKE(long id, short owner, long version, LineFunction function) {
        cache.receive(Message.INVOKE(owner, id, function));
    }

    void PUTX(long id, short owner, long version, String obj) {
        PUTX(id, owner, 0, version, obj);
    }

    void PUTX(long id, short owner, int parts, long version, String obj) {
        cache.receive(Message.PUTX(Message.GETX(owner, id), id, new short[0], parts, version, toBuffer(obj)).setMessageId(++messageId));
        if (hasServer())
            cache.receive(Message.INVACK(Message.INV(sh(0), id, owner)));
        //cache.receive(Message.BACKUPACK(sh(-1), id, version));
    }

    void GET(long id, short node) {
        cache.receive(Message.GET(node, id));
    }

    void GETX(long id, short node) {
        cache.receive(Message.GETX(node, id));
    }

    void INV(long id, short owner) {
        cache.receive(Message.INV(owner, id, owner).setMessageId(++messageId));
    }

    void INVACK(long id, short owner) {
        cache.receive(Message.INVACK(owner, id));
    }

    long put(String obj) throws Exception {
        getRefAllocationListener(cache.getRefAllocator()).refsAllocated(100, 1000);
        return (Long) doOp(PUT, -1L, serialize(obj));
    }

    String get(long id) throws Exception {
        return deserialize((byte[]) doOp(GET, id));
    }

    void set(long id, String obj) throws Exception {
        doOp(SET, id, serialize(obj));
    }

    void set(long id, String obj, Transaction txn) throws Exception {
        doOp(SET, id, serialize(obj), txn);
    }

    void del(long id) throws Exception {
        doOp(DEL, id);
    }

    private Message captureMessage() throws Exception {
        ArgumentCaptor<Message> captor = (ArgumentCaptor) ArgumentCaptor.forClass(Message.class);
        verify(comm).send(captor.capture());
        return captor.getValue();
    }

    private NodeInfo makeNodeInfo(final short node) {
        return new NodeInfo() {
            @Override
            public String getName() {
                return "NODE-" + node;
            }

            @Override
            public short getNodeId() {
                return node;
            }

            @Override
            public Object get(String property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Collection<String> getProperties() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public static RefAllocationsListener getRefAllocationListener(RefAllocator allocator) {
        if (new MockUtil().isMock(allocator)) {
            try {
                return (RefAllocationsListener) capture(allocator, "addRefAllocationsListener", arg(RefAllocationsListener.class));
            } catch (Exception e) {
                return null;
            }
        } else
            return allocator.getRefAllocationsListeners().iterator().next();
    }

    void assertState(long id, State state, State nextState) {
        CacheLine line = cache.getLine(id);
        assertThat(line.getState(), is(state));
        assertThat(line.getNextState(), is(nextState));
    }

    void assertOwner(long id, short node) {
        CacheLine line = cache.getLine(id);
        assertThat(line.getOwner(), is(node));
    }

    void assertVersion(long id, long version) {
        CacheLine line = cache.getLine(id);
        assertThat(line.getVersion(), is(version));
    }

    void assertLocked(long id, boolean value) {
        CacheLine line = cache.getLine(id);
        assertThat(line.isLocked(), is(value));
    }

    void assertModified(long id, boolean value) {
        CacheLine line = cache.getLine(id);
        assertThat(line.is(CacheLine.MODIFIED), is(value));
    }

    void evict(long id, boolean invack) {
        CacheLine line = cache.getLine(id);
        cache.evictLine(line, invack);
    }

    static long id(long id) {
        return Cache.MAX_RESERVED_REF_ID + id;
    }

    static short sh(int x) {
        return (short) x;
    }

    static short[] sh(int... args) {
        final short[] array = new short[args.length];
        for (int i = 0; i < args.length; i++)
            array[i] = (short) args[i];
        return array;
    }

    static ByteBuffer toBuffer(String object) {
        return ByteBuffer.wrap(serialize(object));
    }

    static byte[] serialize(String object) {
        return object != null ? object.getBytes(Charsets.UTF_8) : null;
    }

    static String deserialize(Object obj) {
        return deserialize((byte[]) obj);
    }

    static String deserialize(byte[] array) {
        return array != null ? new String(array, Charsets.UTF_8) : null;
    }

    static void pending() {
        fail("Test pending");
    }

    public Object doOp(Op.Type type, long line, byte[] data, Object extra) throws TimeoutException {
        return cache.doOp(type, line, (Object) (data != null ? Arrays.copyOf(data, data.length) : null), extra, null);
    }

    public Object doOp(Op.Type type, long line, ByteBuffer data, Object extra) throws TimeoutException {
        return cache.doOp(type, line, (Object) data, extra, null);
    }

    public Object doOp(Op.Type type, long line, Persistable data, Object extra) throws TimeoutException {
        return cache.doOp(type, line, (Object) data, extra, null);
    }

    public Object doOp(Op.Type type, long line, Object extra) throws TimeoutException {
        return cache.doOp(type, line, (Object) null, extra, null);
    }

    public Object doOp(Op.Type type, long line) throws TimeoutException {
        return cache.doOp(type, line, (Object) null, null, null);
    }

    public Object doOp(Op.Type type, long line, byte[] data) throws TimeoutException {
        return doOp(type, line, data, null);
    }

    public Object doOp(Op.Type type, long line, ByteBuffer data) throws TimeoutException {
        return doOp(type, line, data, null);
    }

    public Object doOp(Op.Type type, long line, Persistable data) throws TimeoutException {
        return doOp(type, line, data, null);
    }

    public Object doOp(Op.Type type, long line, byte[] data, Object extra, Transaction txn) throws TimeoutException {
        return cache.doOp(type, line, (Object) (data != null ? Arrays.copyOf(data, data.length) : null), extra, txn);
    }

    public Object doOp(Op.Type type, long line, ByteBuffer data, Object extra, Transaction txn) throws TimeoutException {
        return cache.doOp(type, line, (Object) data, extra, txn);
    }

    public Object doOp(Op.Type type, long line, Persistable data, Object extra, Transaction txn) throws TimeoutException {
        return cache.doOp(type, line, (Object) data, extra, txn);
    }

    public Object doOp(Op.Type type, long line, Object extra, Transaction txn) throws TimeoutException {
        return cache.doOp(type, line, (Object) null, extra, txn);
    }

    public Object doOp(Op.Type type, long line, Transaction txn) throws TimeoutException {
        return cache.doOp(type, line, (Object) null, null, txn);
    }

    public Object doOp(Op.Type type, long line, byte[] data, Transaction txn) throws TimeoutException {
        return doOp(type, line, data, null, txn);
    }

    public Object doOp(Op.Type type, long line, ByteBuffer data, Transaction txn) throws TimeoutException {
        return doOp(type, line, data, null, txn);
    }

    public Object doOp(Op.Type type, long line, Persistable data, Transaction txn) throws TimeoutException {
        return doOp(type, line, data, null, txn);
    }

    private void setCommMsgCounter() throws NodeNotFoundException {
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                Message msg = (Message) invocation.getArguments()[0];
                System.out.println("mock send msg " + msg);
                if (msg.getMessageId() < 0)
                    msg.setMessageId(++messageId);
                return null;
            }
        }).when(comm).send(any(Message.class));
    }
}
