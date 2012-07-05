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
package co.paralleluniverse.galaxy.netty;

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.core.Comm;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.MessageReceiver;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import static co.paralleluniverse.galaxy.core.MessageMatchers.*;
import co.paralleluniverse.galaxy.core.ServerComm;
import static co.paralleluniverse.galaxy.netty.MessagePacketMatchers.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;

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
import co.paralleluniverse.galaxy.test.ClonesArguments;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;
import com.google.common.primitives.Shorts;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unfortunately, these tests are based on timings, and are therefore a bit flaky.
 * Also, there should be more tests.
 * @author pron
 */
public class UDPCommTest {
    static final int PORT = 100;
    static final InetSocketAddress GROUP;
    static final InetSocketAddress node2Address;
    static final InetSocketAddress node3Address;
    static final InetSocketAddress node4Address;
    static final long MAX_RESERVED_REF_ID = 0xffffffffL;

    static {
        try {
            GROUP = new InetSocketAddress(InetAddress.getByName("1.2.3.4"), PORT);
            node2Address = new InetSocketAddress(InetAddress.getByName("1.1.1.2"), PORT);
            node3Address = new InetSocketAddress(InetAddress.getByName("1.1.1.3"), PORT);
            node4Address = new InetSocketAddress(InetAddress.getByName("1.1.1.4"), PORT);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    UDPComm comm;
    Cluster cluster;
    DatagramChannel channel;
    ServerComm serverComm;
    Collection<NodeInfo> masters = new ArrayList<NodeInfo>();
    MessageReceiver receiver;

    public UDPCommTest() {
    }

    @Before
    public void setUp() throws Exception {
        cluster = mock(Cluster.class);
        when(cluster.isMaster()).thenReturn(true); // otherwise messages aren't passed to the receiver.
        when(cluster.getMyNodeId()).thenReturn(sh(1));
        when(cluster.getNodes()).thenReturn(new HashSet<Short>(Shorts.asList(sh(0, 2, 3, 4))));
        when(cluster.getMasters()).thenReturn(masters);

        addNodeInfo(sh(2), node2Address);
        addNodeInfo(sh(3), node3Address);
        addNodeInfo(sh(4), node4Address);

        serverComm = mock(ServerComm.class);
        channel = mock(DatagramChannel.class, new ClonesArguments()); // we must clone the arguments because the message packet keeps changing
        receiver = mock(MessageReceiver.class);

        comm = new UDPComm("comm", cluster, serverComm, PORT);
        comm.setChannel(channel);
        comm.setReceiver(receiver);
        comm.setSendToServerInsteadOfMulticast(false);
        comm.setMinimumNodesToMulticast(3);
        comm.setMulticastGroup(GROUP);
        comm.setResendPeriodMillisecs(20);
        comm.setTimeout(350);
        comm.setMinDelayMicrosecs(5000); // 5 millis
        comm.setMaxDelayMicrosecs(15000); // 15 millis
        comm.setExponentialBackoff(false);
        comm.setJitter(false);

        for (short node : sh(0, 2, 3, 4))
            comm.nodeAdded(node);
    }

    @After
    public void tearDown() {
    }
    ///////////////////////////////////////////////

    @Test
    public void testSimpleSendMessage() throws Exception {
        final Message m = Message.GET(sh(2), 1234L);
        comm.send(m);
        await();
        verify(channel, atLeastOnce()).write(argThat(is(packetThatContains(m))), eq(node2Address));
    }

    @Test
    public void whenSeveralMessagesThenAggregateInPacketUntilMaxDelay() throws Exception {
        // we test on responses because requests actually send immediately when comm.send() is called
        final Message m1 = Message.INVACK(Message.INV(sh(2), id(1111L), sh(10))).setMessageId(10001);
        final Message m2 = Message.INVACK(Message.INV(sh(2), id(2222L), sh(10))).setMessageId(10002);
        final Message m3 = Message.INVACK(Message.INV(sh(2), id(3333L), sh(10))).setMessageId(10003);
        final Message m4 = Message.INVACK(Message.INV(sh(2), id(4444L), sh(10))).setMessageId(10004);
        comm.send(m1);
        comm.send(m2);
        comm.send(m3);
        sleep(10); // more than min delay since last
        comm.send(m4);
        await();

        verify(channel, never()).write(argThat(equalTo(packet(m1))), eq(node2Address));
        verify(channel, never()).write(argThat(equalTo(packet(m1, m2))), eq(node2Address));
        verify(channel, atLeastOnce()).write(argThat(equalTo(packet(m1, m2, m3))), eq(node2Address));
        verify(channel, atLeastOnce()).write(argThat(equalTo(packet(m1, m2, m3, m4))), eq(node2Address));
//        verify(channel).write(argThat(is(allOf(
//                packetThatContains(m1), 
//                packetThatContains(m2), 
//                packetThatContains(m3), 
//                not(packetThatContains(m4))))), eq(node2Address));
    }

    @Test
    public void whenSendRequestThenResendUntilResponse() throws Exception {
        final Message m = Message.INV(sh(2), id(1234L), sh(10));
        comm.send(m);
        sleep(100);
        await();
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node2Address));
    }

    @Test
    public void whenNoResponseThenTimeout() throws Exception {
        final LineMessage m = Message.GET(sh(2), id(1234L));
        comm.send(m);
        sleep(400);
        await();
        verify(receiver).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenNoResponseForINVThenNoTimeout() throws Exception {
        final LineMessage m = Message.INV(sh(2), id(1234L), sh(10));
        comm.send(m);
        sleep(400);
        await();
        verify(receiver, never()).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenReceiveResponseThenStopResendingAndNoTimeout() throws Exception {
        final LineMessage m = Message.INV(sh(2), id(1234L), sh(10));

        comm.send(m);
        sleep(200);
        comm.messageReceived(packet(Message.INVACK(m).setIncoming()));
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node2Address));
        sleep(400);
        await();
        verifyNoMoreInteractions(channel);
        verify(receiver, never()).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenSendRespondThenSendOnlyOnce() throws Exception {
        final Message m = Message.INVACK(Message.INV(sh(2), id(1234L), sh(10))).setMessageId(10001);
        comm.send(m);
        sleep(100);
        await();
        verify(channel, times(1)).write(argThat(is(packetThatContains(m))), eq(node2Address));
    }

    @Test
    public void whenRequestAgainAndHasResponseThenResendResponse() throws Exception {
        final LineMessage m = Message.INV(sh(2), id(1234L), sh(10)).setMessageId(10001).setIncoming();
        comm.messageReceived(packet(m));
        comm.send(Message.INVACK(m));
        sleep(100);

        verify(channel, times(1)).write(argThat(is(packetThatContains(Message.INVACK(m)))), eq(node2Address));

        comm.messageReceived(packet(m));

        await();
        verify(channel, times(2)).write(argThat(is(packetThatContains(Message.INVACK(m)))), eq(node2Address));
    }

    @Test
    public void testSimpleBroadcast() throws Exception {
        final LineMessage m = Message.GET(sh(-1), id(1234L));
        comm.send(m);

        await();
        verify(channel, atLeastOnce()).write(argThat(is(packetThatContains(m))), eq(GROUP));
    }

    @Test
    public void whenBroadcastRequestThenResendUntilResponse() throws Exception {
        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));
        comm.send(m);
        sleep(200);

        await();
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));
    }

    @Test
    public void whenBroadcastNoReplyThenTimeout() throws Exception {
        final LineMessage m = Message.GET(sh(-1), id(1234L));
        comm.send(m);
        sleep(400);

        await();
        verify(receiver).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenBroadcastAndReceiveReplyThenStopResendingAndNoTimeout() throws Exception {
        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));

        comm.send(m);
        sleep(200);
        comm.messageReceived(packet(Message.INVACK(m).setNode(sh(3)).setIncoming()));

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));

        sleep(400);

        await();
        verifyNoMoreInteractions(channel);
        verify(receiver, never()).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenBroadcastAndReceiveAcksThenStopResendingAndNotFound() throws Exception {
        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));

        comm.send(m);
        sleep(200);
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(2)).setIncoming()));
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(3)).setIncoming()));
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(4)).setIncoming()));

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));

        sleep(400);

        await();
        verify(receiver).receive(argThat(equalTo(Message.NOT_FOUND(m))));
        verify(receiver, never()).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenUnicastBroadcastThenResendUnicastUntilResponse() throws Exception {
        comm.setMinimumNodesToMulticast(10);

        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));
        comm.send(m);
        sleep(200);
        await();

        verify(channel, never()).write(any(MessagePacket.class), eq(GROUP));
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node2Address));
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node3Address));
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node4Address));
        verifyNoMoreInteractions(channel);
    }

    @Test
    public void whenBroadcastRequestAndSomeAcksThenResendUnicastUntilResponse() throws Exception {
        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));
        comm.send(m);
        sleep(200);
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(3)).setIncoming()));

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));

        sleep(200);
        
        await();
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node2Address));
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node4Address));
        verifyNoMoreInteractions(channel);
    }

    @Test
    public void whenUnicastBroadcastAndNoReplyThenTimeout() throws Exception {
        final LineMessage m = Message.GET(sh(-1), id(1234L));
        comm.send(m);
        sleep(100);
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(3)).setIncoming()));

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));

        sleep(200);

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node2Address));
        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(node4Address));

        sleep(400);

        await();
        verify(receiver).receive(argThat(equalTo(Message.TIMEOUT(m))));
        verifyNoMoreInteractions(receiver);
    }

    @Test
    public void whenUnicastBroadcastAndReceiveReplyThenNoTimeout() throws Exception {
        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));
        comm.send(m);
        sleep(200);
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(3)).setIncoming()));

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));

        sleep(100);

        comm.messageReceived(packet(Message.INVACK(m).setNode(sh(4)).setIncoming()));

        verify(channel, atLeast(1)).write(argThat(equalTo(packet(m))), eq(node2Address));
        verify(channel, atLeast(1)).write(argThat(equalTo(packet(m))), eq(node4Address));

        sleep(400);

        await();
        // verifyNoMoreInteractions(channel); - node 2 will continue resending. we don't care about that
        verify(receiver).receive(argThat(equalTo(Message.INVACK(m).setNode(sh(4)).setIncoming())));
        verify(receiver, never()).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }

    @Test
    public void whenUnicastBroadcastAndReceiveAcksThenNotFound() throws Exception {
        final LineMessage m = Message.INV(sh(-1), id(1234L), sh(10));
        comm.send(m);
        sleep(150);
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(3)).setIncoming()));

        verify(channel, atLeast(3)).write(argThat(equalTo(packet(m))), eq(GROUP));

        sleep(150);

        comm.messageReceived(packet(Message.ACK(m).setNode(sh(2)).setIncoming()));
        comm.messageReceived(packet(Message.ACK(m).setNode(sh(4)).setIncoming()));

        verify(channel, atLeast(2)).write(argThat(equalTo(packet(m))), eq(node2Address));
        verify(channel, atLeast(2)).write(argThat(equalTo(packet(m))), eq(node4Address));

        sleep(400);

        await();
        verify(receiver).receive(argThat(equalTo(Message.NOT_FOUND(m))));
        verify(receiver, never()).receive(argThat(equalTo(Message.TIMEOUT(m))));
    }
    ///////////////////////////////////////////////

    static NodeChangeListener getNodeChangeListener(Cluster mock) {
        try {
            return (NodeChangeListener) capture(mock, "addNodeChangeListener", arg(NodeChangeListener.class));
        } catch (Exception e) {
            return null;
        }
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

    private long id(long id) {
        return MAX_RESERVED_REF_ID + id;
    }

    void await() {
        try {
            ExecutorService executor = comm.getExecutor();
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.err.println("Interrupted");
        }
    }

    static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            System.err.println("Interrupted");
        }
    }

    private static MessagePacket packet(Message... ms) {
        MessagePacket packet = new MessagePacket();
        for (Message m : ms)
            packet.addMessage(m);
        return packet;
    }

    private void addNodeInfo(short node, InetSocketAddress address) {
        NodeInfo ni = mock(NodeInfo.class);
        when(ni.getNodeId()).thenReturn(node);
        when(ni.get(IpConstants.IP_ADDRESS)).thenReturn(address.getAddress());
        when(ni.get(IpConstants.IP_COMM_PORT)).thenReturn(address.getPort());

        when(cluster.getMaster(node)).thenReturn(ni);
        masters.add(ni);
    }

    private MessagePacket captureMessagePacket() throws Exception {
        ArgumentCaptor<MessagePacket> captor = (ArgumentCaptor) ArgumentCaptor.forClass(MessagePacket.class);
        verify(channel, times(1)).write(captor.capture(), any(SocketAddress.class));
        return captor.getValue();
    }
}
