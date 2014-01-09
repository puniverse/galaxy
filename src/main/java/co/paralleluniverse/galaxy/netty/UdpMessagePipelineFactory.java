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

import java.util.concurrent.Executor;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.slf4j.Logger;

/**
 *
 * @author pron
 */
class UdpMessagePipelineFactory implements ChannelPipelineFactory {
    private final Logger logger;
    private final ChannelMessageNodeResolver nodeResolver;
    private final Executor executor;

    public UdpMessagePipelineFactory(Logger logger, ChannelNodeAddressResolver nodeResolver, Executor executor) {
        this.logger = logger;
        this.nodeResolver = nodeResolver;
        this.executor = executor;
    }
    
    @Override
    public ChannelPipeline getPipeline() throws Exception {
        final ChannelPipeline pipeline = Channels.pipeline();
        if(executor != null)
            pipeline.addLast("executor", new ExecutionHandler(executor));
        pipeline.addLast("logging", new LoggingHandler(logger));
        // a node resolver must be added before the mesage codec
        pipeline.addLast("messageCodec", new MessagePacketCodec());
        pipeline.addLast("nodeResolver", nodeResolver);
        
        return pipeline;
    }
}
