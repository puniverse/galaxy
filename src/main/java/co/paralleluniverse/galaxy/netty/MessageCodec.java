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
import java.nio.ByteBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 *
 * @author pron
 */
@ChannelHandler.Sharable
public class MessageCodec extends OneToOneCodec {
    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        final Message message = (Message) msg;
        final ByteBuffer[] buffers = message.toByteBuffers();
        return ChannelBuffers.wrappedBuffer(buffers);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        final ChannelBuffer buffer = (ChannelBuffer) msg;
        final Message message = Message.fromByteBuffer(buffer.toByteBuffer());
        return message;
    }
}
