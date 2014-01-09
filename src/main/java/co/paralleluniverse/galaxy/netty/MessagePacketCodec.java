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

import java.nio.ByteBuffer;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
@ChannelHandler.Sharable
public class MessagePacketCodec extends OneToOneCodec {
    private static final Logger LOG = LoggerFactory.getLogger(MessagePacketCodec.class);

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        final MessagePacket packet = (MessagePacket) msg;
        final ByteBuffer[] toByteBuffers = packet.toByteBuffers();
        if (LOG.isDebugEnabled()) {
            int size=0;
            for (ByteBuffer byteBuffer : toByteBuffers) {
                size+=byteBuffer.remaining();
            }
            LOG.debug("encoding size "+size+ " "+packet);
        }
        return ChannelBuffers.wrappedBuffer(toByteBuffers);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        final ChannelBuffer buffer = (ChannelBuffer) msg;
        final MessagePacket packet = new MessagePacket();
        final ByteBuffer toByteBuffer = buffer.toByteBuffer();
        if (LOG.isDebugEnabled())
            LOG.debug("decoding size "+toByteBuffer.remaining());
        packet.fromByteBuffer(toByteBuffer);
        return packet;
    }
}
