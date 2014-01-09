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
package co.paralleluniverse.galaxy.jgroups;

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.AbstractNodeAddressResolver;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import org.jgroups.Address;

/**
 *
 * @author pron
 */
class JGroupsNodeAddressResolver extends AbstractNodeAddressResolver<Address> {

    public JGroupsNodeAddressResolver(Cluster cluster) {
        super(cluster);
        init();
    }
 
    @Override
    protected Address getAddress(NodeInfo node) {
        return (Address)node.get(JGroupsConstants.JGROUPS_ADDRESS);
    }
    
}
