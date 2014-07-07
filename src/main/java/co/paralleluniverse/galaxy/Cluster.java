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
package co.paralleluniverse.galaxy;

import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.galaxy.cluster.DistributedTree;
import co.paralleluniverse.galaxy.cluster.LifecycleListener;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.cluster.NodePropertyListener;
import co.paralleluniverse.galaxy.cluster.ReaderWriter;
import co.paralleluniverse.galaxy.cluster.SlaveConfigurationListener;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The cluster management service. Provides information and events on the nodes comprising the grid's cluster. The cluster manages most of the information via a {@link DistributedTree} that's
 * implemented either on top of <a href="http://jgroups.org/">JGroups</a> or <a href="http://zookeeper.apache.org/">Apache ZooKeeper</a>.
 */
public interface Cluster {
    /**
     * Returns this cluster node's ID. The ID is not unique in the cluster - a master and its slaves all share the same ID.
     * @return This cluster node's ID.
     */
    short getMyNodeId();

    /**
     * Returns this cluster node's info. This node's <b>name</b> (accessed through {@link NodeInfo#getName()}) is unique in the cluster.
     * @return This cluster node's info.
     */
    NodeInfo getMyNodeInfo();

    /**
     * Returns a {@link NodeInfo} for any cluster node, identified by its (unique) name.
     * @param nodeName The node's (unique) name.
     * @return The node's information.
     */
    NodeInfo getNodeInfoByName(String nodeName);

    /**
     * Returns a set of {@link NodeInfo} for any cluster node, with a property equal to the given value.
     * @param propertyName The property we want to compare to the given value.
     * @param value The requested value of the property.
     * @return The nodes having the requested property value.
     */
    Set<NodeInfo> getNodesByProperty(String propertyName, Object value);
    
    /**
     * Returns true if the grid is configured to use a server.
     * @return {@code true} if the grid is configured to use a server; {@code false} otherwise.
     */
    boolean hasServer();

    /**
     * Returns {@code true} if this node is online (operational and visible to other nodes).
     * @return {@code true} if this node is online and {@code false} otherwise.
     */
    boolean isOnline();

    /**
     * Returns {@code true} if this node is a master; {@code false} if it's a slave.
     * @return {@code true} if this node is a master; {@code false} if it's a slave.
     */
    boolean isMaster();

    /**
     * Returns the IDs of all online nodes (each master and slaves group of nodes is listed once - remember: masters and slaves share the same ID).
     * @return A set containing the IDs of all online nodes.
     */
    Set<Short> getNodes();

    /**
     * Returns all the current, online master nodes.
     * @return A collection containing all the current, online master nodes.
     */
    Collection<NodeInfo> getMasters();

    /**
     * Returns this node's master node if this is a slave.
     * @return This node's master node if this is a slave and {@code null} otherwise.
     */
    NodeInfo getMyMaster();

    /**
     * Returns this node's master node if this is a slave.
     * @return This node's master node if this is a slave. {@code null} otherwise.
     */
    List<NodeInfo> getMySlaves();

    /**
     * Returns a given node-group master if this node is a slave.
     * @param node The node ID of the mater-slaves group (remember: masters and slaves share the same ID) the master of which we want.
     * @return The master of the given node-group.
     */
    NodeInfo getMaster(short node);

    /**
     * Returns {@code true} if the given node is a master; {@code false} if it's a slave.
     * @param node
     * @return {@code true} if the given node is a master; {@code false} if it's a slave.
     */
    boolean isMaster(NodeInfo node);

    /**
     * Returns the {@link DistributedTree} used by the cluster. You may use the tree for your own purposes. The cluster protects any internal Galaxy data in the tree from accidental deletion or
     * modification.
     * @return The {@link DistributedTree} used by the cluster.
     */
    DistributedTree getDistributedTree();

    /**
     * Adds a listener for lifecycle events.
     * @param listener The listener
     */
    void addLifecycleListener(LifecycleListener listener);

    /**
     * Removes a {@link LifecycleListener}.
     * @param listener The listener.
     */
    void removeLifecycleListener(LifecycleListener listener);

    /**
     * Adds a listener for events relating to this master-slaves node group configuration.
     * @param listener The listener.
     */
    void addSlaveConfigurationListener(SlaveConfigurationListener listener);

    /**
     * Removes a {@link SlaveConfigurationListener}.
     * @param listener The listener.
     */
    void removeSlaveConfigurationListener(SlaveConfigurationListener listener);

    /**
     * Adds a listener for events relating nodes joining and leaving the cluster.
     * @param listener The listener.
     */
    void addNodeChangeListener(NodeChangeListener listener);

    /**
     * Removes a {@link NodeChangeListener}.
     * @param listener The listener.
     */
    void removeNodeChangeListener(NodeChangeListener listener);

    /**
     * Adds a listener that will be notified when a node property has changed on one of the master nodes.
     * @param property The property on whose changes we want to listen.
     * @param listener The listener.
     */
    void addMasterNodePropertyListener(String property, NodePropertyListener listener);

    /**
     * Removes a {@link NodePropertyListener} listening on master nodes.
     * @param property The property on the listener is listening on.
     * @param listener
     */
    void removeMasterNodePropertyListener(String property, NodePropertyListener listener);

    /**
     * Adds a listener that will be notified when a node property has changed on one of <i>this node's</i> slaves.
     * @param property The property on whose changes we want to listen.
     * @param listener The listener.
     */
    void addSlaveNodePropertyListener(String property, NodePropertyListener listener);

    /**
     * Removes a {@link NodePropertyListener} listening on this node's slaves.
     * @param property The property on the listener is listening on.
     * @param listener
     */
    void removeSlaveNodePropertyListener(String property, NodePropertyListener listener);

    /**
     * Adds a new node property that will be visible on the cluster. <p> If {@code required} is true, tells the cluster that each node must have the given property set to a non-null value in order to
     * be online. This would not normally be done by a user process, as it would require that his method be called during the Spring container initialization, i.e. in a {@link Component}'s {@link Component#init() init()}
     * or {@link Component#postInit() postInit()}.
     * <p>
     * @param property The property's name.
     * @param requiredForPeer Whether a non-null value for this property is required for the a peer to be online. For properties added by user processes, this should be {@code false}.
     * @param requiredForServer Whether a non-null value for this property is required for a server node to be online. For properties added by user processes, this should be {@code false}.
     * @param readerWriter A {@link ReaderWriter} instance to read and write this property to and from the distributed tree.
     */
    void addNodeProperty(String property, boolean requiredForPeer, boolean requiredForServer, ReaderWriter<?> readerWriter);

    /**
     * Sets a {@link #addNodeProperty(java.lang.String, boolean, boolean, co.paralleluniverse.galaxy.cluster.ReaderWriter) property} for this node.
     * @param property The property name being set.
     * @param value The new property value.
     */
    void setNodeProperty(String property, Object value);

    /**
     * Returns the main resource used by the cluster for coordination, namely a {@code org.jgroups.JChannel} for a JGroups-based cluster or 
     * a {@code com.netflix.curator.framework.CuratorFramework} for a ZooKeeper-based cluster.
     * @return The main resource used by the cluster for coordination, currently of type {@code org.jgroups.JChannel} or {@code org.apache.zookeeper.ZooKeeper}.
     */
    Object getUnderlyingResource();
    
    /**
     * Leave the cluster and shutdown the JVM.
     * This would take the node offline and result in a call to {@link LifecycleListener#offline()}. This action cannot be reversed - the JVM must be restarted for the node to
     * come back online.
     */
    void goOffline();
}
