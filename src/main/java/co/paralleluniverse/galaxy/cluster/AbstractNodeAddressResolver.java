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
package co.paralleluniverse.galaxy.cluster;

import co.paralleluniverse.galaxy.Cluster;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public abstract class AbstractNodeAddressResolver<Address> implements NodeAddressResolver<Address> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNodeAddressResolver.class);
    private final Cluster cluster;
    private final Map<Short, Address> nodeIdToAddress = new ConcurrentHashMap<Short, Address>();
    private final Map<Address, Short> addressToNodeId = new ConcurrentHashMap<Address, Short>();

    public AbstractNodeAddressResolver(final Cluster cluster) {
        this.cluster = cluster;
        cluster.addNodeChangeListener(new NodeChangeListener() {
            @Override
            public void nodeAdded(short id) {
                put(cluster.getMaster(id));
            }

            @Override
            public void nodeSwitched(short id) {
                put(cluster.getMaster(id));
            }

            @Override
            public void nodeRemoved(short id) {
                final Address address = nodeIdToAddress.get(id);
                if (address != null)
                    addressToNodeId.remove(address);
                nodeIdToAddress.remove(id);
            }
        });
    }

    protected void init() {
        for (NodeInfo node : cluster.getMasters())
            put(node);
    }

    private void put(NodeInfo node) {
        final short id = node.getNodeId();
        final Address address = getAddress(cluster.getMaster(id));
        LOG.debug("Node {}, address: {}", id, address);
        if (address != null) {
            nodeIdToAddress.put(id, address);
            addressToNodeId.put(address, id);
        }
    }

    protected abstract Address getAddress(NodeInfo node);

    @Override
    public short getNodeId(Address address) {
        final Short node = addressToNodeId.get(address);
        return node != null ? node : -1;
    }

    @Override
    public Address getNodeAddress(short id) {
        return nodeIdToAddress.get(id);
    }
}
