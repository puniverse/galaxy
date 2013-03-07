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
package co.paralleluniverse.galaxy.monitoring;

import java.util.List;
import java.util.SortedMap;

/**
 *
 * @author pron
 */
public interface ClusterMXBean {
    SortedMap<String, String> getMyNodeInfo();
    
    boolean isOnline();

    boolean isMaster();

    void shutdown();
    
    boolean hasServer();

    List<SortedMap<String, String>> getNodes();
    
    SortedMap<String, String> getMyMaster();
    
    List<SortedMap<String, String>> getMySlaves();
}
