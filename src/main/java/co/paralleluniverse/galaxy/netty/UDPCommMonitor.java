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

import co.paralleluniverse.common.monitoring.Monitor;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author pron
 */
class UDPCommMonitor extends Monitor<UDPComm> implements UDPCommMXBean {

    public UDPCommMonitor(String name, UDPComm monitored) {
        super("co.paralleluniverse.galaxy.netty:type=UDPComm,name=" + name, monitored);
    }

    @Override
    public int getBroadcastQueueLength() {
        final UDPComm comm = getMonitored();
        if (comm == null)
            return -1;
        return comm.getBroadcastPeer().getQueueLength();
    }

    @Override
    public Map<Short, Integer> getPeerQueuesLengths() {
        final UDPComm comm = getMonitored();
        if (comm == null)
            return null;
        final Map<Short, Integer> lengths = new HashMap<Short, Integer>(comm.getPeers().size());
        for (Map.Entry<Short, UDPComm.NodePeer> entry : comm.getPeers().entrySet())
            lengths.put(entry.getKey(), entry.getValue().getQueueLength());
        return lengths;
    }
}
