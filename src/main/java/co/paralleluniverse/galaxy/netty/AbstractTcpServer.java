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
import co.paralleluniverse.galaxy.core.ClusterService;
import co.paralleluniverse.galaxy.core.CommThread;
import co.paralleluniverse.galaxy.core.Message;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import static co.paralleluniverse.galaxy.netty.NettyUtils.KEEP_UNCHANGED_DETERMINER;

/**
 * @author pron
 */
public abstract class AbstractTcpServer extends ClusterService {
    private final Logger LOG = LoggerFactory.getLogger(AbstractTcpServer.class.getName() + "." + getName());
    //
    private final int port;
    private ChannelFactory channelFactory;
    private ServerBootstrap bootstrap;
    private final DefaultChannelGroup channels;
    private final AtomicLong nextMessageId = new AtomicLong(1L);
    private ChannelPipelineFactory origChannelFacotry;
    private final ChannelHandler testHandler;
    private ThreadPoolExecutor bossExecutor;
    private ThreadPoolExecutor workerExecutor;
    private OrderedMemoryAwareThreadPoolExecutor receiveExecutor;

    AbstractTcpServer(String name, final Cluster cluster, DefaultChannelGroup channels, int port, final ChannelHandler testHandler) {
        super(name, cluster);
        this.channels = channels;
        this.port = port;
        this.testHandler = testHandler;
    }

    public AbstractTcpServer(String name, Cluster cluster, DefaultChannelGroup channels, int port) {
        this(name, cluster, channels, port, null);
    }

    @Override
    protected void init() throws Exception {
        super.init();
        if (bossExecutor == null)
            bossExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        if (workerExecutor == null)
            workerExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        final short currentNodeId = getCluster().getMyNodeId();
        configureThreadPool(currentNodeId + "-" + getName() + "-tcpServerBoss", bossExecutor);
        configureThreadPool(currentNodeId + "-" + getName() + "-tcpServerWorker", workerExecutor);
        if (receiveExecutor != null)
            configureThreadPool(currentNodeId + "-" + getName() + "-tcpServerReceive", receiveExecutor);

        channelFactory = new NioServerSocketChannelFactory(
                new NioServerBossPool(bossExecutor, NettyUtils.DEFAULT_BOSS_COUNT, KEEP_UNCHANGED_DETERMINER),
                new NioWorkerPool(workerExecutor, NettyUtils.getWorkerCount(workerExecutor), KEEP_UNCHANGED_DETERMINER));
        bootstrap = new ServerBootstrap(channelFactory);

        origChannelFacotry = new TcpMessagePipelineFactory(LOG, channels, receiveExecutor) {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                final ChannelPipeline pipeline = super.getPipeline();
                pipeline.addBefore("messageCodec", "nodeNameReader", new ChannelNodeNameReader(getCluster()));
                pipeline.addLast("router", channelHandler);
                if (testHandler != null)
                    pipeline.addLast("test", testHandler);
                return pipeline;
            }
        };

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                return AbstractTcpServer.this.getPipeline();
            }
        });
        //bootstrap.setParentHandler(new LoggingHandler(LOG));

        bootstrap.setOption("reuseAddress", true);
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);
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

    private final ChannelHandler channelHandler = new SimpleChannelHandler() {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
            final Message message = (Message) e.getMessage();
            LOG.debug("Received {}", message);
            receive(ctx, message);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
            LOG.info("Channel exception: {} {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            ctx.getChannel().close();
        }
    };

    protected ChannelPipeline getPipeline() throws Exception {
        return origChannelFacotry.getPipeline();
    }

    abstract protected void receive(ChannelHandlerContext ctx, Message message);

    protected void bind() {
        Channel channel = bootstrap.bind(new InetSocketAddress(port));
        channels.add(channel);
        LOG.info("Channel {} listening on port {}", channel, port);
        setReady(true);
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down.");
        channels.close().awaitUninterruptibly();
        channelFactory.releaseExternalResources();
    }

    protected DefaultChannelGroup getChannels() {
        return channels;
    }

    protected long nextMessageId() {
        return nextMessageId.getAndIncrement();
    }

    @ManagedAttribute
    public int getPort() {
        return port;
    }
}
