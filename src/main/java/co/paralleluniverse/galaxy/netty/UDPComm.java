/*
 * Galaxy
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
package co.paralleluniverse.galaxy.netty;

import co.paralleluniverse.common.monitoring.ThreadPoolExecutorMonitor;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.ReaderWriters;
import co.paralleluniverse.galaxy.core.AbstractComm;
import co.paralleluniverse.galaxy.core.Comm;
import co.paralleluniverse.galaxy.core.CommThread;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.MessageReceiver;
import co.paralleluniverse.galaxy.core.NodeNotFoundException;
import co.paralleluniverse.galaxy.core.ServerComm;
import static co.paralleluniverse.galaxy.netty.IpConstants.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.beans.ConstructorProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import static java.util.concurrent.TimeUnit.*;
import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.jboss.netty.channel.socket.oio.OioDatagramChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 * This crucial class could use a good refactoring.
 *
 * @author pron
 */
public class UDPComm extends AbstractComm<InetSocketAddress> {
    // Note: class must be public for Spring's auto generated javax.management.modelmbean.RequiredModelMBean to expose @ManagedAttribute
    private static final Logger LOG = LoggerFactory.getLogger(UDPComm.class);
    //
    private final int port;
    private InetSocketAddress multicastGroup;
    private NetworkInterface multicastNetworkInterface;
    private int maxQueueSize = 50;
    private int maxPacketSize = 4096;
    private int maxRequestOnlyPacketSize = maxPacketSize / 2;
    private long minDelayNanos = NANOSECONDS.convert(1, MILLISECONDS);
    private long maxDelayNanos = NANOSECONDS.convert(10, MILLISECONDS);
    private long resendPeriodNanos = NANOSECONDS.convert(20, MILLISECONDS);
    private boolean jitter = false;
    private boolean exponentialBackoff = true;
    private int minimumNodesToMulticast = 3;
    private ThreadPoolExecutor workerExecutor;
    private OrderedMemoryAwareThreadPoolExecutor receiveExecutor;
    //
    private final Comm serverComm;
    private DatagramChannelFactory channelFactory;
    private ConnectionlessBootstrap bootstrap;
    private DatagramChannel channel;
    private DatagramChannel multicastChannel;
    private BroadcastPeer broadcastPeer = new BroadcastPeer();
    private SocketAddress myAddress;
    private final ConcurrentMap<Short, NodePeer> peers = new ConcurrentHashMap<Short, NodePeer>();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("uspCommScheduled-%d").setDaemon(true).build());
    private final UDPCommMonitor monitor;

    @ConstructorProperties({"name", "cluster", "serverComm", "port"})
    UDPComm(String name, Cluster cluster, ServerComm serverComm, int port) throws Exception {
        super(name, cluster, new SocketNodeAddressResolver(cluster, IP_COMM_PORT));
        this.serverComm = serverComm;
        this.port = port;

        cluster.addNodeProperty(IP_ADDRESS, true, true, INET_ADDRESS_READER_WRITER);
        cluster.setNodeProperty(IP_ADDRESS, InetAddress.getLocalHost());
        cluster.addNodeProperty(IP_COMM_PORT, true, false, ReaderWriters.INTEGER);
        cluster.setNodeProperty(IP_COMM_PORT, port);

        this.monitor = new UDPCommMonitor(name, this);
    }

    @ManagedAttribute
    public int getPort() {
        return port;
    }

    public void setReceiveBufferSize(int size) {
        assertDuringInitialization();
        bootstrap.setOption("receiveBufferSize", size);
    }

    public void setMulticastGroup(InetSocketAddress group) {
        assertDuringInitialization();
        this.multicastGroup = group;
    }

    @ManagedAttribute
    public String getMulticastGroupName() {
        return multicastGroup.toString();
    }

    public void setMulticastNetworkInterface(NetworkInterface multicastNetworkInterface) {
        assertDuringInitialization();
        this.multicastNetworkInterface = multicastNetworkInterface;
    }

    @ManagedAttribute
    public String getMulticastNetworkInterfaceName() {
        return multicastNetworkInterface.toString();
    }

    public void setMaxQueueSize(int maxQueueSize) {
        assertDuringInitialization();
        this.maxQueueSize = maxQueueSize;
    }

    @ManagedAttribute
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        assertDuringInitialization();
        this.maxPacketSize = maxPacketSize;
    }

    @ManagedAttribute
    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxRequestOnlyPacketSize(int maxRequestOnlyPacketSize) {
        assertDuringInitialization();
        this.maxRequestOnlyPacketSize = maxRequestOnlyPacketSize;
    }

    @ManagedAttribute
    public int getMaxRequestOnlyPacketSize() {
        return maxRequestOnlyPacketSize;
    }

    public void setMaxDelayMicrosecs(int maxDelayMicrosecs) {
        assertDuringInitialization();
        this.maxDelayNanos = NANOSECONDS.convert(maxDelayMicrosecs, MICROSECONDS);
    }

    @ManagedAttribute
    public int getMaxDelayMicrosecs() {
        return (int) MICROSECONDS.convert(maxDelayNanos, NANOSECONDS);
    }

    public void setMinDelayMicrosecs(int minDelayMicrosecs) {
        assertDuringInitialization();
        this.minDelayNanos = NANOSECONDS.convert(minDelayMicrosecs, MICROSECONDS);
    }

    @ManagedAttribute
    public int getMinDelayMicrosecs() {
        return (int) MICROSECONDS.convert(minDelayNanos, NANOSECONDS);
    }

    public void setResendPeriodMillisecs(int resnedPeriodMillisecs) {
        assertDuringInitialization();
        this.resendPeriodNanos = NANOSECONDS.convert(resnedPeriodMillisecs, MILLISECONDS);
    }

    @ManagedAttribute
    public int getResendPeriodMillisecs() {
        return (int) MILLISECONDS.convert(resendPeriodNanos, NANOSECONDS);
    }

    public void setMinimumNodesToMulticast(int minimumNodesToMulticast) {
        assertDuringInitialization();
        this.minimumNodesToMulticast = minimumNodesToMulticast;
    }

    @ManagedAttribute
    public int getMinimumNodesToMulticast() {
        return minimumNodesToMulticast;
    }

    public void setWorkerExecutor(ThreadPoolExecutor executor) {
        assertDuringInitialization();
        this.workerExecutor = executor;
    }

    @ManagedAttribute
    public String getWorkerExecutorName() {
        return "udpCommWorkerExecutor";
    }

    public void setReceiveExecutor(OrderedMemoryAwareThreadPoolExecutor executor) {
        assertDuringInitialization();
        this.receiveExecutor = executor;
    }

    @ManagedAttribute
    public String getReceiveExecutorName() {
        return "udpCommReceiveExecutor";
    }

    public void setJitter(boolean value) {
        // see http://highscalability.com/blog/2012/4/17/youtube-strategy-adding-jitter-isnt-a-bug.html and http://news.ycombinator.com/item?id=3757456 
        assertDuringInitialization();
        this.jitter = value;
    }

    @ManagedAttribute
    public boolean isJitter() {
        return jitter;
    }

    public void setExponentialBackoff(boolean value) {
        assertDuringInitialization();
        this.exponentialBackoff = value;
    }

    @ManagedAttribute
    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    @Override
    public void setReceiver(MessageReceiver receiver) {
        super.setReceiver(receiver);
        if (serverComm != null)
            serverComm.setReceiver(receiver);
    }

    @Override
    public void init() throws Exception {
        super.init();

        if (!isSendToServerInsteadOfMulticast() && multicastGroup == null) {
            LOG.error("If sendToServerInsteadOfBroadcast, multicastGroup must be set!");
            throw new RuntimeException("multicastGroup not set.");
        }

        this.myAddress = new InetSocketAddress(InetAddress.getLocalHost(), port);
        if (workerExecutor == null)
            workerExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        // Netty ignores executor thread naming strategy cause of Worker renaming policy.
        // org.jboss.netty.channel.socket.nio.AbstractNioWorker.newThreadRenamingRunnable()
        // And unfortunately for NioDatagramChannelFactory itsn't possible to pass our own ThreadNameDeterminer.
        configureThreadPool(getWorkerExecutorName(), workerExecutor);

        if (receiveExecutor != null)
            configureThreadPool(getReceiveExecutorName(), receiveExecutor);

        this.channelFactory = isSendToServerInsteadOfMulticast()
                ? new NioDatagramChannelFactory(workerExecutor, NettyUtils.getWorkerCount(workerExecutor))
                : new OioDatagramChannelFactory(workerExecutor);
        this.bootstrap = new ConnectionlessBootstrap(channelFactory);
        this.bootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(4096));

        bootstrap.setPipelineFactory(new UdpMessagePipelineFactory(LOG, new ChannelNodeAddressResolver(addressResolver), receiveExecutor) {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                final ChannelPipeline pipeline = super.getPipeline();
                pipeline.addLast("router", new SimpleChannelHandler() {
                    @Override
                    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
                        if (ctx.getChannel() == multicastChannel) {
                            if (e.getRemoteAddress().equals(myAddress))
                                return; // this is our own multicast
                            ((MessagePacket) e.getMessage()).setMulticast();
                        }
                        UDPComm.this.messageReceived((MessagePacket) e.getMessage());
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
                        LOG.info("Channel exception: {} {}", e.getCause().getClass().getName(), e.getCause().getMessage());
                        LOG.debug("Channel exception", e.getCause());
                    }
                });
                return pipeline;
            }
        });

        bootstrap.setOption("localAddress", new InetSocketAddress(InetAddress.getLocalHost(), port));
        bootstrap.setOption("tcpNoDelay", true);

        monitor.registerMBean();
    }

    private void configureThreadPool(String name, ThreadPoolExecutor executor) {
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setThreadFactory(new ThreadFactoryBuilder().setNameFormat(name + "-%d").setDaemon(true).setThreadFactory(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new CommThread(r);
            }
        }).build());
        ThreadPoolExecutorMonitor.register(name, executor);
    }

    @Override
    public void postInit() throws Exception {
        if (!sendToServerInsteadOfMulticast)
            this.broadcastPeer = new BroadcastPeer();
        super.postInit();
    }

    @Override
    public void start(boolean master) {
        this.channel = (DatagramChannel) bootstrap.bind();
        LOG.info("Channel {} listening on port {}", channel, port);
        if (!isSendToServerInsteadOfMulticast()) {

            final int multicastPort = multicastGroup.getPort();
            this.multicastChannel = (DatagramChannel) bootstrap.bind(new InetSocketAddress(multicastPort));
            if (multicastNetworkInterface != null) {
                LOG.info("Channel {} joining multicast group {} on network interface {}", new Object[]{multicastChannel, multicastGroup, multicastNetworkInterface});
                multicastChannel.joinGroup(multicastGroup, multicastNetworkInterface);
            } else {
                LOG.info("Channel {} joining multicast group {} ", multicastChannel, multicastGroup);
                multicastChannel.joinGroup(multicastGroup.getAddress());
            }
        } else
            this.multicastChannel = null;
        setReady(true);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        LOG.info("Shutting down.");
        monitor.unregisterMBean();
        if (channel != null)
            channel.close();
        if (multicastChannel != null)
            multicastChannel.close();
        channelFactory.releaseExternalResources();
    }

    // for testing only
    void setChannel(DatagramChannel channel) {
        this.channel = channel;
    }

    ExecutorService getExecutor() {
        return executor;
    }

    @Override
    protected void sendToServer(Message message) {
        super.sendToServer(message);
        try {
            serverComm.send(message);
        } catch (NodeNotFoundException e) {
            throw new RuntimeException("Server not found!", e);
        }
    }

    /**
     * Can block if buffer is full
     */
    @Override
    protected void sendToNode(Message message, short node, InetSocketAddress address) {
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Sending to node {} ({}): {}", new Object[]{node, address, message});
            message.cloneDataBuffers(); // important, as we're going to be doing actual sending on another thread

            final NodePeer peer = peers.get(node);
            if (peer == null)
                throw new NodeNotFoundException(node);

            peer.sendMessage(message);
            executor.submit(peer);
        } catch (InterruptedException ex) {
            LOG.error("InterruptedException", ex);
            throw new RuntimeException(ex);
        } catch (Exception ex) {
            LOG.error("Error while sending message " + message + " to node " + node, ex);
        }
    }

    /**
     * Can block
     */
    @Override
    protected synchronized void broadcast(Message message) { // synchronized for message ID ordering
        try {
            assert message.isBroadcast() && !message.isResponse();

            assignMessageId(message);
            final boolean unicast = getNumPeerNodes() < minimumNodesToMulticast;
            final ShortSet nodes = new ShortOpenHashSet();
            for (NodePeer peer : peers.values()) {
                nodes.add(peer.node);
                peer.sendMessage(message, unicast);
                executor.submit(peer);
            }
            if (nodes.isEmpty()) {
                if (message instanceof LineMessage) {
                    LOG.debug("No other nodes in cluster. Responding with NOT_FOUND to message {}", message);
                    receive(Message.NOT_FOUND((LineMessage) message).setIncoming());
                }
                return;
            }
            broadcastPeer.sendMessage(message, nodes, unicast);
            if (!unicast)
                executor.submit(broadcastPeer);
        } catch (InterruptedException ex) {
            LOG.error("InterruptedException", ex);
            throw new RuntimeException(ex);
        }
    }

    // visible for testing
    void messageReceived(MessagePacket packet) {
        if (!getCluster().isMaster())
            return;
        LOG.debug("Received packet {}", packet);

        final long now = System.nanoTime();
        packet.setTimestamp(now);

        final short node = packet.getNode();
        final NodePeer peer = peers.get(node);
        if (peer == null)
            throw new RuntimeException("Message received from unhandled node " + node);

        try {
            peer.receivePacket(packet); // we're now running in the executor we installed in the netty pipeline.
        } catch (InterruptedException ex) {
            LOG.error("InterruptedException", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public synchronized void nodeAdded(short id) {
        super.nodeAdded(id);
        if (id == 0)
            return;
        if (peers.get(id) != null)
            return;
        final NodePeer peer = new NodePeer(id);
        LOG.info("Adding peer {} for node {}", peer, id);
        peer.setAddress(getNodeAddress(id));
        peers.put(id, peer);
    }

    @Override
    public synchronized void nodeSwitched(short id) {
        super.nodeSwitched(id);
        final NodePeer peer = peers.get(id);
        LOG.info("Node switched. Fixing peer {}", peer);
        peer.setAddress(getNodeAddress(id));
        executor.submit(peer); // resend
        executor.submit(broadcastPeer); // resend
    }

    @Override
    public synchronized void nodeRemoved(short id) {
        super.nodeRemoved(id);
        final NodePeer peer = peers.get(id);
        if (peer != null)
            peer.removed();
        peers.remove(id);
        broadcastPeer.removeNode(id);
    }
    private static final ThreadLocal<Boolean> recursive = new ThreadLocal<Boolean>();

    abstract class Peer implements Callable<Void> {
        protected final ArrayBlockingQueue<Message> queue = new ArrayBlockingQueue<Message>(maxQueueSize);
        protected Message overflow;
        protected MessagePacket sentPacket;
        private int delayMultiplier = 1;
        private long lastSent;
        private long nextSend;
        private final Set<Message> timeouts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());
        private long lastTimeoutsCleanup;

        /**
         * This can block!
         */
        public void sendMessage(Message message) throws InterruptedException {
            if (!queue.offer(message)) {
                LOG.info("Adding message {} to full queue. Waiting for available space.", message);
                LOG.warn("no space in Peer {}", this);
                if (recursive.get() == Boolean.TRUE) {
                    LOG.error("Queue is too small");
                    throw new RuntimeException("Queue full");
                }
                queue.put(message);
            }
        }

        public int getQueueLength() {
            return queue.size();
        }

        protected void forceResend() {
            this.lastSent = 0;
            this.nextSend = 0;
            this.delayMultiplier = 0;
        }

        protected boolean isTimeToResned(long now) {
            if (now > nextSend) {
                nextSend = Long.MAX_VALUE;
                lastSent = now;
                return true;
            } else
                return false;
        }

        protected void resendIn(long now, long delay) {
            if (LOG.isDebugEnabled())
                LOG.debug("Peer {} rescheduling in {}", this, delay);
            nextSend = now + delay;
            executor.schedule(this, delay, NANOSECONDS);
        }

        protected void resend(long now) {
            long delay = resendPeriodNanos << delayMultiplier;
            if (exponentialBackoff)
                delayMultiplier++;
            if (jitter)
                delay = randInterval(delay);
            resendIn(now, delay);
        }

        protected long getLastSent() {
            return lastSent;
        }

        protected void addTimeout(Message message) {
            timeouts.add(message);
        }

        protected boolean isTimeout(Message response) {
            return timeouts.remove(response);
        }

        protected synchronized void cleanupTimeouts(long now) {
            if (now - lastTimeoutsCleanup >= NANOSECONDS.convert(10, SECONDS)) {
                for (Iterator<Message> it = timeouts.iterator(); it.hasNext();) {
                    if (now - it.next().getTimestamp() >= NANOSECONDS.convert(10, SECONDS))
                        it.remove();
                }
                lastTimeoutsCleanup = now;
            }
        }
    }

    class NodePeer extends Peer {
        public final short node;
        private volatile boolean removed = false;
        private InetSocketAddress nodeAddress;
        private boolean hasRequests = false; // true if not all messages in the sent packet are responses
        private boolean requestsOnly = true; // true if none of the messages in the sent packet are responses
        private volatile boolean broadcast; // true if the sent packet contains a (single) broadcast (and only that)
        private final LongSet pendingRequests = new LongOpenHashSet();
        private final Set<Message> unicastBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<Message, Boolean>());
        private long lastReceivedBroadcastId;

        public NodePeer(short node) {
            this.node = node;
        }

        public synchronized void setAddress(InetSocketAddress nodeAddress) {
            LOG.info("Node peer {} set address to {}", this, nodeAddress);
            this.nodeAddress = nodeAddress;
            lastReceivedBroadcastId = 0;
            if (sentPacket != null) {
                for (Iterator<Message> it = sentPacket.iterator(); it.hasNext();) {
                    final Message message = it.next();
                    if (message.isResponse()) {
                        LOG.debug("Peer {} removing response {} because of node switch.", this, message);
                        it.remove(); // if our peer hasn't requested again then it must have received our response
                    }
                }
            }
            forceResend();
        }

        @Override
        public synchronized String toString() {
            return "NodePeer{" + "node=" + node + ", nodeAddress=" + nodeAddress + ", lastSent=" + getLastSent() + ", sentPacket=" + sentPacket + ", pendingRequests=" + pendingRequests + ", next=" + overflow + ", queue=" + queue + ", broadcast=" + broadcast + '}';
        }

        public boolean isBroadcast() {
            return broadcast;
        }

        public void unicastBroadcast() {
            assert broadcast;
            LOG.debug("Node peer {} is asked to unicast broadcast.", this);
            broadcast = false;
        }

        public void removed() {
            removed = true;
        }

        @Override
        public void sendMessage(Message message) throws InterruptedException {
            synchronized (queue) { // syncrhonization ensures message id is in the order of messages put in the queue
                assignMessageId(message);
                super.sendMessage(message);
            }
        }

        public void sendMessage(Message message, boolean unicastBroadcast) throws InterruptedException {
            if (unicastBroadcast && message.isBroadcast())
                unicastBroadcasts.add(message);
            sendMessage(message);
        }

        void receivePacket(MessagePacket packet) throws InterruptedException {
            final List<Message> received = new ArrayList<Message>(packet.numMessages());
            final List<Message> broadcastResponses = new ArrayList<Message>(packet.numMessages());

            synchronized (this) {
                handleReceived(packet, received, broadcastResponses);
            }

            for (Message message : broadcastResponses)
                broadcastPeer.receivedResponse(message, received);

            recursive.set(Boolean.TRUE);
            try {
                for (Message message : received) {
                    LOG.debug("Passing received message {} to cache", message);
                    receive(message); // XXXX
                }
            } finally {
                recursive.remove();
            }

            call();
        }

        @Override
        public Void call() throws InterruptedException {
            if (recursive.get() == Boolean.TRUE)
                return null;
            recursive.set(Boolean.TRUE);
            try {
                if (removed || getCluster().getMaster(node) == null) {
                    LOG.debug("Node removed from the cluster so returning from peer {}", this);
                    return null; // don't reschedule
                }

                final List<Message> received = new ArrayList<Message>();
                synchronized (this) {
                    LOG.trace("Peer {} CALL", this);

                    final long now = System.nanoTime();

                    handleTimeout(now, received);
                    handleQueue(now);

                    if (sentPacket != null && sentPacket.isEmpty())
                        sentPacket = null;
                    if (sentPacket != null && !broadcast) {
                        if (isTimeToResned(now)) { // if messages have been added to sentPacket has changed, handleQueue sets lastSent to 0
                            LOG.debug("Peer {} sending packet {}", this, sentPacket);
                            channel.write(sentPacket, nodeAddress);
                            if (hasRequests)
                                resend(now);
                        }
                    }
                }

                for (Message message : received)
                    receive(message);
                LOG.trace("Peer {} CALL DONE", this);
                return null;
            } finally {
                recursive.remove();
            }
        }

        private void handleReceived(MessagePacket receivedPacket, List<Message> received, List<Message> broadcastResponses) {
            if (receivedPacket == null)
                return;
            LOG.debug("Peer {} has received packet {}", this, receivedPacket);

            boolean oobMulticast = false;
            if (receivedPacket.isMulticast()) { // multicast messages may overlap with unicast ones if the original broadcast was sent as a unicast, say if the peers sentPacket wasn't empty
                long maxIdInPacket = -1;
                for (Iterator<Message> it = receivedPacket.iterator(); it.hasNext();) {
                    final Message message = it.next();
//                    if (message.getMessageId() < lastReceivedBroadcastId) {
//                        LOG.trace("Peer {} received a multicast message {} which has already been seen.", this, message);
//                        it.remove();
//                    }
                    maxIdInPacket = Math.max(maxIdInPacket, message.getMessageId());
                }
                if (maxIdInPacket < lastReceivedBroadcastId) {
                    LOG.debug("Peer {} received an out-of-band multicast packet {} which has already been seen.", this, receivedPacket);
                    oobMulticast = true;
                }
            }
            if (receivedPacket.isEmpty())
                return;

            if (!oobMulticast && sentPacket != null) {
                for (Iterator<Message> it = sentPacket.iterator(); it.hasNext();) {
                    final Message message = it.next();
                    // here we rely on Message.equals() to match request/response
                    if (message.isResponse() && !receivedPacket.contains(message)) {
                        LOG.debug("Peer {} removing response {} from sent packet because it was no longer asked for.", this, message);
                        it.remove(); // if our peer hasn't requested again then it must have received our response
                    }
                }
            }
            for (Message message : receivedPacket) {
                message.setTimestamp(receivedPacket.getTimestamp());
                if (message.isBroadcast()) {
                    if (message.getMessageId() > lastReceivedBroadcastId)
                        lastReceivedBroadcastId = message.getMessageId();
                }
                // here we rely on Message.equals() to match request/response
                if (message.isResponse()) {
                    final Message request = (sentPacket != null ? sentPacket.getMessage(message) : null);
                    if (request == null && !(isTimeout(message) || (broadcast && broadcastPeer.isTimeout(message)))) {
                        LOG.debug("Peer {} ignoring repeat response {}", this, message);
                        continue; // we may be re-receiving the response, so the request may be gone. in this case we don't need to pass the message again to the receiver
                    }
                    if (LOG.isDebugEnabled())
                        LOG.debug("Peer {} received response {} for request ({})", new Object[]{this, message, request != null ? request : "TIMEOUT"});
                    if (request != null) {
                        if (request.isBroadcast())
                            broadcastResponses.add(message);

//                        if(message.getType() == Message.Type.CHNGD_OWNR && ((Message.CHNGD_OWNR)message).getNewOwner() == message.getNode()) {
//                            // this is a quickReplyToBroadcast
//                            // TODO
//                        }
                        sentPacket.removeMessage(message);
                    }
                } else {
                    if (sentPacket != null && sentPacket.contains(message)) {
                        LOG.debug("Peer {} already has a response for message {}", this, message);
                        continue; // no need to re-generate a response we already have
                    }
                    if (pendingRequests.contains(message.getMessageId())) {
                        LOG.debug("Peer {} already has a request pending for message {}", this, message);
                        continue; // we don't pass on requests to the receiver more than once
                    } else
                        pendingRequests.add(message.getMessageId());
                }

                if (message.getType() == Message.Type.ACK)
                    continue; // we do not pass ACKs on to the receiver

                received.add(message); // getReceiver().receive(message);

                if (!message.isResponse() && !message.isReplyRequired()) {
                    if (!queue.offer(Message.ACK(message))) {
                        LOG.error("Queue capacity for perr {} exceeded", this);
                        throw new RuntimeException("Peer queue full!");
                    }
                }
            }
            //receivedPacket = null;
            if (sentPacket != null) {
                forceResend();
                if (sentPacket.isEmpty()) {
                    sentPacket = null;
                    broadcast = false;
                    hasRequests = false;
                    requestsOnly = true;
                } else {
                    // update hasRequests, requestsOnly and broadcast
                    boolean _hasRequests = false;
                    boolean _requestsOnly = true;
                    boolean _broadcast = true;
                    for (Message message : sentPacket) {
                        if (message.isResponse())
                            _requestsOnly = false;
                        else
                            _hasRequests = true;
                        if (!message.isBroadcast())
                            _broadcast = false;
                    }
                    hasRequests = _hasRequests;
                    requestsOnly = _requestsOnly;
                    if (!broadcast && _broadcast) {
                        LOG.trace("Peer {} notifying broadcast.", this);
                        executor.submit(broadcastPeer);
                    }
                    broadcast = _broadcast;
                }
            }
        }

        private void handleTimeout(long now, List<Message> received) {
            if (broadcast || sentPacket == null || sentPacket.isEmpty())
                return;

            final long timeoutNanos = NANOSECONDS.convert(getTimeout(), MILLISECONDS);
            for (Iterator<Message> it = sentPacket.reverseIterator(); it.hasNext();) {
                final Message message = it.next();
                if (message.getType() != Message.Type.INV && now - message.getTimestamp() > timeoutNanos) {
                    if (message.isResponse() || message.isBroadcast())
                        continue;
                    if (message instanceof LineMessage) {
                        LOG.debug("Timeout on message {}", message);
                        received.add(Message.TIMEOUT((LineMessage) message).setIncoming());
                    }
                    it.remove();
                    addTimeout(message);
                } else
                    break;
            }
            if (sentPacket.isEmpty()) {
                sentPacket = null;
                broadcast = false;
                hasRequests = false;
                requestsOnly = true;
            }

            cleanupTimeouts(now);
        }

        /**
         * Specifies that a message should not be resent, but a response is still possible
         *
         * @param message
         */
        public synchronized void markAsTimeout(Message message) {
            if (sentPacket.removeMessage(message.getMessageId()))
                addTimeout(message);
        }

        private synchronized void handleQueue(long start) throws InterruptedException {
            // ProbLem:
            // assume we send a full packet with requests only, and our peer send us a full packet with requests only.
            // we cannot add requests to the sentPacket b/c it's full, so we must wait for our peer to respond so that we can emty 
            // the packet, only it can't b/c its sentPacket is also full - we got a deadlock.
            // as I see it, the only way to truly resolve it is to have multi-part packets, but we don't want to do that.
            // what we do is that we don't allow a packet with requests only to be full - we always leave room for a response.

            // assumes hasRequests and requestsOnly are up to date.
            Message next = overflow;
            overflow = null;
            if (next == null)
                next = queue.poll();
            for (;;) {
                LOG.trace("handleQueue loop");
                if (next == null) {
                    LOG.trace("handleQueue loop: next == null");
                    break;
                }
                overflow = next; // we put the next message into overflow. if we _don't_ break out of the loop and use the message, we'll null overflow

                final boolean unicastBroadcast = next.isBroadcast() && unicastBroadcasts.remove(next);

                if (broadcast && (!next.isBroadcast() || unicastBroadcast)) {
                    LOG.trace("Node peer {} not taking non-broadcast message {} during broadcast", this, next);
                    break; // we're not taking any non-broadcast messages during broadcast
                }

                if (!broadcast && next.isBroadcast() && !unicastBroadcast) {
                    if (sentPacket == null || sentPacket.isEmpty()) {
                        LOG.debug("Node peer {} going into broadcast mode for message {}.", this, next);
                        broadcast = true;
                    }
                    // else, we add message to packet, and continue transmitting.
                    // if the packet had responses only, the new broadcast request would force a re-send and expedite matters
                    // if a response for the broadcast is received before we get a chance to multicast, that's ok because we simply remove the node
                    // from the BroadcastEntry
                }

                if (next.size() > maxPacketSize) {
                    LOG.error("Message {} is larger than the maximum packet size {}", next, maxPacketSize);
                    throw new RuntimeException("Message is larger than maxPacketSize");
                }

                if (next.size() + sentPacketSizeInBytes() > maxPacketSize) {
                    if (next.isResponse() && requestsOnly)
                        LOG.warn("IMPORTANT: Response message {} does not fit in packet {} which contains only requests. THIS MAY CAUSE A DEADLOCK!", next, sentPacket);
                    LOG.debug("Message {} cannot be added to packet now; packet full (size = {})", next, next.size());
                    break;
                }

                if (!next.isResponse()) {
                    if (requestsOnly && next.size() + sentPacketSizeInBytes() > maxRequestOnlyPacketSize && sentPacketSizeInBytes() > 0) {
                        // check if packet consists of requestOnly message unless it is only one message.
                        LOG.debug("NOT Sending requests only {}. can't add to packet {} bytes long.", next, sentPacketSizeInBytes());
                        break;
                    }
                    hasRequests = true;
                } else
                    requestsOnly = false;

                if (next.isResponse())
                    pendingRequests.remove(next.getMessageId());

                LOG.debug("Adding message {} to sent-packet", next);
                if (sentPacket == null)
                    sentPacket = new MessagePacket();
                sentPacket.addMessage(next);
                forceResend();
                overflow = null;

                if (broadcast) {
                    LOG.trace("Peer {} notifying broadcast.", this);
                    executor.submit(broadcastPeer);
                }

                final long now = System.nanoTime();
                if ((now - start + minDelayNanos) > maxDelayNanos)
                    break;
                next = queue.poll(minDelayNanos, NANOSECONDS);
            }
        }

        private int sentPacketSizeInBytes() {
            return sentPacket != null ? sentPacket.sizeInBytes() : 0;
        }
    }

    class BroadcastPeer extends Peer {
        private final ConcurrentMap<Long, BroadcastEntry> broadcasts = new ConcurrentHashMap<Long, BroadcastEntry>();

        @Override
        public String toString() {
            return "BroadcastPeer{" + "multicastAddress=" + multicastGroup + ", lastSent=" + getLastSent() + ", sentPacket=" + sentPacket + ", next=" + overflow + ", queue=" + queue + '}';
        }

        public void sendMessage(Message message, ShortSet nodes, boolean unicast) throws InterruptedException {
            broadcasts.put(message.getMessageId(), new BroadcastEntry(message, nodes));
            if (!unicast)
                sendMessage(message);
        }

        @Override
        public Void call() throws InterruptedException {
            final List<Message> received = new ArrayList<Message>();
            synchronized (this) {
                LOG.trace("BroadcastPeer CALL");
                final long now = System.nanoTime();

                handleTimeout(now, received);
                handleQueue(now);

                if (sentPacket != null && sentPacket.isEmpty())
                    sentPacket = null;
                if (isTimeToResned(now)) {
                    if (sentPacket != null) { // if messages have been added tos sentPacket has changed, handleQueue sets lastSent to 0
                        assert !sendToServerInsteadOfMulticast;
                        LOG.debug("BroadcastPeer {} multicasting packet {}", this, sentPacket);
                        channel.write(sentPacket, multicastGroup);
                        resend(now);
                    } else if (!broadcasts.isEmpty()) {
                        executor.schedule(this, getTimeout(), MILLISECONDS);
                    }
                }
            }
            for (Message message : received)
                receive(message);
            LOG.trace("BroadcastPeer CALL DONE");
            return null;
        }

        private void handleQueue(long start) throws InterruptedException {
            Message next = overflow;
            overflow = null;
            if (next == null)
                next = queue.poll();
            loop:
            for (;;) {
                if (next == null)
                    break;

                overflow = next; // we put the next message into overflow. if we _don't_ break out of the loop and use the message, we'll null overflow

                if (next.size() > maxPacketSize) {
                    LOG.error("Message {} is larger than the maximum packet size {}", next, maxPacketSize);
                    throw new RuntimeException("Message is larger than maxPacketSize");
                }

                if (sentPacket != null && next.size() + sentPacket.sizeInBytes() > maxPacketSize)
                    break;

                LOG.debug("Waiting for peers to enter broadcast mode for message {}", next);
                BroadcastEntry entry = broadcasts.get(next.getMessageId());

                if (entry != null) {
                    if (entry.nodes.isEmpty()) {
                        broadcasts.remove(next.getMessageId());
                        if (next instanceof LineMessage) {
                            LOG.debug("No other nodes in cluster. Responding with NOT_FOUND to message {}", next);
                            receive(Message.NOT_FOUND((LineMessage) next).setIncoming());
                        }
                        entry = null;
                    }
                }

                if (entry != null) {
                    for (ShortIterator it = entry.nodes.iterator(); it.hasNext();) {
                        final short node = it.next();
                        final NodePeer peer = peers.get(node);
                        synchronized (peer) {
                            if (!(peer.isBroadcast() && peer.sentPacket.contains(next.getMessageId()))) {
                                LOG.trace("Waiting for peer {}.", peer);
                                break loop;
                            }
                            LOG.trace("Peer {} ok (broadcast {})", peer, next);
                        }
                    }

                    LOG.debug("Adding message {} to sent-packet", next);
                    if (sentPacket == null)
                        sentPacket = new MessagePacket();
                    sentPacket.addMessage(next);
                    forceResend();
                }

                overflow = null;

                final long now = System.nanoTime();
                if (maxDelayNanos > (now - start + minDelayNanos))
                    break;
                next = queue.poll(minDelayNanos, NANOSECONDS);
            }
        }

        private void handleTimeout(long now, List<Message> received) {
            if (broadcasts.isEmpty())
                return;
            final long timeoutNanos = NANOSECONDS.convert(getTimeout(), MILLISECONDS);

            for (Iterator<BroadcastEntry> it = broadcasts.values().iterator(); it.hasNext();) {
                final BroadcastEntry entry = it.next();
                final Message message = entry.message;
                if (message.getType() != Message.Type.INV && now - message.getTimestamp() > timeoutNanos) {
                    if (message instanceof LineMessage) {
                        LOG.debug("Timeout on message {}", message);
                        received.add(Message.TIMEOUT((LineMessage) message).setIncoming());
                    }
                    it.remove();
                    releasePeers(entry, (short) -1);
                    addTimeout(message);
                    if (sentPacket != null)
                        sentPacket.removeMessage(message.getMessageId());
                }
            }
            if (sentPacket != null && sentPacket.isEmpty())
                sentPacket = null;

            cleanupTimeouts(now);
        }

        public void receivedResponse(Message message, List<Message> received) {
            final BroadcastEntry entry = broadcasts.get(message.getMessageId());
            if (entry == null)
                return;
            synchronized (this) {
                boolean done = entry.removeNode(message.getNode());
                if (message.getType() != Message.Type.ACK) {// this is a response - no need to wait for further acks
                    LOG.debug("Message {} is a reply to a broadcast! (discarding pending)", message);
                    if (!done)
                        releasePeers(entry, message.getNode());
                    done = true;
                } else {
                    if (LOG.isDebugEnabled())
                        LOG.debug("Got ACK from {} to message {}", message.getNode(), entry.message);
                    final int numNodes = entry.nodes.size();
                    if (done) {
                        if (entry.message instanceof LineMessage) {
                            LOG.debug("Got all ACKs for message {}, but no response - sending NOT_FOUND to cache!", entry.message);
                            received.add(Message.NOT_FOUND((LineMessage) entry.message).setIncoming());
                        }
                    } else if (numNodes < minimumNodesToMulticast && (numNodes + 1) >= minimumNodesToMulticast) {
                        if (sentPacket != null)
                            sentPacket.removeMessage(message.getMessageId()); // don't multicast...

                        // unicast:
                        final long now = System.nanoTime();
                        final long sinceLastSent = now - getLastSent();
                        long delay = resendPeriodNanos - sinceLastSent;
                        delay = (delay >= 0 ? delay : 0);
                        for (ShortIterator it = entry.nodes.iterator(); it.hasNext();) {
                            final NodePeer peer = peers.get(it.next());
                            if (peer.isBroadcast()) {
                                peer.unicastBroadcast();
                                peer.forceResend();
                                peer.resendIn(now, delay);
                                executor.submit(peer);
                            }
                        }
                    }
                }

                if (done) {
                    if (sentPacket != null)
                        sentPacket.removeMessage(message.getMessageId());
                    broadcasts.remove(message.getMessageId());
                }
                if (sentPacket != null && sentPacket.isEmpty())
                    sentPacket = null;
            }
        }

        private void releasePeers(BroadcastEntry entry, short node) {
            final Message message = entry.message;
            for (ShortIterator it = entry.nodes.iterator(); it.hasNext();) {
                final NodePeer peer = peers.get(it.next());
                if (peer.isBroadcast()) {
                    LOG.debug("Broadcast releasing peer {} for message {}", peer, message);
                    if (peer.node != node) {
                        LOG.debug("Broadcast marking message {} as timeout for peer {}", message, peer);
                        peer.markAsTimeout(message);
                    }
                    peer.unicastBroadcast();
                    executor.submit(peer);
                }
            }
        }

        public void removeNode(short node) {
            synchronized (this) {
                for (Iterator<Map.Entry<Long, BroadcastEntry>> it = broadcasts.entrySet().iterator(); it.hasNext();) {
                    BroadcastEntry entry = it.next().getValue();
                    if (entry.removeNode(node) && entry.message instanceof LineMessage) {
                        LOG.debug("Got all ACKs for message {}, but no response - sending NOT_FOUND to cache!", entry.message);
                        receive(Message.NOT_FOUND((LineMessage) entry.message).setIncoming());
                        it.remove();
                    }
                }
            }
        }
    }

    private static class BroadcastEntry {
        final Message message;
        final ShortSet nodes;

        public BroadcastEntry(Message message, ShortSet nodes) {
            this.message = message;
            this.nodes = nodes;
            this.nodes.remove(Comm.SERVER); // NOT TO SERVER
            LOG.debug("Awaiting ACKS for message {} from nodes {}", message, this.nodes);
        }

        public synchronized void addNode(short node) {
            nodes.add(node);
        }

        public synchronized boolean removeNode(short node) {
            nodes.remove(node);
            return nodes.isEmpty();
        }
    }

    private int getNumPeerNodes() {
        return getCluster().getNodes().size() - (getCluster().getNodes().contains(Comm.SERVER) ? 1 : 0) + 1;
    }

    private static long randInterval(long expected) {
        return (long) randExp(1.0 / expected);
    }

    /**
     * Return a real number from an exponential distribution with rate lambda. Based on
     * http://en.wikipedia.org/wiki/Inverse_transform_sampling
     */
    private static double randExp(double lambda) {
        return -Math.log(1 - ThreadLocalRandom.current().nextDouble()) / lambda;
    }

    BroadcastPeer getBroadcastPeer() {
        return broadcastPeer;
    }

    ConcurrentMap<Short, NodePeer> getPeers() {
        return peers;
    }
}
