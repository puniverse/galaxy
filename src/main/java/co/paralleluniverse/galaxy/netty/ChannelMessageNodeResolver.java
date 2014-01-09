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

import co.paralleluniverse.galaxy.core.Message;
import java.net.SocketAddress;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 *
 * @author pron
 */
@ChannelHandler.Sharable
public abstract class ChannelMessageNodeResolver extends SimpleChannelUpstreamHandler {
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        final short nodeId = getNodeId(ctx, e.getRemoteAddress());
        if (e.getMessage() instanceof Message)
            ((Message) e.getMessage()).setNode(nodeId);
        else if (e.getMessage() instanceof MessagePacket)
            ((MessagePacket) e.getMessage()).setNode(nodeId);
        ctx.sendUpstream(e);
    }

    protected abstract short getNodeId(ChannelHandlerContext ctx, SocketAddress address);
}
