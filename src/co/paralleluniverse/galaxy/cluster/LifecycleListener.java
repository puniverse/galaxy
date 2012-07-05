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

/**
 * A listener for lifecycle events.
 */
public interface LifecycleListener {
    /**
     * Invoked when this node has joined the cluster, but before it has gone online.
     */
    void joinedCluster();
    
    /**
     * Invoked when this node is online, i.e. operational and visible to the cluster.
     * @param master {@code true} if this node is a master, {@code false} if it's a slave.
     */
    void online(boolean master);

    /**
     * Invoked when this node is taken offline, i.e. shuts down. An offline node cannot go back online until the JVM has been restarted.
     */
    void offline();

    /**
     * Invoked when this (formerly) slave node has now become the master.
     */
    void switchToMaster();
}
