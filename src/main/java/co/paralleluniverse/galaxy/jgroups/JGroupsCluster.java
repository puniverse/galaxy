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

import co.paralleluniverse.common.monitoring.ThreadPoolExecutorMonitor;
import co.paralleluniverse.galaxy.cluster.DistributedTree;
import co.paralleluniverse.galaxy.core.AbstractCluster;
import co.paralleluniverse.galaxy.core.CommThread;
import co.paralleluniverse.galaxy.core.RefAllocator;
import co.paralleluniverse.galaxy.core.RefAllocatorSupport;
import co.paralleluniverse.galaxy.core.RootLocker;
import static co.paralleluniverse.galaxy.jgroups.JGroupsConstants.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.ConstructorProperties;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.Lock;
import org.jgroups.Address;
import org.jgroups.ChannelListener;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.blocks.atomic.Counter;
import org.jgroups.blocks.atomic.CounterService;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.protocols.SEQUENCER;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

/**
 *
 * @author pron
 */
class JGroupsCluster extends AbstractCluster implements RootLocker, RefAllocator {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsCluster.class);
    private static final long INITIAL_REF_ID = 0xffffffffL + 1;
    private final String jgroupsClusterName;
    private JChannel channel;
    private CounterService counterService;
    private LockService lockService;
    private Counter refIdCounter;
    private Channel controlChannel;
    private Channel dataChannel;
    //
    private String jgroupsConfFile;
    private Element jgroupsConfXML;
    private ThreadPoolExecutor jgroupsThreadPool;
    private final RefAllocatorSupport refAllocatorSupport = new RefAllocatorSupport();
    private final ExecutorService refAllocationExecutor = Executors.newFixedThreadPool(1);
    private volatile boolean counterReady;

    @ConstructorProperties({"name", "nodeId", "jgroupsClusterName"})
    public JGroupsCluster(String name, short nodeId, String jgroupsClusterName) throws Exception {
        super(name, nodeId);
        this.jgroupsClusterName = jgroupsClusterName;
    }

    public void setJgroupsConfFile(String jgroupsConfFile) {
        assertDuringInitialization();
        this.jgroupsConfFile = jgroupsConfFile;
    }

    public void setJgroupsConf(Element jgroupsConfXML) {
        assertDuringInitialization();
        this.jgroupsConfXML = jgroupsConfXML;
    }

    public void setJgroupsThreadPool(ThreadPoolExecutor threadPool) {
        assertDuringInitialization();
        this.jgroupsThreadPool = threadPool;
    }

    @Override
    protected void init() throws Exception {
        super.init();

        if (jgroupsConfXML != null)
            this.channel = new JChannel(jgroupsConfXML);
        else if (jgroupsConfFile != null)
            this.channel = new JChannel(jgroupsConfFile);
        else
            throw new IllegalStateException("jgroupsConf or jgroupsConfFile must be set!");

        if (jgroupsConfXML != null && jgroupsConfFile != null)
            throw new IllegalStateException("jgroupsConf or jgroupsConfFile cannot both be set!");

        this.controlChannel = new ControlChannel(channel);
        if (!controlChannel.hasProtocol(SEQUENCER.class))
            throw new RuntimeException("JChannel must have the SEQUENCER protocol");

        addNodeProperty(JGROUPS_ADDRESS, true, true, JGROUPS_ADDRESS_READER_WRITER);

        channel.addChannelListener(new ChannelListener() {

            @Override
            public void channelConnected(org.jgroups.Channel channel) {
            }

            @Override
            public void channelDisconnected(org.jgroups.Channel channel) {
                LOG.warn("JGroups channel disconnected. Going offline!");
                goOffline();
            }

            @Override
            public void channelClosed(org.jgroups.Channel channel) {
                LOG.warn("JGroups channel closed. Going offline!");
                goOffline();
            }

        });

        final DistributedTree tree = new DistributedTreeAdapter(new ReplicatedTree(controlChannel, null, 10000));

        if (jgroupsThreadPool == null)
            throw new RuntimeException("jgroupsThreadPool property not set!");

        jgroupsThreadPool.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        jgroupsThreadPool.setThreadFactory(new ThreadFactoryBuilder().setNameFormat("jgroups-%d").setThreadFactory(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                return new CommThread(r);
            }
        }).build());
        ThreadPoolExecutorMonitor.register("jgroups", jgroupsThreadPool);

        channel.getProtocolStack().getTransport().setDefaultThreadPool(jgroupsThreadPool);

        channel.connect(jgroupsClusterName, null, 10000);

        setName(getMyAddress().toString());
        setNodeProperty(JGROUPS_ADDRESS, getMyAddress());

        initRefIdCounter();

        if (!hasServer())
            this.lockService = new LockService(channel);

        this.dataChannel = new JChannelAdapter(channel) {
            @Override
            public void send(Message msg) throws Exception {
                msg.setFlag(Message.Flag.NO_TOTAL_ORDER);
                super.send(msg);
            }
        };

        setControlTree(tree);

        super.init(); // super.init() must be called after setControlTree()
    }

    private void initRefIdCounter() throws Exception {
        this.counterService = new CounterService(channel);
        this.refIdCounter = counterService.getOrCreateCounter("refIdCounter", 1);
        if (!hasServer())
            this.setCounter(INITIAL_REF_ID);
        refAllocationExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                LOG.info("Waiting for id counter to be set...");
                long id;
                while ((id = refIdCounter.get()) < INITIAL_REF_ID)
                    Thread.sleep(500);
                LOG.info("Id counter set: {}", id);
                counterReady = true;
                refAllocatorSupport.fireCounterReady();
                return null;
            }
        });
    }

    @Override
    public void shutdown() {
        super.shutdown();
        refAllocationExecutor.shutdownNow();
        channel.disconnect();
        channel.close();
    }

    @Override
    public Object getUnderlyingResource() {
        return channel;
    }

    @Override
    protected boolean isMe(NodeInfoImpl node) {
        return node.get(JGROUPS_ADDRESS).equals(getMyAddress());
    }

    protected final Address getMyAddress() {
        return channel.getAddress();
    }

    public Channel getDataChannel() {
        return dataChannel;
    }

    private boolean setCounter(long initialValue) {
        initialValue = Math.max(initialValue, INITIAL_REF_ID);
        LOG.info("Setting ref counter to {}", initialValue);
        for (;;) {
            long id = refIdCounter.get();
            if (id >= initialValue) {
                LOG.info("Id counter set by someone else to {}", id);
                return false;
            }

            if (refIdCounter.compareAndSet(id, initialValue)) {
                LOG.info("Set id counter to {}", initialValue);
                return true;
            }
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
    
    @Override
    public void allocateRefs(final int count) {
        refAllocationExecutor.submit(new Runnable() {

            @Override
            public void run() {
                long end = refIdCounter.addAndGet(count);
                refAllocatorSupport.fireRefsAllocated(end - count, count);
            }

        });
    }

    @Override
    public Object lockRoot(int id) {
        final Lock lock = lockService.getLock(Integer.toHexString(id));
        lock.lock();
        return lock;
    }

    @Override
    public void unlockRoot(Object obj) {
        ((Lock) obj).unlock();
    }

}
