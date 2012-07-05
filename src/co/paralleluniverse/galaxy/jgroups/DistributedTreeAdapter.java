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
