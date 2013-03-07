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

import co.paralleluniverse.galaxy.cluster.NodeAddressResolver;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class MasterNodesFilteringHandler extends IpFilteringHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MasterNodesFilteringHandler.class);
    private final NodeAddressResolver<SocketAddress> addressHandler;

    public MasterNodesFilteringHandler(NodeAddressResolver<SocketAddress> addressHandler) {
        this.addressHandler = addressHandler;
    }
    
    @Override
    protected boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception {
        final boolean res = addressHandler.getNodeId(inetSocketAddress) >= 0;
        if(!res)
            LOG.warn("Rejecting connection from {} - unknown node.", ctx.getChannel());
        return res;
    }
}
