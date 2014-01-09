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
import com.google.common.base.Charsets;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.DefaultChannelFuture;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
@ChannelHandler.Sharable
class ChannelNodeNameWriter extends SimpleChannelUpstreamHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ChannelNodeNameWriter.class);
    private final Cluster cluster;

    public ChannelNodeNameWriter(Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        final Channel channel = ctx.getChannel();

        final String name = cluster.getMyNodeInfo().getName();
        LOG.debug("Writing node name, {}, to channel.", name);
        final byte[] array = name.getBytes(Charsets.UTF_8);
        final ChannelBuffer message = ChannelBuffers.wrappedBuffer(array);
        ctx.sendDownstream(new DownstreamMessageEvent(channel, new DefaultChannelFuture(channel, false), message, null));

        super.channelConnected(ctx, e);

        ctx.getPipeline().remove(this);
    }
}
