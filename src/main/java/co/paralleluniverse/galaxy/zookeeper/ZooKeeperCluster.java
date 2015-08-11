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

import static co.paralleluniverse.galaxy.cluster.DistributedTreeUtil.child;
import co.paralleluniverse.galaxy.core.AbstractCluster;
import co.paralleluniverse.galaxy.core.RefAllocator;
import co.paralleluniverse.galaxy.core.RefAllocatorSupport;
import co.paralleluniverse.galaxy.core.RootLocker;
import com.google.common.base.Throwables;
import java.beans.ConstructorProperties;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.atomic.AtomicValue;
import org.apache.curator.framework.recipes.atomic.DistributedAtomicLong;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class ZooKeeperCluster extends AbstractCluster implements RootLocker, RefAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperCluster.class);
    private static final long INITIAL_REF_ID = 0xffffffffL + 1;
    private static final String ROOT_LOCKS = ROOT + "/root_locks";
    private static final String REF_COUNTER = ROOT + "/ref_counter";
    private final String zkConnectString;
    private int sessionTimeoutMs = 15000;
    private int connectionTimeoutMs = 10000;
    private RetryPolicy retryPolicy = new ExponentialBackoffRetry(20, 20);
    private CuratorFramework client;
    private String myNodeName;
    private final RefAllocatorSupport refAllocatorSupport = new RefAllocatorSupport();
    private final ExecutorService refAllocationExecutor = Executors.newFixedThreadPool(1);
    private DistributedAtomicLong refIdCounter;
    private final String zkNamespace;
    private volatile boolean counterReady;

    @ConstructorProperties({"name", "nodeId", "zkConnectString", "zkNamespace"})
    public ZooKeeperCluster(String name, short nodeId, String zkConnectString, String zkNamespace) throws Exception {
        super(name, nodeId);
        this.zkConnectString = zkConnectString;
        this.zkNamespace = zkNamespace;
    }

    @ConstructorProperties({"name", "nodeId", "zkConnectString"})
    public ZooKeeperCluster(String name, short nodeId, String zkConnectString) throws Exception {
        this(name, nodeId, zkConnectString, null);
    }

    public void setConnectionTimeoutMs(int connectionTimeoutMs) {
        assertDuringInitialization();
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        assertDuringInitialization();
        this.retryPolicy = retryPolicy;
    }

    public void setSessionTimeoutMs(int sessionTimeoutMs) {
        assertDuringInitialization();
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    @Override
    protected void init() throws Exception {
        super.init();
        client = CuratorFrameworkFactory.builder().namespace(zkNamespace).connectString(zkConnectString).sessionTimeoutMs(sessionTimeoutMs).
                connectionTimeoutMs(connectionTimeoutMs).retryPolicy(retryPolicy).defaultData(new byte[0]).build();
        client.start();

        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(ROOT + "/node_names");
        } catch (KeeperException.NodeExistsException e) {
        }

        myNodeName = child(client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(ROOT + "/node_names" + "/node-"));
        LOG.info("Node name is {}, id is {}", myNodeName, myId);
        setName(myNodeName);

        initRefIdCounter();

        final ZooKeeperDistributedTree tree = new ZooKeeperDistributedTree(client);
        setControlTree(tree);

        super.init(); // super.init() must be called after setControlTree()
    }

    private void initRefIdCounter() throws Exception {
        this.refIdCounter = new DistributedAtomicLong(client, REF_COUNTER, retryPolicy);
        AtomicValue<Long> av;

        av = refIdCounter.increment(); // we need this b/c refIdCounter.compareAndSet(0, INITIAL_REF_ID) doesn't work on a newly allocated znode.
        if (!av.succeeded())
            throw new RuntimeException("Error initializing refIdCounter");
        if (!hasServer())
            setCounter(INITIAL_REF_ID);
        refAllocationExecutor.submit(new Callable<Void>() {

            @Override
            public Void call() throws Exception {
                LOG.info("Waiting for id counter to be set...");
                try {
                    AtomicValue<Long> av;
                    for (;;) {
                        av = refIdCounter.get();
                        if (av.succeeded()) {
                            if (av.postValue() >= INITIAL_REF_ID)
                                break;
                        } else
                            LOG.info("Failed to read counter");
                        Thread.sleep(500);
                    }
                    LOG.info("Id counter set: {}", av.postValue());
                    counterReady = true;
                    refAllocatorSupport.fireCounterReady();
                    return null;
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }

        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        refAllocationExecutor.shutdownNow();
        client.close();
    }

    @Override
    protected boolean isMe(NodeInfoImpl node) {
        return myNodeName.equals(node.getName());
    }

    @Override
    public Object getUnderlyingResource() {
        return client;
    }

    @Override
    public Object lockRoot(int id) {
        try {
            final InterProcessMutex mutex = new InterProcessMutex(client, ROOT_LOCKS + '/' + id);
            mutex.acquire();
            return mutex;
        } catch (Exception ex) {
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void unlockRoot(Object lock) {
        try {
            final InterProcessMutex mutex = (InterProcessMutex) lock;
            mutex.release();
        } catch (Exception ex) {
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void addRefAllocationsListener(RefAllocationsListener listener) {
        refAllocatorSupport.addRefAllocationsListener(listener);
        if (counterReady)
            listener.counterReady();
    }

    @Override
    public void removeRefAllocationsListener(RefAllocationsListener listener) {
        refAllocatorSupport.addRefAllocationsListener(listener);
    }

    @Override
    public Collection<RefAllocationsListener> getRefAllocationsListeners() {
        return refAllocatorSupport.getRefAllocationListeners();
    }

    private boolean setCounter(long initialValue) {
        initialValue = Math.max(initialValue, INITIAL_REF_ID);
        LOG.info("Setting ref counter to {}", initialValue);
        try {
            AtomicValue<Long> av;
            long id = 0;
            for (;;) {
                av = refIdCounter.compareAndSet(id, initialValue);
                if (av.succeeded()) {
                    assert av.postValue() == initialValue;
                    LOG.info("Set id counter to {}", initialValue);
                    return true;
                } else if (av.postValue() >= initialValue) {
                    LOG.info("Id counter set by someone else to {}", initialValue);
                    return false;
                } else
                    id = av.preValue();

                Thread.sleep(500);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public void allocateRefs(final int count) {
        refAllocationExecutor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    LOG.info("Allocating {} IDs", count);
                    final AtomicValue<Long> av = refIdCounter.add((long) count);
                    if (av.succeeded())
                        refAllocatorSupport.fireRefsAllocated(av.preValue(), count);
                    else
                        LOG.error("Allocating ref IDs has failed!");
                } catch (Exception e) {
                    LOG.error("Allocating ref IDs has failed!", e);
                }
            }

        });
    }

}
