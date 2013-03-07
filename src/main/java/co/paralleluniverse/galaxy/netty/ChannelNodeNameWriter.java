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
