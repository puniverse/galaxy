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
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 *
 * @author pron
 */
@ChannelHandler.Sharable
public class ChannelNodeAddressResolver extends ChannelMessageNodeResolver {
    private final NodeAddressResolver<InetSocketAddress> addressResolver;

    public ChannelNodeAddressResolver(NodeAddressResolver<InetSocketAddress> addressResolver) {
        this.addressResolver = addressResolver;
    }

    @Override
    protected short getNodeId(ChannelHandlerContext ctx, SocketAddress address) {
        Short node = null;
        try {
            node = addressResolver.getNodeId((InetSocketAddress) address);
        } catch (Exception e) {
        }
        if (node == null)
            throw new RuntimeException("Node not found for address " + address);
        return node;
    }
}
