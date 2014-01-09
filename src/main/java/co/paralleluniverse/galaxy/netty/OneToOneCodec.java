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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.DownstreamMessageEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.UpstreamMessageEvent;

/**
 *
 * @see org.jboss.netty.handler.codec.oneone.OneToOneEncoder
 * @see org.jboss.netty.handler.codec.oneone.OneToOneDecoder
 * @author pron
 */
public abstract class OneToOneCodec implements ChannelDownstreamHandler, ChannelUpstreamHandler {
    // Code copied from org.jboss.netty.handler.codec.oneone.OneToOneEncoder and org.jboss.netty.handler.codec.oneone.OneToOneDecoder

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendDownstream(evt);
            return;
        }

        final MessageEvent e = (MessageEvent) evt;
        final Object originalMessage = e.getMessage();
        final Object encodedMessage = encode(ctx, e.getChannel(), originalMessage);
        if (originalMessage == encodedMessage)
            ctx.sendDownstream(evt);
        else if (encodedMessage != null)
            ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel(), e.getFuture(), encodedMessage, e.getRemoteAddress())); // Channels.write(ctx, e.getFuture(), encodedMessage, e.getRemoteAddress());
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent evt) throws Exception {
        if (!(evt instanceof MessageEvent)) {
            ctx.sendUpstream(evt);
            return;
        }

        final MessageEvent e = (MessageEvent) evt;
        final Object originalMessage = e.getMessage();
        final Object decodedMessage = decode(ctx, e.getChannel(), originalMessage);
        if (originalMessage == decodedMessage)
            ctx.sendUpstream(evt);
        else if (decodedMessage != null)
            ctx.sendUpstream(new UpstreamMessageEvent(ctx.getChannel(), decodedMessage, e.getRemoteAddress())); // Channels.fireMessageReceived(ctx, decodedMessage, e.getRemoteAddress());
    }

    /**
     * Transforms the specified message into another message and return the
     * transformed message.  Note that you can not return {@code null}, unlike
     * you can in {@link OneToOneDecoder#decode(ChannelHandlerContext, Channel, Object)};
     * you must return something, at least {@link ChannelBuffers#EMPTY_BUFFER}.
     */
    protected abstract Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception;

    /**
     * Transforms the specified received message into another message and return
     * the transformed message.  Return {@code null} if the received message
     * is supposed to be discarded.
     */
    protected abstract Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception;
}
