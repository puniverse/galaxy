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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.MergeView;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;
import org.jgroups.protocols.SEQUENCER;
import org.jgroups.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tree-like structure that is replicated across several members. Updates will be multicast to all group members reliably and in the same order.
 *
 * @author Ron Pressler
 * @author Bela Ban Jan 17 2002
 * @author Alfonso Olias-Sanz
 */
public class ReplicatedTree {
    public static final char SEPARATOR = '/';
    public static final String SSEPARATOR = Character.toString(SEPARATOR);
    private static final int INDENT = 4;
    private static final Logger LOG = LoggerFactory.getLogger(ReplicatedTree.class);
    private final Channel channel;
    private final Node root = new Node("", SSEPARATOR, null, null, null);
    private final List<Address> members = new ArrayList<Address>();
    private final ConflictResolver conflictResolver;
    private final long getStateTimeout;
    private Address otherAddress; // used for view merges. we employ the fact that all callbacks are on the same thread.
    private boolean running = true;
    private final Multimap<String, ReplicatedTreeListener> pendingListeners = Multimaps.synchronizedMultimap((Multimap) HashMultimap.create());
    private final Object lock = new Object();
    private final Object updateCondition = new Object();

    public interface ReplicatedTreeListener {
        void nodeAdded(String fqn);

        void nodeRemoved(String fqn);

        void nodeUpdated(String fqn);

        void nodeChildAdded(String parentFqn, String childName);

        void nodeChildRemoved(String parentFqn, String childName);

        void nodeChildUpdated(String parentFqn, String childName);
    }

    public interface ConflictResolver {
        byte[] resolve(String node, byte[] current, byte[] other, Address otherAddress);
    }

    public ReplicatedTree(Channel channel, ConflictResolver conflictResolver, long getStateTimeout) throws Exception {
        if (!channel.hasProtocol(SEQUENCER.class))
            throw new RuntimeException("Channel must have SEQUENCER protocol to ensure total ordering needed for replicated tree");
        this.channel = channel;
        this.conflictResolver = conflictResolver;
        this.getStateTimeout = getStateTimeout;
        channel.setReceiver(myReceiver);
    }

    public ReplicatedTree(JChannel channel, ConflictResolver conflictResolver, long getStateTimeout) throws Exception {
        this(new JChannelAdapter(channel), conflictResolver, getStateTimeout);
    }

    public void start() throws Exception {
        channel.getState(null, getStateTimeout, true);
    }

    public Channel getChannel() {
        return channel;
    }

    public void addListener(String node, ReplicatedTreeListener listener) {
        final Node n = findNode(node);
        if (n == null)
            pendingListeners.put(node, listener);
        else
            n.addListener(listener);
    }

    public void removeListener(String node, ReplicatedTreeListener listener) {
        final Node n = findNode(node);
        if (n == null)
            pendingListeners.remove(node, listener);
        else
            n.removeListener(listener);
    }

    /**
     * Adds a new node to the tree. If the node does not exist yet, it will be created. Also, parent nodes will be created if non-existent. If the node already exists, this is a no-op, it's status as
     * ephemeral may change.
     *
     * @param fqn The fully qualified name of the new node
     * @param data The new data. May be null if no data should be set in the node.
     */
    public void create(String fqn, boolean ephemeral) {
        awaitRunning();
        try {
            LOG.trace("Creating {} {}", fqn, ephemeral ? "(ephemeral)" : "");
            channel.send(new Message(null, new Request(Request.CREATE, fqn, ephemeral)));
            synchronized (updateCondition) {
                while (!exists(fqn))
                    updateCondition.wait();
            }
        } catch (Exception ex) {
            LOG.error("failure bcasting PUT request", ex);
        }
    }

    public void set(String fqn, byte[] data) {
        awaitRunning();
        try {
            channel.send(new Message(null, new Request(Request.SET, fqn, data)));
        } catch (Exception ex) {
            LOG.error("failure bcasting PUT request", ex);
        }
    }

    /**
     * Removes the node from the tree.
     *
     * @param fqn The fully qualified name of the node.
     */
    public void remove(String fqn) {
        awaitRunning();
        try {
            channel.send(new Message(null, new Request(Request.REMOVE, fqn)));
        } catch (Exception ex) {
            LOG.error("failure bcasting REMOVE request", ex);
        }
    }

    public void remove(String parentFqn, String childName) {
        remove(parentFqn + SEPARATOR + childName);
    }

    public void flush() {
        awaitRunning();
        try {
            Message msg = new Message(null, new byte[0]);
            msg.setFlag(Message.RSVP);
            channel.send(msg);
        } catch (Exception ex) {
            LOG.error("failure bcasting FLUSH request", ex);
        }
    }

    /**
     * Checks whether a given node exists in the tree
     *
     * @param fqn The fully qualified name of the node
     * @return
     * <code>true</code> if the node exists,
     * <code>false</code> otherwise.
     */
    public boolean exists(String fqn) {
        if (fqn == null)
            return false;
        return findNode(fqn) != null;
    }

    /**
     * Finds a node given its name and returns the data associated with it. Returns null if the node was not found in the tree or the data is null.
     *
     * @param fqn The fully qualified name of the node.
     * @return The data associated with the node, or
     * <code>null</code> if none or node is nonexistent.
     */
    public byte[] get(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        final byte[] buffer = n.getData();
        if (buffer == null)
            return null;
        return Arrays.copyOf(buffer, buffer.length);
    }

    /**
     * Returns a String representation of the node defined by
     * <code>fqn</code>. Output includes name, fqn and data.
     */
    public String print(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        return n.toString();
    }

    /**
     * Returns all children of a given node.
     *
     * @param fqn The fully qualified name of the node
     * @return A list of child names (as Strings)
     */
    public List<String> getChildren(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        final Set<String> names = n.getChildrenNames();
        return new ArrayList<String>(names);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        root.print(sb, 0);
        return sb.toString();
    }

    public String toString(String fqn) {
        final Node n = findNode(fqn);
        if (n == null)
            return null;
        final StringBuilder sb = new StringBuilder();
        n.print(sb, 0);
        return sb.toString();
    }
    private final Receiver myReceiver = new ReceiverAdapter() {
        @Override
        public void receive(Message msg) {
            if (msg == null || msg.getLength() == 0)
                return;
            try {
                final Request req = (Request) msg.getObject();
                final String fqn = req.fqn;
                switch (req.type) {
                    case Request.CREATE:
                        _create(fqn, req.ephemeral ? msg.getSrc() : null);
                        break;
                    case Request.SET:
                        _set(fqn, req.data);
                        break;
                    case Request.REMOVE:
                        _remove(fqn);
                        break;
                    default:
                        LOG.error("type {} unknown", req.type);
                        break;
                }
            } catch (Exception ex) {
                LOG.error("failed unmarshalling request", ex);
            } finally {
                synchronized (updateCondition) {
                    updateCondition.notifyAll();
                }
            }
        }

        @Override
        public void getState(OutputStream ostream) throws Exception {
            synchronized (root) {
                LOG.info("State requested");
                // all modifications to the tree
                Util.objectToStream(root, new DataOutputStream(ostream));
            }
        }

        @Override
        public void setState(InputStream istream) throws Exception {
            synchronized (root) {
                LOG.info("State received");
                final Node _root = (Node) Util.objectFromStream(new DataInputStream(istream));
                merge(root, _root);
            }
        }

        @Override
        public void viewAccepted(View newView) {
            LOG.info("New view accepted: {}", newView);
            if (newView instanceof MergeView) {
                LOG.info("Merge view");
                final MergeView mergeView = (MergeView) newView;
                final List<View> subgroups = mergeView.getSubgroups();
                for (View subgroup : subgroups) {
                    if (!subgroup.containsMember(channel.getAddress())) { // not my group
                        try {
                            otherAddress = subgroup.getMembers().get(0);
                            LOG.info("Merging state with {}", otherAddress);
                            channel.getState(otherAddress, getStateTimeout, false);
                        } catch (Exception ex) {
                            LOG.error("Exception while getting state", ex);
                        } finally {
                            otherAddress = null;
                        }
                    }
                }
                LOG.info("Done merging state.");
            }

            final List<Address> currentMembers = newView.getMembers();
            final Set<Address> dead = new HashSet<Address>(members);
            dead.removeAll(currentMembers);
            members.clear();
            members.addAll(currentMembers);

            LOG.info("Dead members: {}", dead);
            removeDeadEphemerals(root, dead);
        }

        @Override
        public void block() {
            setRunning(false);
        }

        @Override
        public void unblock() {
            setRunning(true);
        }
    };

    private void _create(String fqn, Address ephemeral) {
        if (fqn == null)
            return;
        LOG.debug("Adding node {}", fqn);
        findNode(fqn, true, ephemeral); // create all nodes if they don't exist
    }

    private void _set(String fqn, byte[] data) {
        if (fqn == null)
            return;
        final Node n = findNode(fqn); // create all nodes if they don't exist
        if (n != null && ((n.getData() == null && data != null) || !Arrays.equals(n.getData(), data))) {
            LOG.debug("Modifying data for node {}", fqn);
            n.setData(data);
        } else
            LOG.warn("Attempted to modify nonexistent node {}", fqn);
    }

    private void _remove(String fqn) {
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

        LOG.debug("Removing node {}", fqn);
        parent.removeChild(child(fqn));
    }

    private Node findNode(String fqn, boolean create, Address ephemeral) {
        if (fqn == null || fqn.equals(SSEPARATOR) || "".equals(fqn))
            return root;

        final Scanner scanner = new Scanner(fqn).useDelimiter(SSEPARATOR);
        Node curr = root;
        while (scanner.hasNext()) {
            final String name = scanner.next();
            Node node = curr.getChild(name);
            if (create) {
                if (node == null) {
                    LOG.debug("Creating node {}", curr.fqn + (!curr.fqn.equals(SSEPARATOR) ? SEPARATOR : "") + name);
                    node = curr.createChild(name, ephemeral, null, pendingListeners);
                } else {
                    if (node.getEphemeralAddress() != null) {
                        if (ephemeral == null) {
                            LOG.debug("Making node {} non-ephemeral.", node.fqn);
                            node.setNotEphemeral();
                        } else if (!ephemeral.equals(node.getEphemeralAddress())) {
                            LOG.info("Node {} ephemeral conflict {} vs {} - making non-ephemeral.", new Object[]{node.fqn, node.getEphemeralAddress(), ephemeral});
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
        return findNode(fqn, false, null);
    }

    private void removeDeadEphemerals(Node node, Set<Address> dead) {
        List<Node> removedChildren = new ArrayList<Node>();
        List<Node> nonRemovedChildren = new ArrayList<Node>();
        synchronized (node) {
            for (Iterator<Node> it = node.getChildren().values().iterator(); it.hasNext();) {
                final Node child = it.next();
                if (child.getEphemeralAddress() != null && dead.contains(child.getEphemeralAddress())) {
                    LOG.debug("Removing ephemeral node {}, owned by dead member {}", child.fqn, child.getEphemeralAddress());
                    it.remove();
                    removedChildren.add(child);
                } else
                    nonRemovedChildren.add(child);
            }
        }
        for (Node child : removedChildren)
            child.notifyNodeRemoved();
        for (Node child : nonRemovedChildren)
            removeDeadEphemerals(child, dead);
    }

    private void merge(Node node, Node other) {
        assert node.fqn.equals(other.fqn);
        if ((node.data == null && other.getData() != null) || (node.data != null && !Arrays.equals(node.data, other.getData()))) {
            byte[] newData = null;
            if (otherAddress == null)
                newData = other.getData();
            else {
                LOG.info("Detected conflict for node {}", node.fqn);
                if (conflictResolver != null)
                    newData = conflictResolver.resolve(node.fqn, node.getData(), other.getData(), otherAddress);
            }
            if ((node.getData() == null && newData != null) || !Arrays.equals(node.data, newData)) {
                LOG.debug("Modifying data for node {}", node.fqn);
                node.setData(newData);
            }
        }
        for (Node otherChild : other.getChildren().values()) {
            Node child = node.getChild(otherChild.name);
            if (child == null) {
                LOG.debug("Adding node {}", otherChild.name);
                child = node.createChild(otherChild.name, otherChild.getEphemeralAddress(), otherChild.getData(), pendingListeners);
            }
            merge(child, otherChild);
        }
    }

    public static String parent(String fqn) {
        final int index = fqn.lastIndexOf(SEPARATOR);
        if (index < 0)
            return null;
        return fqn.substring(0, index);
    }

    public static String child(String fqn) {
        final int index = fqn.lastIndexOf(SEPARATOR);
        if (index < 0 || index == fqn.length() - 1)
            return null;
        return fqn.substring(index + 1, fqn.length());
    }

    private void setRunning(boolean value) {
        synchronized (lock) {
            running = value;
            if (running)
                lock.notifyAll();
        }
    }

    private void awaitRunning() {
        try {
            synchronized (lock) {
                while (!running)
                    lock.wait();
            }
        } catch (InterruptedException e) {
        }
    }

    private static class Node implements Serializable {
        private static final long serialVersionUID = -3077676554440038890L;
        final String name;     // relative name (e.g. "Security")
        final String fqn;      // fully qualified name (e.g. "/federations/fed1/servers/Security")
        final Node parent;
        private transient Address ephemeral;
        private byte[] data;
        private Map<String, Node> children;
        private transient volatile List<ReplicatedTreeListener> listeners = null;

        Node(String childName, String fqn, Node parent, Address ephemeral, byte[] data) {
            this.name = childName;
            this.fqn = fqn;
            this.parent = parent;
            this.ephemeral = ephemeral;
            this.data = data != null ? Arrays.copyOf(data, data.length) : null;
        }

        Address getEphemeralAddress() {
            return ephemeral;
        }

        void setNotEphemeral() {
            ephemeral = null;
        }

        synchronized byte[] getData() {
            return data;
        }

        final void setData(byte[] data) {
            synchronized (this) {
                this.data = data != null ? Arrays.copyOf(data, data.length) : null;
            }
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

        private Node addChild(Node child) {
            synchronized (this) {
                assert children == null || !children.containsKey(child.name);
                assert fqn.equals(parent(child.fqn)) || (fqn.equals("/") && parent(child.fqn).equals(""));
                if (children == null)
                    children = new LinkedHashMap<String, Node>();
                children.put(child.name, child);
            }
            child.notifyNodeAdded();
            return child;
        }

        Node createChild(String childName, Address ephemeral, byte[] data, Multimap<String, ReplicatedTreeListener> pendingListeners) {
            if (childName == null)
                return null;

            final Node child = new Node(childName, (!fqn.equals(SSEPARATOR) ? fqn : "") + SEPARATOR + childName, this, ephemeral, data);
            for (ReplicatedTreeListener listener : pendingListeners.removeAll(child.fqn))
                child.addListener(listener);

            addChild(child);
            return child;
        }

        void removeChild(String childName) {
            Node child = null;
            synchronized (this) {
                if (childName != null && children != null)
                    child = children.remove(childName);
            }
            if (child != null)
                child.notifyNodeRemoved();
        }

        synchronized void removeAll() {
            children = null;
        }

        public void addListener(ReplicatedTreeListener listener) {
            synchronized (this) {
                if (listeners == null)
                    listeners = new CopyOnWriteArrayList<ReplicatedTreeListener>();
            }
            if (!listeners.contains(listener))
                listeners.add(listener);
        }

        public void removeListener(ReplicatedTreeListener listener) {
            synchronized (this) {
                if (listeners == null)
                    return;
            }
            listeners.remove(listener);
        }

        void notifyNodeAdded() {
            List<ReplicatedTreeListener> _listeners = listeners;
            if (_listeners != null) {
                for (ReplicatedTreeListener listener : _listeners) {
                    try {
                        listener.nodeAdded(fqn);
                    } catch (Exception e) {
                        LOG.error("Listener threw an exception.", e);
                    }
                }
            }
            if (parent != null)
                _listeners = parent.listeners;
            if (_listeners != null) {
                for (ReplicatedTreeListener listener : _listeners) {
                    try {
                        listener.nodeChildAdded(parent.fqn, name);
                    } catch (Exception e) {
                        LOG.error("Listener threw an exception.", e);
                    }
                }
            }
        }

        void notifyNodeRemoved() {
            notifyNodeRemoved1();
            List<ReplicatedTreeListener> _listeners = null;
            if (parent != null)
                _listeners = parent.listeners;
            if (_listeners != null) {
                for (ReplicatedTreeListener listener : _listeners) {
                    try {
                        listener.nodeChildRemoved(parent.fqn, name);
                    } catch (Exception e) {
                        LOG.error("Listener threw an exception.", e);
                    }
                }
            }
        }

        private void notifyNodeRemoved1() {
            List<Node> _children = null;
            synchronized (this) {
                if (children != null)
                    _children = new ArrayList<Node>(children.values());
            }
            if (_children != null) {
                for (Node child : _children)
                    child.notifyNodeRemoved1();
            }
            List<ReplicatedTreeListener> _listeners = listeners;
            if (_listeners != null) {
                for (ReplicatedTreeListener listener : _listeners) {
                    try {
                        listener.nodeRemoved(fqn);
                    } catch (Exception e) {
                        LOG.error("Listener threw an exception.", e);
                    }
                }
            }
        }

        void notifyNodeModified() {
            List<ReplicatedTreeListener> _listeners = listeners;
            if (_listeners != null) {
                for (ReplicatedTreeListener listener : listeners) {
                    try {
                        listener.nodeUpdated(fqn);
                    } catch (Exception e) {
                        LOG.error("Listener threw an exception.", e);
                    }
                }
            }
            if (parent != null)
                _listeners = parent.listeners;
            if (_listeners != null) {
                for (ReplicatedTreeListener listener : _listeners) {
                    try {
                        listener.nodeChildUpdated(parent.fqn, name);
                    } catch (Exception e) {
                        LOG.error("Listener threw an exception.", e);
                    }
                }
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

        private void writeObject(ObjectOutputStream s) throws IOException {
            try {
                s.defaultWriteObject();
                s.writeBoolean(ephemeral != null);
                if (ephemeral != null)
                    Util.writeAddress(ephemeral, s);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }

        private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
            try {
                s.defaultReadObject();
                final boolean hasAddress = s.readBoolean();
                if (hasAddress)
                    ephemeral = Util.readAddress(s);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }

    private static class Request implements Serializable {
        static final byte CREATE = 1;
        static final byte SET = 2;
        static final byte REMOVE = 3;
        final byte type;
        final String fqn;
        final byte[] data;
        final boolean ephemeral;
        private static final long serialVersionUID = 7772753222127676782L;

        private Request(byte type, String fqn) {
            this(type, fqn, false, null);
        }

        private Request(byte type, String fqn, boolean ephemeral) {
            this(type, fqn, ephemeral, null);
        }

        private Request(byte type, String fqn, byte[] data) {
            this(type, fqn, false, data);
        }

        private Request(byte type, String fqn, boolean ephemeral, byte[] data) {
            this.type = type;
            this.fqn = fqn;
            this.data = data;
            this.ephemeral = ephemeral;
        }

        @Override
        public String toString() {
            return (type == CREATE ? "CREATE" : type == SET ? "SET" : type == REMOVE ? "REMOVE" : "UNKNOWN") + " (" + ", fqn: " + fqn + ", value: " + Arrays.toString(data) + ')';
        }
    }
}
