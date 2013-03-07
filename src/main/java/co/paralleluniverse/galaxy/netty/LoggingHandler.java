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

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;

/**
 *
 * @author pron
 */
public class LoggingHandler extends SimpleChannelUpstreamHandler {

    private final Logger logger;

    public LoggingHandler(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.info("Channel {} closed.", e.getChannel());
        super.channelClosed(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.info("Channel {} connected to {}", e.getChannel(), e.getChannel().getRemoteAddress());
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.info("Channel {} disconnected from {}", e.getChannel(), e.getChannel().getRemoteAddress());
        super.channelDisconnected(ctx, e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.warn("Exception caught in channel {}: {} {}", new Object[]{e.getChannel(), e.getCause().getClass().getName(), e.getCause().getMessage()});
        logger.debug("Exception caught in channel", e.getCause());
        super.exceptionCaught(ctx, e);
    }
    
//    @Override
//    public void channelBound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
//        logger.info("Channel {} bound to port {}", e.getChannel(), e.getValue());
//        super.channelBound(ctx, e);
//    }
//
//    @Override
//    public void channelUnbound(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
//        logger.info("Channel {} unbound", e.getChannel());
//        super.channelUnbound(ctx, e);
//    }
//
//    @Override
//    public void childChannelClosed(ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
//        logger.info("Child channel {} closed", e.getChildChannel());
//        super.childChannelClosed(ctx, e);
//    }
//
//    @Override
//    public void childChannelOpen(ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
//        logger.info("Child channel {} opened", e.getChildChannel());
//        super.childChannelOpen(ctx, e);
//    }
}
