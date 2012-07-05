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

import java.util.Collection;

/**
 * Provides information about a cluster node.
 */
public interface NodeInfo {
    /**
     * Returns the node's cluster-wide unique name.
     * @return The node's name.
     */
    String getName();

    /**
     * Returns the node's ID. ID's are a master and all its slave nodes share the same ID.
     * @return The node's ID.
     */
    short getNodeId();

    /**
     * Returns a given property of the node.
     * @param property The name of the property whose value we want.
     * @return The property's value.
     */
    Object get(String property);

    /**
     * Returns a collection of all of this node's property names.
     * @return A collection of all of this node's property names.
     */
    Collection<String> getProperties();
}
