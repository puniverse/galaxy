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
package co.paralleluniverse.galaxy.zookeeper;

import co.paralleluniverse.galaxy.cluster.DistributedTree;
import static co.paralleluniverse.galaxy.cluster.DistributedTreeUtil.child;
import static co.paralleluniverse.galaxy.cluster.DistributedTreeUtil.correctForRoot;
import static co.paralleluniverse.galaxy.cluster.DistributedTreeUtil.parent;
import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class ZooKeeperDistributedTree implements DistributedTree {
    // Contains a hack to allow (one level only of) ephemeral node children.
    // ZooKeeper 3.5.0 is supposed to allow epehemeral node children, so this hack could be removed.
    // See: https://issues.apache.org/jira/browse/ZOOKEEPER-834
    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperDistributedTree.class);
    private final CuratorFramework client;
    private final Map<String, String> namesWithSequence = new ConcurrentHashMap<String, String>();
    private final Set<Listener> removedListeners = Collections.newSetFromMap(new ConcurrentHashMap<Listener, Boolean>());
    /**
     * Flag means this component was requested to shutdown. So we don't want to process ZooKeeper events in watchers anymore
     * and this help us to avoid spamming exceptions in log when {@link ZooKeeperDistributedTree#client} is already closed
     * but watcher events are still processing.
     */
    private volatile boolean shutdownRequested = false;

    public ZooKeeperDistributedTree(CuratorFramework client) {
        this.client = client;
    }

    @Override
    public void addListener(final String node, final Listener listener) {
        try {
            LOG.info("Adding listener {} on {}", listener, possiblyWithSequence(node));
            final MyWatcher watcher = new MyWatcher(listener, possiblyWithSequence(node));
            watcher.checkEphemeral();
            watcher.setChildren();
            client.checkExists().usingWatcher(watcher).forPath(watcher.path);

            client.getChildren().inBackground(new BackgroundCallback() {

                @Override
                public void processResult(CuratorFramework client, CuratorEvent event) throws Exception {
                    final List<String> children = event.getChildren();
                    if (children != null) {
                        for (String child : children)
                            listener.nodeChildAdded(nodeName(node), nodeName(child));
                    }
                }
            }).forPath(node);
        } catch (Exception ex) {
            LOG.error("Adding listener on node " + node + " has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void removeListener(String node, Listener listener) {
        LOG.info("Removing listener {}", listener);
        removedListeners.add(listener);
    }

    @Override
    public void create(String node, boolean ephemeral) {
        try {
            if (exists(node)) {
                LOG.info("Node {} already exists ({})", node, possiblyWithSequence(node));
                return;
            }
            LOG.info("Creating {} node {}", ephemeral ? "ephemeral" : "", node);
            if (ephemeral) {
                EphemeralChildren ec = null;
                final String parent = parent(node);
                if (!exists(parent))
                    client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(parent);
                else
                    ec = getEphemeralChildren(parent);
                if (ec != null || client.checkExists().forPath(possiblyWithSequence(parent)).getEphemeralOwner() != 0) {
                    if (ec == null)
                        ec = new EphemeralChildren();
                    ec.createChild(child(node));
                    client.setData().forPath(possiblyWithSequence(parent), ec.toByteArray());
                    LOG.info("Created ephemeral child node {} ({})", node, possiblyWithSequence(parent) + '/' + child(node));
                    return;
                }
            }
            client.create().creatingParentsIfNeeded().withMode(ephemeral ? CreateMode.EPHEMERAL : CreateMode.PERSISTENT).forPath(node);
        } catch (KeeperException.NodeExistsException ignored) {
            LOG.error("Node " + node + " has been already created.");
        } catch (Exception ex) {
            LOG.error("Node " + node + " creation has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void createEphemeralOrdered(String node) {
        try {
            LOG.info("Creating ephemeral ordered node {}", node);
            if (exists(node)) {
                LOG.info("Node {} already exists ({})", node, possiblyWithSequence(node));
                return;
            }
            String nameWithSequence = client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(node + ':');
            LOG.info("Created node {}", nameWithSequence);
            putOrdered(node, nameWithSequence);
        } catch (Exception ex) {
            LOG.error("Node " + node + " creation has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public boolean exists(String node) {
        try {
            Stat stat = client.checkExists().forPath(possiblyWithSequence(node));
            final boolean exists = (stat != null);

            if (!exists) {
                EphemeralChildren ec = getEphemeralChildren(parent(node));
                if (ec != null)
                    return ec.hasChild(child(node));
            }
            return exists;
        } catch (Exception ex) {
            LOG.error("Node " + node + " checkExists has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void set(String node, byte[] data) {
        try {
            LOG.info("Setting node {} ({})", node, possiblyWithSequence(node));
            client.setData().forPath(possiblyWithSequence(node), data);
        } catch (Exception ex) {
            final EphemeralChildren ec = getEphemeralChildren(parent(node));
            if (ec != null) {
                try {
                    LOG.info("in set ec is {}", ec);
                    ec.setChildData(child(node), data);
                    client.setData().forPath(possiblyWithSequence(parent(node)), ec.toByteArray());
                    LOG.info("in set ec is {}", ec);
                    return;
                } catch (Exception e) {
                    ex = e;
                }
            }
            LOG.error("Node " + node + " setData has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public byte[] get(String node) {
        try {
            return client.getData().forPath(possiblyWithSequence(node));
        } catch (Exception ex) {
            final EphemeralChildren ec = getEphemeralChildren(parent(node));
            if (ec != null)
                return ec.getChildData(child(node));
            LOG.error("Node " + node + " getData has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void delete(String node) {
        try {
            LOG.info("Deleting node {} ({})", node, possiblyWithSequence(node));
            for (String child : getChildren1(node))
                delete(correctForRoot(node) + '/' + child);
            client.delete().guaranteed().forPath(possiblyWithSequence(node));
            removeOrdered(node);
        } catch (Exception ex) {
            final EphemeralChildren ec = getEphemeralChildren(parent(node));
            if (ec != null) {
                try {
                    ec.deleteChild(child(node));
                    client.setData().forPath(possiblyWithSequence(parent(node)), ec.toByteArray());
                    return;
                } catch (Exception e) {
                    ex = e;
                }
            }
            LOG.error("Node " + node + " delete has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    private List<String> getChildren1(String node) throws Exception {
        List<String> unordered = client.getChildren().forPath(possiblyWithSequence(node));
        if (unordered == null || unordered.isEmpty()) {
            final EphemeralChildren ec = getEphemeralChildren(node);
            if (ec != null)
                return ec.getChildren();
        }
        return orderedChildren(node, unordered);
    }

    @Override
    public List<String> getChildren(String node) {
        try {
            return getChildren1(node);
        } catch (Exception ex) {
            LOG.error("Node " + node + " getChildren has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void print(String node, java.io.PrintStream out) {
        print(node, out, 0);
        out.print('\n');
    }

    private void print(String node, java.io.PrintStream out, int indent) {
        for (int i = 0; i < indent; i++)
            out.print(' ');
        final String name = child(node);
        out.append('/').append(name != null ? name : "");
        try {
            for (String child : getChildren1(node)) {
                out.print('\n');
                print(correctForRoot(node) + '/' + child, out, indent + 4);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private class MyWatcher implements Watcher {
        public final Listener listener;
        public final String path;
        private List<String> children = Collections.emptyList();
        private EphemeralChildren ephemeralChildren = null;
        private boolean ephemeral;
        private boolean created;

        public MyWatcher(Listener listener, String path) {
            this.listener = listener;
            this.path = path;
        }

        public void setChildren() {
            try {
                List<String> cs = null;
                if (ephemeral) {
                    ephemeralChildren = getEphemeralChildren(path);
                    if (ephemeralChildren != null)
                        cs = ephemeralChildren.getChildren();
                } else {
                    if (client.checkExists().forPath(path) != null)
                        cs = client.getChildren().usingWatcher(this).forPath(path);
                }
                if (cs == null)
                    return;
                children = new ArrayList<String>(cs);
                Collections.sort(children);
                for (String child : children) {
                    try {
                        client.checkExists().usingWatcher(childrenWatcher).forPath(correctForRoot(path) + '/' + child);
                    } catch (Exception ex) {
                        LOG.error("Node checkExists has failed!", ex);
                    }
                }
            } catch (Exception ex) {
                LOG.error("Node checkExists has failed!", ex);
                throw Throwables.propagate(ex);
            }
        }

        public void checkEphemeral() {
            try {
                if (!created) {
                    final Stat stat = client.checkExists().usingWatcher(this).forPath(path);
                    if (stat != null) {
                        ephemeral = stat.getEphemeralOwner() != 0;
                        created = true;
                    }
                }
            } catch (Exception ex) {
                LOG.error("Exception:", ex);
                throw Throwables.propagate(ex);
            }
        }
        private final Watcher childrenWatcher = new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (shutdownRequested) {
                    return;
                }
                try {
                    if (event.getPath() != null) {
                        if (!removedListeners.remove(listener)) {
                            if (event.getType() == Watcher.Event.EventType.NodeDataChanged) {
                                assert path.equals(parent(event.getPath()));
                                LOG.info("Node child data changed: {} ({})", nodeName(event.getPath()), event.getPath());
                                listener.nodeChildUpdated(path, child(nodeName(event.getPath())));
                            }
                            client.checkExists().usingWatcher(this).forPath(event.getPath());
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Exception:", ex);
                    throw Throwables.propagate(ex);
                }
            }
        };

        @Override
        public void process(WatchedEvent event) {
            if (shutdownRequested) {
                return;
            }
            try {
                LOG.debug("ZooKeeper event: {}", event);
                if (!removedListeners.remove(listener)) {
                    final String node = nodeName(event.getPath());
                    switch (event.getType()) {
                        case NodeCreated:
                            LOG.info("Node created: {} ({})", node, event.getPath());
                            rememberIfOrdered(event.getPath());
                            listener.nodeAdded(node);
                            checkEphemeral();
                            if (client.checkExists().usingWatcher(this).forPath(event.getPath()) != null)
                                client.getChildren().usingWatcher(this).forPath(event.getPath());
                            break;
                        case NodeDataChanged:
                            client.checkExists().usingWatcher(this).forPath(event.getPath());
                            LOG.info("Node data changed: {} ({})", node, event.getPath());
                            if (ephemeral) {
                                final EphemeralChildren ec = getEphemeralChildren(event.getPath());
                                if (ec != null) {
                                    processChildrenChanged(event, node, ec.getChildren());
                                    if (ephemeralChildren != null) {
                                        for (Map.Entry<String, byte[]> entry : ephemeralChildren.getChildrenData().entrySet()) {
                                            if (ec.hasChild(entry.getKey()) && !Arrays.equals(entry.getValue(), ec.getChildData(entry.getKey())))
                                                listener.nodeChildUpdated(node, entry.getKey());
                                        }
                                        listener.nodeUpdated(node);
                                    } else {
                                        if (ec.getData() != null)
                                            listener.nodeUpdated(node);
                                    }
                                } else {
                                    if (ephemeralChildren != null) {
                                        processChildrenChanged(event, node, new ArrayList<String>());
                                        if (ephemeralChildren.getData() != null)
                                            listener.nodeUpdated(node);
                                    }
                                }
                                ephemeralChildren = ec;
                            } else
                                listener.nodeUpdated(node);
                            break;
                        case NodeDeleted:
                            LOG.info("Node deleted: {} ({})", node, event.getPath());
                            listener.nodeDeleted(node);
                            removeOrdered(node);
                            created = false;
                            break;
                        case NodeChildrenChanged:
                            LOG.info("Node children changed: {} ({})", node, event.getPath());
                            List<String> newChildren = Collections.emptyList();
                            try {
                                newChildren = new ArrayList<String>(client.getChildren().usingWatcher(this).forPath(event.getPath()));
                            } catch (KeeperException.NoNodeException e) {
                            }
                            processChildrenChanged(event, node, newChildren);
                            break;
                        case None:
                            try {
                                if (event.getPath() != null) {
                                    if (client.checkExists().usingWatcher(this).forPath(event.getPath()) != null)
                                        client.getChildren().usingWatcher(this).forPath(event.getPath());
                                }
                            } catch (KeeperException.NoNodeException e) {
                            }
                            break;
                    }
                }
            } catch (Exception ex) {
                LOG.error("Exception:", ex);
                throw Throwables.propagate(ex);
            }
        }

        private void processChildrenChanged(WatchedEvent event, String node, List<String> newChildren) throws Exception {
            Collections.sort(newChildren);
            LOG.debug("processChildrenChanged: old: {} new: {}", children, newChildren);

            int i = 0;
            int j = 0;
            while (i < children.size() || j < newChildren.size()) {
                String o = i < children.size() ? children.get(i) : null;
                String n = j < newChildren.size() ? newChildren.get(j) : null;

                final int c;
                if (o == null && n == null)
                    c = 0;
                else if (o == null)
                    c = 1;
                else if (n == null)
                    c = -1;
                else
                    c = o.compareTo(n);

                if (c == 0) {
                    i++;
                    j++;
                } else if (c > 0) {
                    LOG.info("Node child added: {} {} (" + n + ")", nodeName(n), path);
                    rememberIfOrdered(event.getPath() + '/' + n);
                    listener.nodeChildAdded(node, nodeName(n));
                    if (!ephemeral)
                        client.checkExists().usingWatcher(childrenWatcher).forPath(correctForRoot(event.getPath()) + '/' + n);
                    j++;
                } else {
                    LOG.info("Node child deleted: {} ({})", nodeName(o), o);
                    listener.nodeChildDeleted(node, nodeName(o));
                    removeOrdered(correctForRoot(node) + '/' + nodeName(o));
                    i++;
                }
            }
            children = newChildren;
        }
    }

    private List<String> orderedChildren(String parent, List<String> unordered) {
        final SortedMap<Long, String> sm = new TreeMap<Long, String>();
        final List<String> ordered = new ArrayList<String>(unordered.size());
        for (String child : unordered) {
            if (isOrdered(child)) {
                final String name = getName(child);
                sm.put(getSequence(child), name);
                putOrdered(parent + '/' + name, parent + '/' + child);
            } else
                ordered.add(child);
        }
        for (String child : sm.values())
            ordered.add(child);
        return ordered;
    }

    private String possiblyWithSequence(String node) {
        final String nodeWithSequence = namesWithSequence.get(node);
        return nodeWithSequence != null ? nodeWithSequence : node;
    }

    private void rememberIfOrdered(String node) {
        if (isOrdered(node))
            putOrdered(getName(node), node);
    }

    private void putOrdered(String node, String nodeWithSeq) {
        LOG.debug("Putting sequenced node: {} = {}", node, nodeWithSeq);
        namesWithSequence.put(node, nodeWithSeq);
    }

    private void removeOrdered(String node) {
        LOG.debug("Removing sequenced node: {}", node);
        namesWithSequence.remove(node);
    }

    private static String nodeName(String node) {
        return isOrdered(node) ? getName(node) : node;
    }
    private static final Pattern ORDERED_NODE = Pattern.compile(".*:[0-9]{10}+\\z");

    private static boolean isOrdered(String node) {
        if (node == null)
            return false;
        return ORDERED_NODE.matcher(node).matches();
    }

    private static String getName(String node) {
        return node.substring(0, node.lastIndexOf(':'));
    }

    private static long getSequence(String node) {
        return Long.parseLong(node.substring(node.lastIndexOf(':') + 1));
    }

    private EphemeralChildren getEphemeralChildren(String node) {
        try {
            if (node == null || node.isEmpty() || node.equals("/"))
                return null;
            node = possiblyWithSequence(node);
            final Stat stat = client.checkExists().forPath(node);
            if (stat == null)
                return null;
            else if (stat.getEphemeralOwner() == 0)
                return null;
            byte[] buffer = client.getData().forPath(node);
            if (buffer == null || buffer.length == 0)
                return null;
            return new EphemeralChildren(buffer);
        } catch (Exception ex) {
            LOG.error("Node " + node + " op has failed!", ex);
            throw Throwables.propagate(ex);
        }
    }

    private static final class EphemeralChildren {
        private byte[] data;
        private Map<String, byte[]> children;

        public EphemeralChildren() {
        }

        public EphemeralChildren(byte[] buffer) {
            fromByteArray(buffer);
        }

        public synchronized void setData(byte[] data) {
            if (data == null)
                this.data = null;
            else
                this.data = Arrays.copyOf(data, data.length);
        }

        public synchronized byte[] getData() {
            return data != null ? Arrays.copyOf(data, data.length) : null;
        }

        public synchronized boolean hasChild(String child) {
            if (children == null)
                return false;
            return children.containsKey(child);
        }

        public synchronized void createChild(String child) {
            if (children == null)
                children = new HashMap<String, byte[]>();
            children.put(child, null);
        }

        public synchronized void deleteChild(String child) {
            if (children == null)
                return;
            children.remove(child);
        }

        public synchronized void setChildData(String child, byte[] data) {
            if (children == null)
                children = new HashMap<String, byte[]>();
            children.put(child, data != null ? Arrays.copyOf(data, data.length) : null);
        }

        public synchronized byte[] getChildData(String child) {
            if (children == null || !children.containsKey(child))
                throw new RuntimeException("Child " + child + " does not exist!");
            final byte[] d = children.get(child);
            return d != null ? Arrays.copyOf(d, d.length) : null;
        }

        public synchronized List<String> getChildren() {
            return children != null ? new ArrayList<String>(children.keySet()) : null;
        }

        public synchronized Map<String, byte[]> getChildrenData() {
            return children != null ? children : Collections.<String, byte[]>emptyMap();
        }

        @Override
        public String toString() {
            return "EphemeralChildren{" + "children=" + children.keySet() + '}';
        }

        public synchronized byte[] toByteArray() {
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final ObjectOutputStream oos = new ObjectOutputStream(baos);
                if (data == null)
                    oos.writeInt(-1);
                else {
                    oos.writeShort(data.length);
                    oos.write(data);
                }
                oos.writeObject(children);
                oos.flush();
                baos.flush();

                return baos.toByteArray();
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        }

        public synchronized void fromByteArray(byte[] array) {
            try {
                final ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array));
                final int dataLen = ois.readInt();
                if (dataLen == -1)
                    data = null;
                else {
                    data = new byte[dataLen];
                    ois.readFully(data);
                }
                children = (Map<String, byte[]>) ois.readObject();
            } catch (IOException e) {
                throw new AssertionError(e);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }
    }

    @Override
    public void shutdown() {
        shutdownRequested = true;
    }
}
