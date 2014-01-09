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
