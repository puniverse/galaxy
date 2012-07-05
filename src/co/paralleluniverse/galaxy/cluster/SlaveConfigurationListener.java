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
 * A listener for events relating to this master-slaves node group configuration.
 */
public interface SlaveConfigurationListener {
    /**
     * Invoked when this (slave) node has got a new master.
     * @param node The new master node.
     */
    void newMaster(NodeInfo node);

    /**
     * Invoked when a new slave was added to this (master) node's group.
     * @param node The new slave.
     */
    void slaveAdded(NodeInfo node);

    /**
     * Invoked when a slave has left this (master) node's group (it's gone offline).
     * @param node The offline slave.
     */
    void slaveRemoved(NodeInfo node);
}
