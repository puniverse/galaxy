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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.collection.ConcurrentMultimapWithCopyOnWriteArrayList;
import co.paralleluniverse.common.spring.Service;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.DistributedBranchHelper;
import co.paralleluniverse.galaxy.cluster.DistributedTree;
import co.paralleluniverse.galaxy.cluster.DistributedTreeAdapter;
import co.paralleluniverse.galaxy.cluster.LifecycleListener;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.cluster.NodePropertyListener;
import co.paralleluniverse.galaxy.cluster.ReaderWriter;
import co.paralleluniverse.galaxy.cluster.SlaveConfigurationListener;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class basically works with the distributed tree provided by its concrete subclasses to write this node's info to the
 * cluster and to provide queries and events regarding cluster status.
 *
 * @author pron
 */
public abstract class AbstractCluster extends Service implements Cluster {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCluster.class);
    protected static final String ROOT = "/co.paralleluniverse.galaxy";
    protected static final String NODES = ROOT + "/nodes";
    protected static final String LEADERS = ROOT + "/leaders";
    //
    private boolean hasServer = true;
    //
    private final Set<String> requiredPeerNodeProperties = new HashSet<String>();
    private final Set<String> requiredServerProperties = new HashSet<String>();
    private final Map<String, ReaderWriter> readerWriters = new ConcurrentHashMap<String, ReaderWriter>();
    //
    private final Map<String, NodeInfoImpl> nodes = new ConcurrentHashMap<String, NodeInfoImpl>();
    private final Set<String> leaders = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private DistributedTree controlTree;
    private DistributedTree protectedTree;
    private DistributedBranchHelper branch;
    //
    protected final short myId;
    protected final NodeInfoImpl myNodeInfo;
    private volatile boolean online;
    private volatile boolean joined;
    private volatile boolean master;
    private volatile NodeInfoImpl myMaster;
    private final List<NodeInfoImpl> mySlaves = new CopyOnWriteArrayList<NodeInfoImpl>();
    private final Map<Short, NodeInfoImpl> masters = new ConcurrentHashMap<Short, NodeInfoImpl>();
    private volatile NodeInfoImpl server;
    private final List<NodeInfoImpl> slaveServers = new CopyOnWriteArrayList<NodeInfoImpl>();
    private final Set<Short> activeNodes = Collections.newSetFromMap(new ConcurrentHashMap<Short, Boolean>());
    //
    private final List<NodeChangeListener> nodeChangeListeners = new CopyOnWriteArrayList<NodeChangeListener>();
    private final List<LifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<LifecycleListener>();
    private final List<SlaveConfigurationListener> slaveConfigurationListeners = new CopyOnWriteArrayList<SlaveConfigurationListener>();
    private final ConcurrentMultimapWithCopyOnWriteArrayList<String, NodePropertyListener> masterNodePropertyListeners = new ConcurrentMultimapWithCopyOnWriteArrayList<String, NodePropertyListener>();
    private final ConcurrentMultimapWithCopyOnWriteArrayList<String, NodePropertyListener> slaveNodePropertyListeners = new ConcurrentMultimapWithCopyOnWriteArrayList<String, NodePropertyListener>();

    public AbstractCluster(String name, short nodeId) {
        super(name);
        if (nodeId < 0) {
            throw new IllegalArgumentException("nodeId " + nodeId + " is <= 0!");
        }
        this.myId = nodeId;
        this.online = false;
        this.master = false;
        this.requiredPeerNodeProperties.add("id");
        this.requiredServerProperties.add("id");
        this.myNodeInfo = new NodeInfoImpl();
        myNodeInfo.setNodeId(nodeId);
    }

    protected void setName(String name) {
        assertDuringInitialization();

        myNodeInfo.setName(name);
    }

    public void setHasServer(boolean hasServer) {
        assertDuringInitialization();
        this.hasServer = hasServer;
    }

    @Override
    public boolean hasServer() {
        return hasServer;
    }

    @Override
    public synchronized void addNodeProperty(String property, boolean requiredForPeer, boolean requiredForServer, ReaderWriter<?> readerWriter) {
        if (requiredForPeer) {
            assertDuringInitialization();
            requiredPeerNodeProperties.add(property);
        }
        if (requiredForServer) {
            assertDuringInitialization();
            requiredServerProperties.add(property);
        }
        if (!requiredForPeer && !requiredForServer) {
            myNodeInfo.addProperty(property);
        }
        readerWriters.put(property, readerWriter);
    }

    @Override
    public synchronized void setNodeProperty(String property, Object value) {
        if (requiredPeerNodeProperties.contains(property) || requiredServerProperties.contains(property))
            assertDuringInitialization();

        myNodeInfo.set(property, value);
    }

    protected final void setControlTree(final DistributedTree controlTree) {
        assertDuringInitialization();
        this.controlTree = controlTree;
        this.protectedTree = new DistributedTreeAdapter(controlTree) {
            @Override
            public void create(String node, boolean ephemeral) {
                super.create(protect(node), ephemeral);
            }

            @Override
            public void set(String node, byte[] data) {
                super.set(protect(node), data);
            }

            @Override
            public void delete(String node) {
                super.delete(protect(node));
            }

            private String protect(String node) {
                if (node.startsWith(NODES)) {
                    throw new IllegalArgumentException("Tree contents under " + NODES + " are reserved for internal use only!");
                }
                return node;
            }
        };
    }

    /**
     * This is perhaps ugly, but overriding implementations must call this method at the end, or, at least, after calling
     * setControlTree
     *
     * @throws Exception
     */
    @Override
    protected void postInit() throws Exception {
        if (controlTree == null) {
            throw new RuntimeException("controlTree not set");
        }

        controlTree.create(NODES, false);
        controlTree.create(LEADERS, false);

        if (controlTree.exists(myNodeInfo.treeNodePath)) {
            LOG.error("A node with the name " + myNodeInfo.getName() + " already exists!");
            throw new RuntimeException("Initialization failure");
        }

        LOG.info("Required peer node properties: {}", requiredPeerNodeProperties);
        LOG.info("Required server properties: {}", requiredServerProperties);

        final Set<String> requiredProperties = (myId == 0 ? requiredServerProperties : requiredPeerNodeProperties);
        for (String property : requiredProperties) {
            if (!property.equals("id") && myNodeInfo.get(property) == null) {
                LOG.error("Required property {} not set!", property);
                throw new RuntimeException("Initialization failure");
            }
        }

        /// the calls in the demarcated section need to be in this specific order to avoid a possible race between nodes and leaders
        // `>>>> BEGIN CAREFULLY ORDERED SECTION
        branch = new DistributedBranchHelper(controlTree, NODES, false) {
            @Override
            protected boolean isNodeComplete(String node, Set<String> properties) {
                if (!properties.contains("id")) {
                    return false;
                }
                final short id = Short.parseShort(new String(controlTree.get(node + "/id"), Charsets.UTF_8));
                final Set<String> requiredProperties = (id == 0 ? requiredServerProperties : requiredPeerNodeProperties);
                final boolean success = properties.containsAll(requiredProperties);
                return success;
            }
        };

        branch.addListener(new DistributedTree.ListenerAdapter() {
            @Override
            public void nodeChildAdded(String parentPath, String childName) {
                AbstractCluster.this.nodeAdded(childName);
            }

            @Override
            public void nodeChildDeleted(String parentPath, String childName) {
                AbstractCluster.this.nodeRemoved(childName);
            }
        });
        // This read and handles the nodes.
        branch.init();

        controlTree.addListener(LEADERS, new DistributedTree.ListenerAdapter() {
            @Override
            public void nodeChildAdded(String parentPath, String childName) {
                AbstractCluster.this.leaderAdded(childName);
            }

            @Override
            public void nodeChildDeleted(String parentPath, String childName) {
                AbstractCluster.this.leaderRemoved(childName);
            }
        });

        myNodeInfo.writeToTree();

        setReady(true);

        super.postInit();

        joined = true;
        fireJoinedCluster();
        for (short id : masters.keySet()) {
            if (id != myId)
                fireNodeAdded(id);
        }
    }

    @Override
    protected void available(boolean value) {
        super.available(value);

        if (!value)
            goOffline();
    }

    @Override
    public DistributedTree getDistributedTree() {
        return protectedTree;
    }

    @Override
    public short getMyNodeId() {
        return myId;
    }

    @Override
    public NodeInfo getMyNodeInfo() {
        return myNodeInfo;
    }

    @Override
    public NodeInfo getNodeInfoByName(String nodeName) {
        return nodes.get(nodeName);
    }

    @Override
    public Collection<NodeInfo> getMasters() {
        return Collections.unmodifiableCollection((Collection<? extends NodeInfo>) masters.values());
    }

    public Collection<NodeInfo> getAllSlaves() {
        return Collections.unmodifiableCollection((Collection<? extends NodeInfo>) slaveServers);
    }

    @Override
    public NodeInfo getMaster(short node) {
        return masters.get(node);
    }

    @Override
    public boolean isMaster(NodeInfo node) {
        return masters.get(node.getNodeId()) == node;
    }

    @Override
    public NodeInfo getMyMaster() {
        return myMaster;
    }

    @Override
    public List<NodeInfo> getMySlaves() {
        return ImmutableList.copyOf((List<? extends NodeInfo>) mySlaves);
    }

    @Override
    public boolean isMaster() {
        return master;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    public boolean isJoined() {
        return joined;
    }

    @Override
    public void shutdown() {
        controlTree.shutdown();
// moved to setOnline(false)
//        if (myNodeInfo.getName() != null) {
//            controlTree.delete(LEADERS + "/" + myNodeInfo.getName());
//        }
//        controlTree.delete(myNodeInfo.treeNodePath);
    }

    public void goOnline() {
        if (isOnline())
            return;
        if (isSecondSlave()) {
            LOG.error("THERE ALREADY EXISTS A SLAVE FOR NODE " + getMyNodeId() + ". ABORTING.");
            goOffline();
            throw new UnsupportedOperationException("Second slave is not supported");
        }
        try {
            awaitAvailable();
            LOG.info("NODE IS NOW ATTEMPTING TO GO ONLINE");
            controlTree.createEphemeralOrdered(LEADERS + "/" + myNodeInfo.getName());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void goOffline() {
        setOnline(false);
    }

    private void setOnline(boolean value) {
        if (this.online == value)
            return;

        if (value) {
            if (isSecondSlave()) {
                LOG.error("THERE ALREADY EXISTS A SLAVE FOR NODE " + getMyNodeId() + ". ABORTING.");
                value = false;
            }
        }
        this.online = value;
        if (online) {
            fireOnline();
            for (NodeInfo slave : mySlaves)
                fireSlaveAdded(slave);
        } else {
            LOG.info("NODE IS GOING OFFLINE!");
            // TODO: check if web have to test (myNodeInfo.getName() != null) before
            controlTree.delete(LEADERS + "/" + myNodeInfo.getName());
//      this was inside the shutdown.
//        if (myNodeInfo.getName() != null) {
//            controlTree.delete(LEADERS + "/" + myNodeInfo.getName());
//        }
          // TODO: test if it helps and can be done here  
//        controlTree.delete(myNodeInfo.treeNodePath);
            fireOffline();
            shutdown();
        }
    }

    private void setMaster(boolean value) {
        if (this.master == value) {
            return;
        }
        this.master = value;
        if (master) {
            fireSwitchToMaster();
        } else {
            LOG.error("Switch to slave??? Souldn't happen!!!!");
            //fireSwitchToSlave();
        }

        for (NodeInfoImpl slave : findSlaves(myId)) {
            mySlaves.add(slave);
            fireSlaveAdded(slave);
        }
    }

    private void setMyMaster(NodeInfoImpl newMaster) {
        if (this.myMaster == newMaster) {
            return;
        }
        this.myMaster = newMaster;
        fireNewMaster(newMaster);
    }

    @Override
    public Set<Short> getNodes() {
        return Collections.unmodifiableSet(activeNodes);
    }

    @Override
    public Set<NodeInfo> getNodesByProperty(String propertyName, Object value) {
        final Set<NodeInfo> ns = new HashSet<NodeInfo>();
        for (NodeInfoImpl n : nodes.values()) {
            final Object v = n.get(propertyName);
            if ((v == value) || (v != null && v.equals(value))) {
                ns.add(n);
            }
        }
        return Collections.unmodifiableSet(ns);
    }

    protected abstract boolean isMe(NodeInfoImpl node);

    private void nodeAdded(String nodeName) {
        LOG.info("New node added: {}", nodeName);
//        Thread.dumpStack();
        final NodeInfoImpl node = createNodeInfo(nodeName, true);
        nodes.put(nodeName, node);
        LOG.debug("nodes: {}", nodes);
        if (leaders.contains(nodeName)) // leader event waited for node data
            finishLeaderAdded(node);
    }

    private void nodeRemoved(String nodeName) {
        LOG.info("Node removed: {}", nodeName);

        final NodeInfoImpl node = nodes.get(nodeName);

        if (isMe(node))
            setOnline(false);
//        else
//            leaderRemoved(nodeName);
        nodes.remove(nodeName);
    }

    private void leaderAdded(String nodeName) {
        leaders.add(nodeName);
        LOG.info("New leader added: {}", nodeName);
        //Thread.dumpStack();   

        final NodeInfoImpl node = nodes.get(nodeName);
        if (node == null) {
            LOG.info("Node {} does not have a complete node info in the control tree. Waiting for node data completition", nodeName);
            // finish_leader_add will be called after node data is completed
            return;
        }
        finishLeaderAdded(node);
    }

    private void finishLeaderAdded(final NodeInfoImpl node) {
        LOG.info("Finishing leader addition: {}", node.getName());
        final NodeInfoImpl nodesMaster = findMaster(node.getNodeId(), null);
        final boolean nodeIsServer = (node.getNodeId() == 0);
        if (node.getNodeId() == myId) {
            if (isMe(node)) {
                this.master = (node == nodesMaster);
                if (!this.master)
                    assert this.myMaster == nodesMaster; // this.myMaster = master;

                LOG.info("=================================");
                LOG.info("GOING ONLINE AS {} {} {}", new Object[]{myId, this.master ? "MASTER" : "SLAVE.", this.master ? "" : "MASTER IS " + nodesMaster});
                LOG.info("=================================");
                setOnline(true);
            } else if (node == nodesMaster) {
                LOG.info("Node {} is my master.", node);
                this.myMaster = node;
                masters.put(node.getNodeId(), node);
            } else if (online && this.master) {
                LOG.info("Node {} is added as slave.", node);
                mySlaves.add(node);
                fireSlaveAdded(node);
            }
        } else if (node == nodesMaster) {
            LOG.info("New master for node {} is {}", node.getNodeId(), node);
            final boolean switchover = masters.containsKey(node.getNodeId()); // could only happen in the extreme circumstance of the master going offline simultaneously with this node going online
            masters.put(node.getNodeId(), node);
            if (nodeIsServer) {
                this.server = node;
            }
            if (switchover) {
                fireNodeSwitched(node.getNodeId());
            } else {
                activeNodes.add(node.getNodeId());
                fireNodeAdded(node.getNodeId());
            }
        } else {
            if (nodeIsServer)
                slaveServers.add(node);
            LOG.info("New slave for {}: {}", node.getNodeId(), node);
        }
    }

    private void leaderRemoved(String nodeName) {
        LOG.info("Leader removed: {}", nodeName);

        final NodeInfoImpl node = nodes.get(nodeName);
        if (node == null) {
            LOG.info("Leader {} has been removed but it has no valid node info.", nodeName);
            return;
        }
        final boolean nodeIsServer = (node.getNodeId() == 0);
        final NodeInfoImpl oldMaster = masters.get(node.getNodeId());
        if (nodeIsServer) {
            slaveServers.remove(node);
        }
        final NodeInfoImpl newMaster = findMaster(node.getNodeId(), nodeName); // we may have been called by removeNode before the leader is actually removed, so we ask findMaster to ignore it
        if (oldMaster != node) {
            // a slave node has died
            if (node.getNodeId() == myId && this.master) {
                if (mySlaves.remove(node)) { // protects against multiple calls
                    LOG.info("My slave node {} has gone offline.", node);
                    fireSlaveRemoved(node);
                }
            } else
                LOG.info("Slave node {} has gone offline.", node);
        } else {
            // a master node has died
            if (node.getNodeId() == myId) {
                if (newMaster == null) {
                    LOG.info("No master found for node {}. (I am {})", node.getNodeId(), online ? "ONLINE" : "NOT ONLINE");
                    setOnline(false);
                } else if (isMe(newMaster)) {
                    if (!this.master) { // protects against multiple calls
                        LOG.info("=====================");
                        LOG.info("SWITCHING TO MASTER");
                        LOG.info("=====================");

                        this.myMaster = null;
                        masters.remove(myId);
                        setMaster(true);
                    }
                } else {
                    if (this.master) { // shouldn't happen
                        LOG.error("Switch to slave??? Souldn't happen!!!!");
                        //LOG.info("Switching to slave!");
                        //this.myMaster = newMaster;
                        //setMaster(false);
                    } else {
                        LOG.info("New master: {}", newMaster);
                        masters.put(newMaster.getNodeId(), newMaster);
                        setMyMaster(newMaster);
                    }
                }
            } else {
                if (nodeIsServer)
                    this.server = newMaster;
                if (newMaster == null) {
                    if (masters.remove(node.getNodeId()) != null) { // protects against multiple calls
                        LOG.info("No master for node {} - it's going offline!", node.getNodeId());
                        activeNodes.remove(node.getNodeId());
                        fireNodeRemoved(node.getNodeId());
                    }
                } else {
                    if (masters.put(newMaster.getNodeId(), newMaster) != newMaster) { // protects against multiple calls
                        LOG.info("New master for node {} is {}", node.getNodeId(), newMaster);
                        fireNodeSwitched(newMaster.getNodeId());
                    }
                }
            }
        }
    }

    private NodeInfoImpl findMaster(short nodeId, String oldMaster) {
        for (String nodeName : controlTree.getChildren(LEADERS)) {
            if (oldMaster != null && oldMaster.equals(nodeName))
                continue;
            final NodeInfoImpl node = nodes.get(nodeName);
            if (node != null) {
                if (node.getNodeId() == nodeId)
                    return node;
            }
        }
        return null;
    }

    private boolean isSecondSlave() {
        int counter = 0;
        for (String nodeName : controlTree.getChildren(LEADERS)) {
            final NodeInfoImpl node = nodes.get(nodeName);
            if (node != null) {
                if (isMe(node))
                    break;
                else if (node.getNodeId() == getMyNodeId())
                    counter++;
            }
        }

        return (counter > 1);
    }

    private List<NodeInfoImpl> findSlaves(short nodeId) {
        List<NodeInfoImpl> slaves = new ArrayList<NodeInfoImpl>();
        boolean foundMaster = false;
        for (String nodeName : controlTree.getChildren(LEADERS)) {
            final NodeInfoImpl node = nodes.get(nodeName);
            assert node != null;
            if (node.getNodeId() == nodeId) {
                if (foundMaster)
                    slaves.add(node);
                else
                    foundMaster = true;
            }
        }
        return slaves;
    }

    protected NodeInfoImpl createNodeInfo(String nodeName, boolean attachToTree) {
        return new NodeInfoImpl(nodeName, attachToTree);
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.add(listener);
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycleListeners.remove(listener);
    }

    @Override
    public void addSlaveConfigurationListener(SlaveConfigurationListener listener) {
        slaveConfigurationListeners.add(listener);
    }

    @Override
    public void removeSlaveConfigurationListener(SlaveConfigurationListener listener) {
        slaveConfigurationListeners.remove(listener);
    }

    @Override
    public void addNodeChangeListener(NodeChangeListener listener) {
        nodeChangeListeners.add(listener);
    }

    @Override
    public void removeNodeChangeListener(NodeChangeListener listener) {
        nodeChangeListeners.remove(listener);
    }

    @Override
    public void addMasterNodePropertyListener(String property, NodePropertyListener listener) {
        assertPropertyRegistered(property);
        masterNodePropertyListeners.put(property, listener);
    }

    @Override
    public void removeMasterNodePropertyListener(String property, NodePropertyListener listener) {
        masterNodePropertyListeners.remove(property, listener);
    }

    @Override
    public void addSlaveNodePropertyListener(String property, NodePropertyListener listener) {
        assertPropertyRegistered(property);
        slaveNodePropertyListeners.put(property, listener);
    }

    @Override
    public void removeSlaveNodePropertyListener(String property, NodePropertyListener listener) {
        slaveNodePropertyListeners.remove(property, listener);
    }

    private void fireJoinedCluster() {
        for (LifecycleListener listener : lifecycleListeners) {
            try {
                listener.joinedCluster();
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireOnline() {
        for (LifecycleListener listener : lifecycleListeners) {
            try {
                listener.online(master);
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireOffline() {
        for (LifecycleListener listener : lifecycleListeners) {
            try {
                listener.offline();
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireSwitchToMaster() {
        for (LifecycleListener listener : lifecycleListeners) {
            try {
                listener.switchToMaster();
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireNodeAdded(short id) {
        if (!isJoined())
            return;

        for (NodeChangeListener listener : nodeChangeListeners) {
            try {
                listener.nodeAdded(id);
            } catch (Exception e) {
                LOG.error("Listener threw an exception for node " + id, e);
            }
        }
    }

    private void fireNodeSwitched(short id) {
        if (!isJoined())
            return;

        for (NodeChangeListener listener : nodeChangeListeners) {
            try {
                listener.nodeSwitched(id);
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireNodeRemoved(short id) {
        if (!isJoined())
            return;

        for (NodeChangeListener listener : nodeChangeListeners) {
            try {
                listener.nodeRemoved(id);
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireSlaveAdded(NodeInfo node) {
        if (!isOnline())
            return;

        for (SlaveConfigurationListener listener : slaveConfigurationListeners) {
            try {
                listener.slaveAdded(node);
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireSlaveRemoved(NodeInfo node) {
        if (!isOnline())
            return;

        for (SlaveConfigurationListener listener : slaveConfigurationListeners) {
            try {
                listener.slaveRemoved(node);
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    private void fireNewMaster(NodeInfo node) {
        if (!isOnline())
            return;

        for (SlaveConfigurationListener listener : slaveConfigurationListeners) {
            try {
                listener.newMaster(node);
            } catch (Exception e) {
                LOG.error("Listener threw an exception.", e);
            }
        }
    }

    void fireNodePropertyChanged(NodeInfoImpl node, String property, Object value) {
        if (node == myNodeInfo)
            return;

        if (masters.containsValue(node)) {
            for (NodePropertyListener listener : masterNodePropertyListeners.get(property)) {
                try {
                    listener.propertyChanged(node, property, value);
                } catch (Exception e) {
                    LOG.error("Listener threw an exception.", e);
                }
            }
        } else if (mySlaves.contains(node)) {
            for (NodePropertyListener listener : slaveNodePropertyListeners.get(property)) {
                try {
                    listener.propertyChanged(node, property, value);
                } catch (Exception e) {
                    LOG.error("Listener threw an exception.", e);
                }
            }
        }
    }

    private ReaderWriter assertPropertyRegistered(String property) {
        final ReaderWriter rw = readerWriters.get(property);
        if (rw == null)
            throw new RuntimeException("No ReaderWriter set for property " + property);

        return rw;
    }

    protected final Object readProperty(String property, byte[] value) {
        ReaderWriter rw = assertPropertyRegistered(property);
        return rw.read(value);
    }

    protected final byte[] writeProperty(String property, Object value) {
        ReaderWriter rw = assertPropertyRegistered(property);
        return rw.write(value);
    }

    protected class NodeInfoImpl extends DistributedTree.ListenerAdapter implements NodeInfo {
        private String name;
        private String treeNodePath;
        private short nodeId = -1;
        private final Map<String, Object> properties = new ConcurrentHashMap<String, Object>();
        private final boolean attached;

        public NodeInfoImpl() {
            this.attached = false;
        }

        public NodeInfoImpl(String name) {
            this(name, true);
        }

        public NodeInfoImpl(String name, boolean attachToTree) {
            setName(name);
            this.attached = attachToTree;

            if (attached) {
                controlTree.addListener(treeNodePath, this);
                for (String child : controlTree.getChildren(treeNodePath))
                    nodeChildUpdated(treeNodePath, child);
            }
        }

        private void setName(String name) {
            this.name = name;
            this.treeNodePath = NODES + '/' + name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Collection<String> getProperties() {
            return properties.keySet();
        }

        public String getTreeNodePath() {
            return treeNodePath;
        }

        @Override
        public short getNodeId() {
            return nodeId;
        }

        private void setNodeId(short nodeId) {
            assertDuringInitialization();
            if (attached) {
                throw new IllegalStateException("Node is attached to the tree!");
            }
            this.nodeId = nodeId;
        }

        public void addProperty(String property) {
            assertInitialized();
            if (attached)
                throw new IllegalStateException("Node is attached to the tree!");

            controlTree.create(treeNodePath + '/' + property, true);
        }

        public synchronized void set(String property, Object value) {
            if (attached)
                throw new IllegalStateException("Node is attached to the tree!");

            if (properties.get(property) != null) {
                if (!properties.get(property).equals(value))
                    throw new IllegalStateException("Property " + property + " has already been set do a different value");
            } else {
                properties.put(property, value);
                if (isInitialized()) {
                    LOG.info("Publishing additional node info: {} = {}", property, value);
                    controlTree.set(treeNodePath + '/' + property, writeProperty(property, value));
                }
            }
        }

        @Override
        public Object get(String property) {
            return properties.get(property);
        }

        public void writeToTree() {
            assertDuringInitialization();
            if (attached)
                throw new IllegalStateException("Node is attached to tree -> cannot be written!");

            if (name == null || nodeId < 0)
                throw new AssertionError("Incomplete node data");

            LOG.info("Publishing node info: name = {}, id = {}", name, nodeId);
            controlTree.create(treeNodePath, true);
            controlTree.create(treeNodePath + '/' + "id", true);
            controlTree.set(treeNodePath + '/' + "id", Short.toString(nodeId).getBytes(Charsets.UTF_8));
            for (Map.Entry<String, Object> property : properties.entrySet()) {
                LOG.info("Publishing node info: {} = {}", property.getKey(), property.getValue());
                controlTree.create(treeNodePath + '/' + property.getKey(), true);
                controlTree.set(treeNodePath + '/' + property.getKey(), writeProperty(property.getKey(), property.getValue()));
            }
            controlTree.flush();
        }

        protected void readChild(String childName, byte[] value) {
            if ("id".equals(childName)) {
                final short nodeValue = Short.parseShort(new String(value, Charsets.UTF_8));
                if (this.nodeId >= 0) {
                    if (this.nodeId != nodeValue)
                        throw new RuntimeException("Id for node " + name + " is already set to " + nodeId);
                    return;
                }
                this.nodeId = nodeValue;
            } else {
                if (value == null)
                    return;
                if (readerWriters.get(childName) == null) {
                    LOG.warn("No reader set for property {} (found in node {})", childName, name);
                    return;
                }

                final Object currentProperty = properties.get(childName);
                final Object newValProperty = readProperty(childName, value);
                // required properties shouldn't be changed
                if ((requiredPeerNodeProperties.contains(childName) || requiredServerProperties.contains(childName)) && currentProperty != null) {
                    if (!currentProperty.equals(newValProperty))
                        throw new RuntimeException("Required property " + childName + " for node " + name + " is already set to " + properties.get(childName));
                    else
                        return;
                }

                properties.put(childName, newValProperty);
            }
        }

        @Override
        public void nodeChildAdded(String parentPath, String childName) {
            nodeChildUpdated(parentPath, childName);
        }

        @Override
        public final void nodeChildUpdated(String parentPath, String childName) {
            assert parentPath.equals(treeNodePath);
            try {
                readChild(childName, controlTree.get(parentPath + '/' + childName));
                fireNodePropertyChanged(this, childName, properties.get(childName));
            } catch (Exception e) {
                LOG.error("Exception while reading control tree value.", e);
            }
        }

        @Override
        public void nodeChildDeleted(String node, String childName) {
            assert !requiredPeerNodeProperties.contains(childName) && !requiredServerProperties.contains(childName);
            properties.remove(childName);
            fireNodePropertyChanged(this, childName, null);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (obj == this)
                return true;
            if (!(obj instanceof NodeInfo))
                return false;

            final NodeInfoImpl other = (NodeInfoImpl) obj;
            if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name))
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 31 * hash + (this.name != null ? this.name.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("NODE ").append(name);
            sb.append(" id: ").append(nodeId);
            for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(properties).entrySet())
                sb.append(' ').append(entry.getKey()).append(": ").append(entry.getValue());

            return sb.toString();
        }
    }
}
