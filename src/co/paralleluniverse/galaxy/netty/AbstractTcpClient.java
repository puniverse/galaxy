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

import static co.paralleluniverse.common.collection.Util.reverse;
import co.paralleluniverse.common.concurrent.CustomThreadFactory;
import co.paralleluniverse.common.monitoring.ThreadPoolExecutorMonitor;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.core.ClusterService;
import co.paralleluniverse.galaxy.core.CommThread;
import co.paralleluniverse.galaxy.core.Message;
import com.google.common.base.Throwables;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
abstract class AbstractTcpClient extends ClusterService {

    private final Logger LOG = LoggerFactory.getLogger(AbstractTcpClient.class.getName() + "." + getName());
    //
    private String nodeName;
    private final String portProperty;
    private InetSocketAddress address;
    private final ChannelPipelineFactory origChannelFacotry;
    private final ChannelFactory channelFactory;
    private final ClientBootstrap bootstrap;
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

        if (bossExecutor == null)
            bossExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        if (workerExecutor == null)
            workerExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        configureThreadPool(name + "-tcpClientBoss", bossExecutor);
        configureThreadPool(name + "-tcpClientWorker", workerExecutor);
        if (receiveExecutor != null)
            configureThreadPool(name + "-tcpClientReceive", receiveExecutor);

        this.channelFactory = new NioClientSocketChannelFactory(bossExecutor, workerExecutor);
        this.bootstrap = new ClientBootstrap(channelFactory);

        origChannelFacotry = new TcpMessagePipelineFactory(LOG, null, receiveExecutor) {

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

        bootstrap.setOption("tcpNoDelay", true);
        bootstrap.setOption("keepAlive", true);
        
        reconnect = true;
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
        executor.setThreadFactory(new CustomThreadFactory(name) {

            @Override
            protected Thread allocateThread(ThreadGroup group, Runnable target, String name) {
                return new CommThread(group, target, name);
            }

        });
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
            for (;;) {
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
                if(!connecting) {
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
            channelLock.lock();
            try {
                if (ctx.getChannel() == channel) {
                    setReady(false);
                    if (channel != null)
                        channel.close();
                    channel = null;
                    connectLater();
                }
            } finally {
                channelLock.unlock();
            }
            super.channelDisconnected(ctx, e);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            channelLock.lock();
            try {
                LOG.info("Channel exception: {} {}", e.getCause().getClass().getName(), e.getCause().getMessage());
                LOG.debug("Channel exception", e.getCause());
                setReady(false);
                if (channel != null)
                    channel.close();
                channel = null;
            } finally {
                channelLock.unlock();
                connectLater();
            }
        }

    };
}
