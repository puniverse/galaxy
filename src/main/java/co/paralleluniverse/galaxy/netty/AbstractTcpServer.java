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

import co.paralleluniverse.common.concurrent.CustomThreadFactory;
import co.paralleluniverse.common.monitoring.ThreadPoolExecutorMonitor;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.core.ClusterService;
import co.paralleluniverse.galaxy.core.CommThread;
import co.paralleluniverse.galaxy.core.Message;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 *
 * @author pron
 */
public abstract class AbstractTcpServer extends ClusterService {
    private final Logger LOG = LoggerFactory.getLogger(AbstractTcpServer.class.getName() + "." + getName());
    //
    private final int port;
    private final ChannelFactory channelFactory;
    private final ServerBootstrap bootstrap;
    private final DefaultChannelGroup channels;
    private final AtomicLong nextMessageId = new AtomicLong(1L);
    private final ChannelPipelineFactory origChannelFacotry;
    private ThreadPoolExecutor bossExecutor;
    private ThreadPoolExecutor workerExecutor;
    private OrderedMemoryAwareThreadPoolExecutor receiveExecutor;

    AbstractTcpServer(String name, final Cluster cluster, DefaultChannelGroup channels, int port, final ChannelHandler testHandler) {
        super(name, cluster);
        this.channels = channels;
        this.port = port;
        
        if(bossExecutor == null)
            bossExecutor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
        if(workerExecutor == null)
            workerExecutor = (ThreadPoolExecutor)Executors.newCachedThreadPool();
        configureThreadPool(name + "-tcpServerBoss", bossExecutor);
        configureThreadPool(name + "-tcpServerWorker", workerExecutor);
        if(receiveExecutor != null)
            configureThreadPool(name + "-tcpServerReceive", receiveExecutor);

        this.channelFactory = new NioServerSocketChannelFactory(bossExecutor, workerExecutor);
        this.bootstrap = new ServerBootstrap(channelFactory);

        origChannelFacotry = new TcpMessagePipelineFactory(LOG, channels, receiveExecutor) {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                final ChannelPipeline pipeline = super.getPipeline();
                pipeline.addBefore("messageCodec", "nodeNameReader", new ChannelNodeNameReader(cluster));
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

    public AbstractTcpServer(String name, Cluster cluster, DefaultChannelGroup channels, int port) {
        this(name, cluster, channels, port, null);
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
