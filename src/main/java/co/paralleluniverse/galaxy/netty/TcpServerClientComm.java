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
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.cluster.ReaderWriters;
import co.paralleluniverse.galaxy.core.Comm;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.MessageReceiver;
import co.paralleluniverse.galaxy.core.ServerComm;
import static co.paralleluniverse.galaxy.netty.IpConstants.*;
import java.beans.ConstructorProperties;
import java.net.InetAddress;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
class TcpServerClientComm extends AbstractTcpClient implements ServerComm {

    private static final Logger LOG = LoggerFactory.getLogger(TcpServerClientComm.class);
    private MessageReceiver receiver;

    @ConstructorProperties({"name", "cluster"})
    public TcpServerClientComm(String name, Cluster cluster) throws Exception {
        super(name, cluster, IP_SERVER_PORT);

        cluster.addNodeProperty(IP_ADDRESS, true, true, INET_ADDRESS_READER_WRITER);
        cluster.setNodeProperty(IP_ADDRESS, InetAddress.getLocalHost());
        cluster.addNodeProperty(IP_SERVER_PORT, false, true, ReaderWriters.INTEGER);

        cluster.addNodeChangeListener(new NodeChangeListener() {

            @Override
            public void nodeAdded(short id) {
                if (getCluster().isMaster() && id == Comm.SERVER) {
                    LOG.info("Server added! Connecting.");
                    setNodeName(getCluster().getMaster(Comm.SERVER).getName());
                    connectLater();
                }
            }

            @Override
            public void nodeSwitched(short id) {
                if (getCluster().isMaster() && id == Comm.SERVER) {
                    LOG.info("Server switched! Reconnecting.");
                    reconnect(getCluster().getMaster(Comm.SERVER).getName());
                }
            }

            @Override
            public void nodeRemoved(short id) {
                if (getCluster().isMaster() && id == Comm.SERVER) {
                    LOG.info("Server removed! Disconnecting.");
                    disconnect();
                    setNodeName(null);
                }
            }

        });
    }

    @Override
    protected void start(boolean master) {
        if (master) {
            final NodeInfo serverInfo = getCluster().getMaster(Comm.SERVER);
            if (serverInfo != null) {
                LOG.info("Came online and server found. Connecting.");
                reconnect(serverInfo.getName());
            }
        }
    }

    @Override
    public void switchToMaster() {
        super.switchToMaster();

        reconnect(getCluster().getMaster(Comm.SERVER).getName());
    }

    @Override
    public void setReceiver(MessageReceiver receiver) {
        assertDuringInitialization();
        this.receiver = receiver;
    }

    @Override
    protected void receive(ChannelHandlerContext ctx, Message message) {
        receiver.receive(message);
    }

}
