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

import co.paralleluniverse.galaxy.cluster.NodeAddressResolver;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class MasterNodesFilteringHandler extends IpFilteringHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MasterNodesFilteringHandler.class);
    private final NodeAddressResolver<SocketAddress> addressHandler;

    public MasterNodesFilteringHandler(NodeAddressResolver<SocketAddress> addressHandler) {
        this.addressHandler = addressHandler;
    }
    
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception {
        final boolean res = addressHandler.getNodeId(inetSocketAddress) >= 0;
        if(!res)
            LOG.warn("Rejecting connection from {} - unknown node.", ctx.getChannel());
        return res;
    }
}
