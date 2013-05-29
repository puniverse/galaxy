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
        int size=0;
        for (ByteBuffer byteBuffer : toByteBuffers) {
            size+=byteBuffer.remaining();
        }
        LOG.info("encoding size "+size+ " "+packet);
        return ChannelBuffers.wrappedBuffer(toByteBuffers);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        final ChannelBuffer buffer = (ChannelBuffer) msg;
        final MessagePacket packet = new MessagePacket();
        final ByteBuffer toByteBuffer = buffer.toByteBuffer();
        LOG.info("decoding size "+toByteBuffer.remaining());
        packet.fromByteBuffer(toByteBuffer);
        return packet;
    }
}
