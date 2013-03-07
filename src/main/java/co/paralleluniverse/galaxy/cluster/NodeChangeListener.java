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
 * A a listener for events relating nodes joining and leaving the cluster.
 */
public interface NodeChangeListener {
    /**
     * Invoked when a new node (group) has joined the cluster.
     * @param id The node (group) ID.
     */
    void nodeAdded(short id);

    /**
     * Invoked when a node group's master has changed.
     * @param id The node (group) ID.
     */
    void nodeSwitched(short id);

    /**
     * Invoked when a node (group) has left the cluster.
     * @param id The node (group) ID.
     */
    void nodeRemoved(short id);
}
