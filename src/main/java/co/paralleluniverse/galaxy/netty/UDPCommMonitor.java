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
