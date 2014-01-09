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

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.cluster.ReaderWriters;
import co.paralleluniverse.galaxy.cluster.SlaveConfigurationListener;
import co.paralleluniverse.galaxy.core.Backup;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.Message.BACKUP;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKET;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKETACK;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.SlaveComm;
import static co.paralleluniverse.galaxy.netty.IpConstants.*;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import java.beans.ConstructorProperties;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ServerChannel;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Right now, because we can only have one slave anyway (due to consensus), this class has been simplified and assumes one slave.
 * If and when this is fixed, it's ok to assume one backup packet at a time, i.e., we don't send a new one until we get a
 * response. This is because backups are asynchronous, and BackupImpl buffers them.
 *
 * With INVs, however, things are more complicated, as they are synchronous, and we'd like to send them as fast as possible, and
 * not wait until the previous has been acked by all before we inform Backup, so it's a little more effort to keep track of
 * multiple slaves (it would simply require some more bookkeeping).
 *
 * @author pron
 */
final class TcpSlaveServerComm extends AbstractTcpServer implements SlaveComm {
    // When writing this class I struggled with a choice: should this component be independent with a very thin API (slave comm)
    // i.e. gather responses from all slaves
    // or should all the logic be in Backup and make SlaveComm a wide, generic API.
    // An independent component provides greater flexibility (and, possibly, performance), while a generic API allows code sharing.
    // I've decided that as long as there is one implementation, any generic API would be arbitrary, probably wrong, and a waste of time.

    private static final Logger LOG = LoggerFactory.getLogger(TcpSlaveServerComm.class);
    private Backup backup;
    private boolean sentSlave; // Set<Channel> sentSlaves; use a simple flag for one server, just to keep track. not really necessary with one slave.
    private final ConcurrentMap<Channel, Iterator<BACKUP>> replIters = new ConcurrentHashMap<Channel, Iterator<BACKUP>>();
    private long lastId;
    private volatile Thread replThread;

    @ConstructorProperties({"name", "cluster", "port"})
    public TcpSlaveServerComm(String name, Cluster cluster, int port) throws Exception {
        this(name, cluster, port, null);
    }

    TcpSlaveServerComm(String name, final Cluster cluster, int port, final ChannelHandler testHandler) throws Exception {
        super(name, cluster, new ChannelGroup(), port, testHandler);

        cluster.addNodeProperty(IP_ADDRESS, true, true, INET_ADDRESS_READER_WRITER);
        cluster.setNodeProperty(IP_ADDRESS, InetAddress.getLocalHost());
        cluster.addNodeProperty(IP_SLAVE_PORT, true, false, ReaderWriters.INTEGER);
        cluster.setNodeProperty(IP_SLAVE_PORT, port);

        cluster.addSlaveConfigurationListener(new SlaveConfigurationListener() {

            @Override
            public void newMaster(NodeInfo node) {
            }

            @Override
            public void slaveAdded(NodeInfo node) {
            }

            @Override
            public void slaveRemoved(NodeInfo node) {
                final Channel channel = getChannels().get(node);
                if (channel != null) {
                    LOG.info("Closing channel for removed node {}", node);
                    channel.close();
                }
            }

        });
    }

    @Override
    public void setBackup(Backup backup) {
        assertDuringInitialization();
        this.backup = backup;
    }

    @Override
    protected void postInit() throws Exception {
        super.postInit();
    }

    @Override
    protected void init() throws Exception {
        super.init();
    }

    @Override
    protected void available(boolean value) {
        super.available(value);
    }

    @Override
    protected void start(boolean master) {
        if (master) {
            bind();
            startReplicationThread();
        }
    }

    @Override
    public void switchToMaster() {
        super.switchToMaster();
        bind();
        startReplicationThread();
    }

    @Override
    public void shutdown() {
        replThread.interrupt();
        super.shutdown();
    }

    @Override
    protected ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = super.getPipeline();
        pipeline.addLast("connections", new SimpleChannelUpstreamHandler() {

            @Override
            public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                if (getChannels().size() > 2) { // 2 b/c one is the server channel
                    throw new RuntimeException("Only one slave is currently supported! - " + new ArrayList<Channel>(getChannels()));
                }
                final InetAddress remoteAddress = ((InetSocketAddress) ctx.getChannel().getRemoteAddress()).getAddress();
                if (getCluster().getNodesByProperty(IP_ADDRESS, remoteAddress).isEmpty()) {
                    LOG.warn("An attempt to connect from an unrecognized address {}. No registered cluster node has this address.", remoteAddress);
                    ctx.getChannel().close();
                    return;
                }

                replIters.put(ctx.getChannel(), backup.iterOwned());
                synchronized (replIters) {
                    replIters.notify();
                }
                super.channelConnected(ctx, e);
            }

            @Override
            public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                ack(ctx, null);
                replIters.remove(ctx.getChannel());
                super.channelDisconnected(ctx, e);
            }

        });
        return pipeline;
    }

    @Override
    protected void receive(ChannelHandlerContext ctx, Message message) {
        switch (message.getType()) {
            case BACKUP_PACKETACK:
                ack(ctx, (BACKUP_PACKETACK) message);
                break;
            case INVACK:
                invack(ctx, (LineMessage) message);
                break;
            default:
                LOG.warn("Unhandled message: {}", message);
        }
    }

    private void ack(ChannelHandlerContext ctx, BACKUP_PACKETACK ack) {

//        boolean allAck = false;
        synchronized (this) {
            if (ack != null && ack.getId() != lastId) {
                LOG.warn("Received backup ack id {} which is different from last sent: {}", ack.getId(), lastId);
                return;
            }
//            if (sentSlaves == null || !sentSlaves.remove(ctx.getChannel())) {
//                LOG.warn("Received backup ack from an unexpected node {}", ctx.getChannel());
//                return;
//            }
            LOG.debug("Received backup ack from slave {}", ctx.getChannel());
//            if (sentSlaves.isEmpty()) {
            sentSlave = false;
//                allAck = true;
//            }
        }
//        if (allAck)
        backup.slavesAck(lastId);
    }

    private void invack(ChannelHandlerContext ctx, LineMessage invack) {
        backup.slavesInvAck(invack.getLine());
    }

    private static NodeInfo getNodeInfo(Channel channel) {
        return ChannelNodeInfo.nodeInfo.get(channel);
    }

    @Override
    public synchronized boolean send(Message message) {
        if (message.getType() == Message.Type.BACKUP_PACKET && sentSlave)
            throw new RuntimeException("Previous backup not handled yet!");

        if (!message.isResponse())
            message.setMessageId(nextMessageId());
        LOG.debug("Send {}", message);

        final Set<Channel> slaves = new HashSet<Channel>();
        final ChannelGroupFuture fs = getChannels().write(message);
        for (ChannelFuture f : fs)
            slaves.add(f.getChannel());

        if (slaves.isEmpty()) {
            LOG.debug("No slaves... Returning false");
            return false;
        } else
            LOG.debug("Sending to slaves: {}", slaves);

        if (slaves.size() > 1)
            throw new RuntimeException("Only one slave is currently supported! - " + slaves);

        switch (message.getType()) {
            case INV:
                return true;
            case BACKUP_PACKET:
                lastId = ((BACKUP_PACKET) message).getId();
                sentSlave = true;
                return true;
            default:
                LOG.warn("Unhandled message: {}", message);
                return false;
        }

    }

    @Override
    protected ChannelGroup getChannels() {
        return (ChannelGroup) super.getChannels();
    }

    private static class ChannelGroup extends DefaultChannelGroup {

        private final BiMap<NodeInfo, Channel> channels = Maps.synchronizedBiMap((HashBiMap) HashBiMap.create());

        public ChannelGroup(String name) {
            super(name);
        }

        public ChannelGroup() {
        }

        @Override
        public boolean add(Channel channel) {
            if (channel instanceof ServerChannel)
                return super.add(channel);
            else {
                final NodeInfo node = getNodeInfo(channel);
                if (node == null) {
                    LOG.warn("Received connection from an unknown address {}.", channel.getRemoteAddress());
                    throw new RuntimeException("Unknown node for address " + channel.getRemoteAddress());
                }
                final boolean added = super.add(channel);
                if (added)
                    channels.put(node, channel);
                return added;
            }
        }

        @Override
        public boolean remove(Object o) {
            final Channel channel = (Channel) o;
            final boolean removed = super.remove(o);
            if (removed)
                channels.inverse().remove(channel);
            ChannelNodeInfo.nodeInfo.remove(channel);
            return removed;
        }

        @Override
        public void clear() {
            super.clear();
            channels.clear();
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof NodeInfo)
                return channels.containsKey((NodeInfo) o);
            else
                return super.contains(o);
        }

        public Channel get(NodeInfo node) {
            return channels.get(node);
        }

        public NodeInfo get(Channel channel) {
            return channels.inverse().get(channel);
        }

    }

    private void startReplicationThread() {
        if (this.replThread != null)
            return;
        this.replThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        synchronized (replIters) {
                            while (replIters.isEmpty())
                                replIters.wait();
                        }

                        for (Iterator<Map.Entry<Channel, Iterator<BACKUP>>> entryIter = replIters.entrySet().iterator(); entryIter.hasNext();) {
                            final Map.Entry<Channel, Iterator<BACKUP>> entry = entryIter.next();
                            final Channel channel = entry.getKey();
                            final Iterator<BACKUP> iter = entry.getValue();

                            for (int i = 0; i < 10; i++) {
                                if (iter.hasNext()) {
                                    final BACKUP backup = iter.next();
                                    LOG.debug("Replicating {} to channel {}", backup, channel);
                                    channel.write(backup);
                                } else {
                                    channel.write(Message.BACKUP(-1, -1, null)); // marks the end of the stream
                                    LOG.debug("Finished replicating to channel {}", channel);
                                    entryIter.remove(); // we're done
                                    break;
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                }
                LOG.info("Replication thread interrupted");
            }

        });
        replThread.setName("backup-replication");
        replThread.setDaemon(true);
        replThread.setPriority(Thread.NORM_PRIORITY - 1);
        replThread.start();
    }

}
