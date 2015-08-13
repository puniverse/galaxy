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
package co.paralleluniverse.galaxy.cluster;

import java.util.List;

/**
 * Provides access to a distributed, observable filesystem-like node tree for configuration data.
 * <p>
 * Nodes in the tree are organized in a directory structure: each tree-node (not to be confused with <i>cluster nodes</i>, or machines) may have sub-nodes (children) as well as byte data associated
 * with it (the node's <i>contents</i>). The nodes are named in the following manner: the root of the tree is called "/", and generations are separated by the '/' (forward-slash) character. A node may
 * have the <i>simple name</i> "mynode" and its <i>full path</i> may be "/grandparent/parent/mynode". All full paths must begin with a "/".<br>
 *
 * The tree is available to all nodes (machines) in the cluster, and any change is immediately visible to all of them. The tree provides an important <i>ordering guarantee</i>: All children (of a
 * certain node) appear (throughout the cluster) <b>in the order in which they've been created</b>.
 * <p>
 * The tree also provides <i>ephemeral</i> nodes: those are automatically deleted when the creating cluster-node (machine) goes offline, i.e. disconnected from the cluster for whatever reason -
 * intentional or due to some fault.
 */
public interface DistributedTree {
    /**
     * Creates a new node in the tree. All nonexistent parent nodes are also created (i.e. all nonexistent nodes along the path).
     * <p>
     * The node can be <i>ephemeral</i> in which case, it and all its descendents will be deleted automatically when the creating cluster-node (this machine) goes offline, i.e. disconnected from the
     * cluster for whatever reason - intentional or due to some fault. Non-ephemeral (<i>permanent</i>) nodes persist in the tree until the entire cluster is taken down.<br>
     *
     * If the node is marked as ephemeral, all <b>nonexistent</b> ancestors that will be created in the process will be ephemeral as well.
     *
     * @param node The full path of the node to create.
     * @param ephemeral {@code true} if the node is to be <i>ephemeral</i>; {@code false} if it's to be <i>permanent</i>.
     */
    void create(String node, boolean ephemeral);

    /**
     * Creates an ephemeral node that, when returned by {@link #getChildren(java.lang.String) getChildren()}, will be placed in the list in relation to other ordered ephemeral nodes by the relative order of its creation. 
     * The node's ancestors are not created.
     * 
     * @param node The full path of the node to create.
     */
    void createEphemeralOrdered(String node);
    
    /**
     * Returns all child-nodes (direct descendents) of a given node, with ephemeral ordered nodes returned <b>in the order they were created</b>.
     * @param node The full path of the node.
     * @return A list of the simple names (without the full path) of all of the given node's children.
     */
    List<String> getChildren(String node);
    
    /**
     * Returns {@code true} if the given node exists in the tree.
     * @param node The full path of the node to test.
     * @return  {@code true} if the given node exists in the tree; {@code false} otherwise.
     */
    boolean exists(String node);

    /**
     * Associates data with (sets the contents of) a given node.
     * @param node The full path name of the node.
     * @param data The data to be associated with the node.
     * @see #get(java.lang.String)
     */
    void set(String node, byte[] data);

    /**
     * Returns the contents of a given node.
     * @param node The full path of the node.
     * @return The given node's data contents.
     * @see #set(java.lang.String, byte[])
     */
    byte[] get(String node);

    /**
     * Deletes a node and all its descendents from the tree.
     * @param node The full path of the node to be removed.
     */
    void delete(String node);

    /**
     * Ensures that all updates to the tree done by this cluster node will have been seen by all nodes in the cluster when this method returns.
     */
    void flush();
    
    /**
     * Adds a {@link Listener listener} listening for modifications on the given node.
     * As soon as the listener is added, {@link Listener#nodeAdded(String) nodeAdded} is called for each child of {@code node}.
     * @param node The full path of the node to observe.
     * @param listener The listener.
     */
    void addListener(String node, Listener listener);

    /**
     * Removes a listener.
     * @param node The full path of the node the listener is currently observing.
     * @param listener The listener to remove.
     */
    void removeListener(String node, Listener listener);

    /**
     * Prints the tree's structure, starting at a given node, to the given stream.
     * @param node The node that will serve as the root of the dump.
     * @param out The output stream to which the tree structure is to be sent.
     */
    void print(String node, java.io.PrintStream out);

    /**
     * Method is called when Galaxy Cluster is going offline and requests all services to shutdown.
     */
    void shutdown();

    /**
     * A listener for DistributedTree node events.
     */
    public static interface Listener {
        /**
         * Invoked when a new node has been added to the tree.
         * @param node The new node's full path.
         */
        void nodeAdded(String node);

        /**
         * Invoked when a node's contents has been modified.
         * @param node The updated node's full path.
         */
        void nodeUpdated(String node);

        /**
         * Invoked when a node has been deleted from the tree.
         * @param node The deleted node's full path.
         */
        void nodeDeleted(String node);

        /**
         * Invoked when a new child node of the listener's target node has been created.
         * {@link #nodeAdded(java.lang.String) nodeAdded} will be called on a listener listening on the child node, if one exists.
         * @param node The full path of the parent node.
         * @param child The simple name (without the full path) of the newly added child node.
         */
        void nodeChildAdded(String node, String child);

        /**
         * Invoked when a child node's contents of the listener's target node has been modified.
         * {@link #nodeUpdated(java.lang.String) nodeUpdated} will be called on a listener listening on the child node, if one exists.
         * @param node The full path of the parent node.
         * @param child The simple name (without the full path) of the updated node.
         */
        void nodeChildUpdated(String node, String child);

        /**
         * Invoked when a child node of the listener's target node has been deleted.
         * {@link #nodeDeleted(java.lang.String) nodeDeleted} will be called on a listener listening on the child node, if one exists.
         * @param node The full path of the parent node.
         * @param child The simple name (without the full path) of the deleted child node.
         */
        void nodeChildDeleted(String node, String child);
    }

    /**
     * An abstract adapter class for receiving DistributedTree events. The methods in this class are empty. This class exists as convenience for creating listener objects.
     */
    public static abstract class ListenerAdapter implements Listener {
        @Override
        public void nodeAdded(String node) {
        }

        @Override
        public void nodeUpdated(String node) {
        }

        @Override
        public void nodeDeleted(String node) {
        }

        @Override
        public void nodeChildAdded(String node, String childName) {
        }

        @Override
        public void nodeChildUpdated(String node, String childName) {
        }

        @Override
        public void nodeChildDeleted(String node, String childName) {
        }
    }
}
