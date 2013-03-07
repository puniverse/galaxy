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
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;

/**
 *
 * @author pron
 */
@ChannelHandler.Sharable
public class ChannelNodeAddressResolver extends ChannelMessageNodeResolver {
    private final NodeAddressResolver<InetSocketAddress> addressResolver;

    public ChannelNodeAddressResolver(NodeAddressResolver<InetSocketAddress> addressResolver) {
        this.addressResolver = addressResolver;
    }

    @Override
    protected short getNodeId(ChannelHandlerContext ctx, SocketAddress address) {
        Short node = null;
        try {
            node = addressResolver.getNodeId((InetSocketAddress) address);
        } catch (Exception e) {
        }
        if (node == null)
            throw new RuntimeException("Node not found for address " + address);
        return node;
    }
}
