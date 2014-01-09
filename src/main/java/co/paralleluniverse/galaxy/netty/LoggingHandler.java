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
        logger.warn("Exception caught in channel " + e.getChannel() + ": " + " " + e.getCause().getClass().getName() + " " + e.getCause().getMessage(), e.getCause());
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
