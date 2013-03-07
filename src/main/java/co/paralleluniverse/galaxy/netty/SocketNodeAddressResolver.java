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

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.AbstractNodeAddressResolver;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class SocketNodeAddressResolver extends AbstractNodeAddressResolver<InetSocketAddress> {
    private static final Logger LOG = LoggerFactory.getLogger(SocketNodeAddressResolver.class);
    private final String portProperty;

    public SocketNodeAddressResolver(Cluster cluster, String portProperty) {
        super(cluster);
        this.portProperty = portProperty;
        init();
    }

    @Override
    protected InetSocketAddress getAddress(NodeInfo node) {
        final InetAddress address = (InetAddress) node.get(IpConstants.IP_ADDRESS);
        final Integer port = (Integer) node.get(portProperty);
        if (address == null || port == null) {
            if (address == null)
                LOG.warn("Socket address (property {}) not set for node {}", IpConstants.IP_ADDRESS, node);
            if (port == null)
                LOG.warn("Socket port (property {}) not set for node {}", portProperty, node);
            return null;
        }
        InetSocketAddress socket = new InetSocketAddress(address, port);
        return socket;
    }
}
