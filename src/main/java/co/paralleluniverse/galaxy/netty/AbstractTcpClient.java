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
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.core.ClusterService;
import co.paralleluniverse.galaxy.core.CommThread;
import co.paralleluniverse.galaxy.core.Message;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientBossPool;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static co.paralleluniverse.common.collection.Util.reverse;
import static co.paralleluniverse.galaxy.netty.NettyUtils.KEEP_UNCHANGED_DETERMINER;

/**
 * @author pron
 */
abstract class AbstractTcpClient extends ClusterService {
    private final Logger LOG = LoggerFactory.getLogger(AbstractTcpClient.class.getName() + "." + getName());
    //
    private String nodeName;
    private final String portProperty;
    private InetSocketAddress address;
    private ChannelPipelineFactory origChannelFacotry;
    private ChannelFactory channelFactory;
    private ClientBootstrap bootstrap;
    private boolean connecting;
    private Channel channel;
    private volatile boolean reconnect;
    private final Lock channelLock = new ReentrantLock();
    private final Condition channelConnected = channelLock.newCondition();
    private final Deque<Message> pendingReply = new ConcurrentLinkedDeque<Message>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ThreadPoolExecutor bossExecutor;
    private ThreadPoolExecutor workerExecutor;
    private OrderedMemoryAwareThreadPoolExecutor receiveExecutor;


    public AbstractTcpClient(String name, final Cluster cluster, final String portProperty) throws Exception {
        super(name, cluster);
        this.portProperty = portProperty;
        reconnect = true;
    }

    @Override
    protected void init() throws Exception {
        super.init();
        if (bossExecutor == null)
            bossExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        if (workerExecutor == null)
            workerExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        final short currentNodeId = getCluster().getMyNodeId();
        configureThreadPool(currentNodeId + "-" + getName() + "-tcpClientBoss", bossExecutor);
        configureThreadPool(currentNodeId + "-" + getName() + "-tcpClientWorker", workerExecutor);
        if (receiveExecutor != null)
            configureThreadPool(currentNodeId + "-" + getName() + "-tcpClientReceive", receiveExecutor);

        this.channelFactory = new NioClientSocketChannelFactory(
                new NioClientBossPool(bossExecutor, NettyUtils.DEFAULT_BOSS_COUNT, new HashedWheelTimer(), KEEP_UNCHANGED_DETERMINER),
                new NioWorkerPool(workerExecutor, NettyUtils.getWorkerCount(workerExecutor), KEEP_UNCHANGED_DETERMINER));
        this.bootstrap = new ClientBootstrap(channelFactory);

        final Cluster cluster = getCluster();
        this.origChannelFacotry = new TcpMessagePipelineFactory(LOG, null, receiveExecutor) {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                final ChannelPipeline pipeline = super.getPipeline();
                pipeline.addBefore("messageCodec", "nodeNameWriter", new ChannelNodeNameWriter(cluster));
                pipeline.addBefore("nodeNameWriter", "nodeInfoSetter", new SimpleChannelUpstreamHandler() {
                    @Override
                    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                        if (nodeName == null)
                            throw new RuntimeException("nodeName not set!");
                        final NodeInfo ni = cluster.getNodeInfoByName(nodeName);
                        ChannelNodeInfo.nodeInfo.set(ctx.getChannel(), ni);
                        super.channelConnected(ctx, e);
                        pipeline.remove(this);
                    }
                });
                pipeline.addLast("router", channelHandler);
                return pipeline;
            }
        };
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return AbstractTcpClient.this.getPipeline();
            }
        });

        bootstrap.setOption("localAddress", new InetSocketAddress(InetAddress.getLocalHost(), 0));
        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down.");
        disconnect();
        channelFactory.releaseExternalResources();
        executor.shutdownNow();
    }

    public void setBossExecutor(ThreadPoolExecutor bossExecutor) {
        assertDuringInitialization();
        this.bossExecutor = bossExecutor;
    }

    public void setWorkerExecutor(ThreadPoolExecutor workerExecutor) {
        assertDuringInitialization();
        this.workerExecutor = workerExecutor;
    }

    public void setReceiveExecutor(OrderedMemoryAwareThreadPoolExecutor receiveExecutor) {
        assertDuringInitialization();
        this.receiveExecutor = receiveExecutor;
    }

    private void configureThreadPool(String name, ThreadPoolExecutor executor) {
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setThreadFactory(new ThreadFactoryBuilder().setNameFormat(name + "-%d").setThreadFactory(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new CommThread(r);
            }
        }).build());
        ThreadPoolExecutorMonitor.register(name, executor);
    }

    protected ChannelPipeline getPipeline() throws Exception {
        return origChannelFacotry.getPipeline();
    }

    protected void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    protected String getNodeName() {
        return nodeName;
    }

    private InetSocketAddress getAddress(NodeInfo node, String portProperty) {
        final InetAddress _address = (InetAddress) node.get(IpConstants.IP_ADDRESS);
        final Integer port = (Integer) node.get(portProperty);
        if (_address == null || port == null) {
            if (_address == null)
                LOG.warn("Socket address (property {}) not set for node {}", IpConstants.IP_ADDRESS, node);
            if (port == null)
                LOG.warn("Socket port (property {}) not set for node {}", portProperty, node);
            return null;
        }
        InetSocketAddress socket = new InetSocketAddress(_address, port);
        return socket;
    }

    protected void reconnect(String nodeName) {
        if (nodeName == null)
            throw new IllegalArgumentException("nodeName cannot be null!");
        channelLock.lock();
        try {
            if (!nodeName.equals(this.nodeName)) {
                disconnect();
                setNodeName(nodeName);
            }
        } finally {
            channelLock.unlock();
        }
        reconnect = true;
        connectLater();
    }

    /**
     * We don't want to expose this method. Here's why: the connected event may only be called after a while (say, after the
     * node's name has been written), so it's quite possible that two connection attempts will happen at the same time. The first
     * will start but the event, wouldn't be called yet, the second would see that isConnected() is false, and try again, while
     * the first is really in the process of connecting.
     *
     * @return
     */
    private boolean isConnected() {
        channelLock.lock();
        try {
            return channel != null;
        } finally {
            channelLock.unlock();
        }
    }

    protected void connectLater() {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                connect();
            }
        });
    }

    private void connect() {
        try {
            for (; ; ) {
                channelLock.lock();
                try {
                    if (!reconnect || Thread.interrupted())
                        return;
                    if (channel != null)
                        return;

                    address = getAddress(getCluster().getNodeInfoByName(nodeName), portProperty);
                    if (address == null) {
                        LOG.warn("No address found for node {}", nodeName);
                        return;
                    }

                    LOG.info("Connecting to node {} at {}...", nodeName, address);
                    connecting = true;
                    ChannelFuture future = bootstrap.connect(address);
                    future.awaitUninterruptibly();
                    // channel = future.getChannel(); ??????
                    if (future.isSuccess()) {
                        LOG.info("Connecting to {} - successful", address);
                        channelConnected.signalAll();
                        break;
                    }
                } catch (ChannelException e) {
                    LOG.warn("ChannelException", e);
                } catch (Exception e) {
                    LOG.error("Exception", e);
                    throw Throwables.propagate(e);
                } finally {
                    channelLock.unlock();
                }
                LOG.info("Connection to {} failed. Retrying.", address);
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
        }
    }

    protected void disconnect() {
        LOG.info("Disconnecting from node {} - {}", nodeName, address);
        channelLock.lock();
        try {
            connecting = false;
            reconnect = false;
            if (channel != null) {
                LOG.debug("Closing channel {}", channel);
                channel.close().awaitUninterruptibly();
            }
            channel = null;
        } finally {
            channelLock.unlock();
        }
    }

    private Channel getChannel() {
        Channel _channel = channel;
        if (_channel == null) {
            channelLock.lock();
            try {
                while (channel == null)
                    channelConnected.await();
                return channel;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                channelLock.unlock();
            }
        } else
            return _channel;
    }

    public void send(Message message) {
        LOG.debug("Send {}", message);
        if (!message.getType().isOf(Message.Type.REQUIRES_RESPONSE)) {
            LOG.debug("Message {} does not require a response.", message);
        } else
            pendingReply.addFirst(message);

        channelLock.lock();
        try {
            if (channel != null) {
                channel.write(message);
                LOG.debug("Message {} written", message);
            } else
                LOG.debug("Message {} not written b/c channel is not yet connected. Keeping as pending.", message);
        } finally {
            channelLock.unlock();
        }
    }

    abstract protected void receive(ChannelHandlerContext ctx, Message message);

    private final ChannelHandler channelHandler = new SimpleChannelHandler() {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
            final Message message = (Message) e.getMessage();
            LOG.debug("Received {}", message);
            pendingReply.removeLastOccurrence(message); // relies on Message.equals that matches request/reply
            receive(ctx, message);
        }

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            channelLock.lock();
            try {
                channel = e.getChannel();
                if (!connecting) {
                    LOG.info("Asked to disconnect from newly connected channel {}. Closing.", channel);
                    channel.close();
                    return;
                }
                LOG.debug("Set channel to {}", channel);
                for (Message pending : reverse(pendingReply)) {
                    LOG.debug("Sending pending message {} (channel connected)", pending);
                    channel.write(pending);
                    LOG.debug("Message {} written", pending);
                }
                setReady(true);
                connecting = false;
                channelConnected.signalAll();
            } finally {
                channelLock.unlock();
            }
            super.channelConnected(ctx, e);
        }

        @Override
        public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
            resetConnectionState(ctx.getChannel());
            super.channelDisconnected(ctx, e);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            LOG.info("Channel {} exception: {} {}", e.getChannel(), e.getCause().getClass().getName(), e.getCause().getMessage());
            LOG.debug("Channel {} exception", e.getChannel(), e.getCause());
            resetConnectionState(ctx.getChannel());
        }
    };

    private void resetConnectionState(Channel contextChannel) {
        channelLock.lock();
        try {
            if (contextChannel == channel) {
                setReady(false);
                if (channel != null)
                    channel.close();
                channel = null;
                connectLater();
            }
        } finally {
            channelLock.unlock();
        }
    }
}
