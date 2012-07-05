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
