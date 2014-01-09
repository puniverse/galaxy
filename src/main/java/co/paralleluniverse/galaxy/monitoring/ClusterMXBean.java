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
