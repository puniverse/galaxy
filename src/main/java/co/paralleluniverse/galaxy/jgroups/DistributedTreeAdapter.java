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
package co.paralleluniverse.galaxy.jgroups;

import co.paralleluniverse.galaxy.cluster.DistributedTree;
import co.paralleluniverse.galaxy.jgroups.ReplicatedTree.ReplicatedTreeListener;
import java.io.PrintStream;
import java.util.List;

/**
 *
 * @author pron
 */
class DistributedTreeAdapter implements DistributedTree {
    private final ReplicatedTree tree;

    public DistributedTreeAdapter(ReplicatedTree tree) {
        this.tree = tree;
    }

    @Override
    public void addListener(String node, final Listener listener) {
        tree.addListener(node, new ListenerAdapter(listener));
        List<String> children = tree.getChildren(node);
        if (children == null)
            return;
        for (String child : children) {
            listener.nodeChildAdded(node, child);
        }
    }

    @Override
    public void removeListener(String node, final Listener listener) {
        tree.removeListener(node, new ListenerAdapter(listener));
    }

    @Override
    public void create(String node, boolean ephemeral) {
        tree.create(node, ephemeral);
    }

    @Override
    public void createEphemeralOrdered(String node) {
        tree.create(node, true);
    }
    
    @Override
    public void set(String node, byte[] data) {
        tree.set(node, data);
    }

    @Override
    public void delete(String node) {
        tree.remove(node);
    }

    @Override
    public void flush() {
        tree.flush();
    }

    @Override
    public boolean exists(String node) {
        return tree.exists(node);
    }

    @Override
    public byte[] get(String node) {
        return tree.get(node);
    }

    @Override
    public List<String> getChildren(String node) {
        return tree.getChildren(node);
    }

    @Override
    public void print(String node, PrintStream out) {
        out.println(tree.toString(node));
    }

    @Override
    public void shutdown() {
    }

    private static class ListenerAdapter implements ReplicatedTreeListener {
        private final Listener listener;

        public ListenerAdapter(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void nodeAdded(String fqn) {
            listener.nodeAdded(fqn);
        }

        @Override
        public void nodeRemoved(String fqn) {
            listener.nodeDeleted(fqn);
        }

        @Override
        public void nodeUpdated(String fqn) {
            listener.nodeUpdated(fqn);
        }

        @Override
        public void nodeChildAdded(String parentFqn, String childName) {
            listener.nodeChildAdded(parentFqn, childName);
        }

        @Override
        public void nodeChildRemoved(String parentFqn, String childName) {
            listener.nodeChildDeleted(parentFqn, childName);
        }

        @Override
        public void nodeChildUpdated(String parentFqn, String childName) {
            listener.nodeChildUpdated(parentFqn, childName);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (!(obj instanceof ListenerAdapter))
                return false;
            return this.listener == ((ListenerAdapter) obj).listener;
        }

        @Override
        public int hashCode() {
            return listener.hashCode();
        }
    }
}
