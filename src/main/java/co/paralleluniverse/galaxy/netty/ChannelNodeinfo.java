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

import co.paralleluniverse.galaxy.cluster.NodeInfo;
import org.jboss.netty.channel.ChannelLocal;

/**
 *
 * @author pron
 */
class ChannelNodeInfo {
    // TODO - remove this class and use channel attachment when Netty 4 arrives.
    public static final ChannelLocal<NodeInfo> nodeInfo = new ChannelLocal<NodeInfo>();
}
