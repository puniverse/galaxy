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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.galaxy.cluster.DistributedTree;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalTree implements DistributedTree {
    public static final char SEPARATOR = '/';
    public static final String SSEPARATOR = Character.toString(SEPARATOR);
    private static final int INDENT = 4;
    private static final Logger LOG = LoggerFactory.getLogger(LocalTree.class);
    private final Node root = new Node("", SSEPARATOR, null, false, null);
    private final Multimap<String, Listener> pendingListeners = Multimaps.synchronizedMultimap((Multimap) HashMultimap.create());

    public LocalTree() {
    }

    @Override
    public void addListener(String node, Listener listener) {
        final Node n = findNode(node);
        if (n == null)
            pendingListeners.put(node, listener);
        else
            n.addListener(listener);
        List<String> children = getChildren(node);
        if (children==null) return;
        for (String child : children) {
            listener.nodeChildAdded(node, child);
        }
            
    }

    @Override
    public void removeListener(String node, Listener listener) {
        final Node n = findNode(node);
        if (n == null)
            pendingListeners.remove(node, listener);
        else
            n.removeListener(listener);
    }

    @Override
    public void create(String fqn, boolean ephemeral) {
        if (fqn == null)
            return;
        findNode(fqn, true, ephemeral); // create all nodes if they don't exist
    }

    @Override
    public void createEphemeralOrdered(String fqn) {
        create(fqn, true);
    }
    
    @Override
    public void set(String fqn, byte[] data) {
        if (fqn == null)
            return;
        final Node n = findNode(fqn); // create all nodes if they don't exist
        if (n != null && ((n.getData() == null && data != null) || !Arrays.equals(n.getData(), data))) {
            LOG.info("Modifying data for node {}", fqn);
            n.setData(data);
        } else
            LOG.warn("Attempted to modify nonexistent node {}", fqn);
    }

    @Override
    public void delete(String fqn) {
        if (fqn == null)
            return;
        if (fqn.equals(SSEPARATOR)) {
            LOG.info("Clearing tree");
            root.removeAll();
            return;
        }
        final Node parent = findNode(parent(fqn));
        if (parent == null) {
            LOG.warn("Parent {} of node {} not found.", parent(fqn), fqn);
            return;
        }

        LOG.info("Removing node {}", fqn);
        parent.removeChild(child(fqn));
    }

    @Override
    public void flush() {
    }
    
    @Override
    public boolean exists(String fqn) {
        if (fqn == null)
            return false;
        return findNode(fqn) != null;
    }

    @Override
    public byte[] get(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        final byte[] buffer = n.getData();
        if(buffer == null)
            return null;
        return Arrays.copyOf(buffer, buffer.length);
    }

    @Override
    public List<String> getChildren(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        final Set<String> names = n.getChildrenNames();
        return new ArrayList<String>(names);
    }

    @Override
    public void print(String fqn, PrintStream out) {
        final Node n = findNode(fqn);
        if (n == null)
            return;
        StringBuilder sb = new StringBuilder();
        n.print(sb, 0);
        out.println(sb.toString());
    }
    
    public String toString(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        return n.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        root.print(sb, 0);
        return sb.toString();
    }

    private Node findNode(String fqn, boolean create, boolean ephemeral) {
        if (fqn == null || fqn.equals(SSEPARATOR) || "".equals(fqn))
            return root;

        final Scanner scanner = new Scanner(fqn).useDelimiter(SSEPARATOR);
        Node curr = root;
        while (scanner.hasNext()) {
            final String name = scanner.next();
            Node node = curr.getChild(name);
            if (create) {
                if (node == null) {
                    LOG.info("Creating node {}", curr.fqn + (!curr.fqn.equals(SSEPARATOR) ? SEPARATOR : "") + name);
                    node = curr.createChild(name, ephemeral, null);
                } else {
                    if (node.isEphemeral()) {
                        if (!ephemeral) {
                            LOG.info("Making node {} non-ephemeral.", node.fqn);
                            node.setNotEphemeral();
                        }
                    }
                }
            }
            if (node == null)
                return null;
            else
                curr = node;
        }
        return curr;
    }

    private Node findNode(String fqn) {
        return findNode(fqn, false, false);
    }

    public static String parent(String fqn) {
        final int index = fqn.lastIndexOf(SEPARATOR);
        if (index < 0)
            return null;
        final String parent = fqn.substring(0, index);
        return parent;
    }

    public static String child(String fqn) {
        final int index = fqn.lastIndexOf(SEPARATOR);
        if (index < 0 || index == fqn.length() - 1)
            return null;
        final String child = fqn.substring(index + 1, fqn.length());
        return child;
    }

    @Override
    public void shutdown() {
    }

    private class Node {
        final String name;     // relative name (e.g. "Security")
        final String fqn;      // fully qualified name (e.g. "/federations/fed1/servers/Security")
        final Node parent;
        private boolean ephemeral;
        private byte[] data;
        private Map<String, Node> children;
        private transient List<Listener> listeners = null;

        Node(String childName, String fqn, Node parent, boolean ephemeral, byte[] data) {
            this.name = childName;
            this.fqn = fqn;
            this.parent = parent;
            this.ephemeral = ephemeral;
            this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        }

        boolean isEphemeral() {
            return ephemeral;
        }

        void setNotEphemeral() {
            ephemeral = false;
        }

        synchronized byte[] getData() {
            return data;
        }

        final synchronized void setData(byte[] data) {
            this.data = data != null ? Arrays.copyOf(data, data.length) : null;
            notifyNodeModified();
        }

        synchronized Map<String, Node> getChildren() {
            return children != null ? children : (Map<String, Node>) Collections.EMPTY_MAP;
        }

        synchronized Set<String> getChildrenNames() {
            return children != null ? Collections.unmodifiableSet(children.keySet()) : (Set<String>) Collections.EMPTY_SET;
        }

        synchronized Node getChild(String childName) {
            return childName == null ? null : children == null ? null : children.get(childName);
        }

        synchronized boolean hasChild(String childName) {
            return childName != null && children != null && children.containsKey(childName);
        }

        private synchronized Node addChild(Node child) {
            assert fqn.equals(parent(child.fqn)) || (fqn.equals("/") && parent(child.fqn).equals(""));
            if (children == null)
                children = new LinkedHashMap<String, Node>();
            children.put(child.name, child);
            child.notifyNodeAdded();
            return child;
        }

        synchronized Node createChild(String childName, boolean ephemeral, byte[] data) {
            if (childName == null)
                return null;
            assert children == null || !children.containsKey(childName);

            final Node child = new Node(childName, (!fqn.equals(SSEPARATOR) ? fqn : "") + SEPARATOR + childName, this, ephemeral, data);
            for (Listener listener : pendingListeners.removeAll(child.fqn))
                child.addListener(listener);

            addChild(child);
            return child;
        }

        synchronized Node removeChild(String childName) {
            if (childName != null && children != null) {
                final Node child = children.remove(childName);
                child.notifyNodeRemoved();
                return child;
            }
            return null;
        }

        synchronized void removeAll() {
            children = null;
        }

        public void addListener(Listener listener) {
            synchronized (this) {
                if (listeners == null)
                    listeners = new CopyOnWriteArrayList<Listener>();
            }
            if (!listeners.contains(listener))
                listeners.add(listener);
        }

        public void removeListener(Listener listener) {
            synchronized (this) {
                if (listeners == null)
                    return;
            }
            listeners.remove(listener);
        }

        void notifyNodeAdded() {
            if (listeners != null) {
                for (Listener listener : listeners)
                    listener.nodeAdded(fqn);
            }
            if (parent != null && parent.listeners != null) {
                for (Listener listener : parent.listeners)
                    listener.nodeChildAdded(parent.fqn, name);
            }
        }

        void notifyNodeRemoved() {
            notifyNodeRemoved1();
            if (parent != null && parent.listeners != null) {
                for (Listener listener : parent.listeners)
                    listener.nodeChildDeleted(parent.fqn, name);
            }
        }

        private void notifyNodeRemoved1() {
            if (children != null) {
                for (Node child : children.values())
                    child.notifyNodeRemoved1();
            }
            if (listeners != null) {
                for (Listener listener : listeners)
                    listener.nodeDeleted(fqn);
            }
        }

        void notifyNodeModified() {
            if (listeners != null && parent.listeners != null) {
                for (Listener listener : listeners)
                    listener.nodeUpdated(fqn);
            }
            if (parent != null && parent.listeners != null) {
                for (Listener listener : parent.listeners)
                    listener.nodeChildUpdated(parent.fqn, name);
            }
        }

        synchronized StringBuilder print(StringBuilder sb, int indent) {
            for (int i = 0; i < indent; i++)
                sb.append(' ');
            sb.append(SEPARATOR).append(name);
            if (children != null) {
                for (Node n : children.values()) {
                    sb.append('\n');
                    n.print(sb, indent + INDENT);
                }
            }
            return sb;
        }

        @Override
        public synchronized String toString() {
            return "Node{" + "name: " + name + ", fqn: " + fqn + ", data: " + Arrays.toString(data) + '}';
        }
    }
}
