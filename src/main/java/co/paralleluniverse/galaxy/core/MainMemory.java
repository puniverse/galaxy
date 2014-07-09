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

import co.paralleluniverse.common.MonitoringType;
import co.paralleluniverse.common.io.Persistables;
import static co.paralleluniverse.common.logging.LoggingUtils.hex;
import co.paralleluniverse.common.util.DegenerateInvocationHandler;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import static co.paralleluniverse.galaxy.core.Cache.isReserved;
import co.paralleluniverse.galaxy.core.Message.BACKUP;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKET;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.server.MainMemoryDB;
import co.paralleluniverse.galaxy.server.MainMemoryEntry;
import com.google.common.base.Throwables;
import java.beans.ConstructorProperties;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class MainMemory extends ClusterService implements MessageReceiver, NodeChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(MainMemory.class);
    private static final long INITIAL_REF_ID = 0xffffffffL + 1;
    private static final short SERVER = 0;
    private final Comm comm;
    private final MainMemoryDB store;
    private final MainMemoryMonitor monitor;
    private final AtomicLong refCounter = new AtomicLong();

    @ConstructorProperties({"name", "cluster", "store", "comm", "monitoringType"})
    public MainMemory(String name, Cluster cluster, MainMemoryDB store, Comm comm, MonitoringType monitoringType) {
        this(name, cluster, store, comm, createMonitor(monitoringType, name));
    }

    MainMemory(String name, Cluster cluster, MainMemoryDB store, Comm comm, MainMemoryMonitor monitor) {
        super(name, cluster);
        this.comm = comm;
        this.store = store;
        this.monitor = monitor;

        monitor.setMonitoredObject(this);
        cluster.addNodeChangeListener(this);
        comm.setReceiver(this);
    }

    @Override
    protected void start(boolean master) {
        if (master) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Performing store dump:");
                store.dump(System.err);
            }

            refCounter.set(Math.max(INITIAL_REF_ID, store.getMaxId() + 1));
        }
        setReady(true);
    }

    @Override
    public void switchToMaster() {
        super.switchToMaster();
    }

    @Override
    protected void shutdown() {
        store.close();
    }

    private static MainMemoryMonitor createMonitor(MonitoringType monitoringType, String name) {
        if (monitoringType == null)
            return (MainMemoryMonitor) Proxy.newProxyInstance(MainMemory.class.getClassLoader(), new Class<?>[]{MainMemoryMonitor.class}, DegenerateInvocationHandler.INSTANCE);
        else
            switch (monitoringType) {
                case JMX:
                    return new JMXMainMemoryMonitor(name);
                case METRICS:
                    return new MetricsMainMemoryMonitor();
            }
        throw new IllegalArgumentException("Unknown MonitoringType " + monitoringType);
    }

    @Override
    public void receive(Message message) {
//        if (!getCluster().isMaster()) {
//            LOG.debug("Ignoring message {} 'cause I'm just a slave.");
//            return;
//        }
        LOG.debug("Received: {}", message);
        switch (message.getType()) {
            case GET:
            case GETX:
                handleMessageGet((Message.GET) message);
                break;
            case INV:
                handleMessageInvalidate((Message.INV) message);
                break;
            case DEL:
                handleMessageDelete((LineMessage) message);
                break;
            case MSG:
                handleMessageMsg((Message.MSG) message);
                break;
            case BACKUP_PACKET:
                handleMessageBackup((BACKUP_PACKET) message);
                break;
            case INVOKE:
                handleMessageGet((LineMessage) message); // Server cant invoke, return putx, chnged_owner or notFound.
                break;
            case ALLOC_REF:
                handleMessageAllocRef((Message.ALLOC_REF) message);
        }
    }

    void send(Message message) {
        LOG.debug("Sending: {}", message);
        try {
            comm.send(message);
        } catch (NodeNotFoundException e) {
        }
    }

    private boolean handleMessageGet(LineMessage msg) {
        final long id = msg.getLine();

        for (;;) {
            short owner;
            if (isReserved(id) && store.casOwner(id, (short) -1, msg.getNode()) == msg.getNode()) { // if nonexistent root - create it
                if (LOG.isDebugEnabled())
                    LOG.debug("Owner of reserved line {} is now node {} (CAS)", hex(id), msg.getNode());
                monitor.addOwnerWrite();
                monitor.addObjectServed();
                store.write(id, msg.getNode(), 1, new byte[0], null);
                send(Message.PUTX(msg, id, new short[0], 0, 1, null));
                return true;
            } else if ((owner = store.casOwner(id, SERVER, msg.getNode())) == msg.getNode()) { // if owner is server, then transfer ownership
                MainMemoryEntry entry = store.read(id);
                if (LOG.isDebugEnabled())
                    LOG.debug("Owner of line {} is now node {} (previously owned by server)", hex(id), msg.getNode());
                monitor.addOwnerWrite();
                monitor.addObjectServed();
                send(Message.PUTX(msg, id, new short[0], 0, entry.version, ByteBuffer.wrap(entry.data)));
                return true;
            }
            if (owner == -1 && !isReserved(id))
                owner = store.findAllocation(id);

            if (owner == -1 && !isReserved(id)) {
                send(Message.NOT_FOUND(msg));
                return false;
            } else if (owner > SERVER) {
//                if (owner == msg.getNode()) {// probably a slave turned master
//                    MainMemoryEntry entry = store.read(id);
//                    send(Message.PUTX(msg, id, new short[0], entry.version, ByteBuffer.wrap(entry.data)));
//                    monitor.addObjectServed();
//                } else
                send(Message.CHNGD_OWNR(msg, id, owner, true));
                monitor.addOwnerServed();
                return false;
            }
            LOG.debug("casOwner returned {}", owner);
        }
    }

    private void handleMessageInvalidate(Message.INV msg) {
        final long id = msg.getLine();
        final short owner = msg.getNode();
        final short previousOwner = msg.getPreviousOwner();

        // if mesages are sent to server instead of broadcast, node B may get a putx from node A, then node A would die (before B INVs the server)
        // then node C gets the line from the server, and then node B INVs the server, we must INV B (in this case, B will wait for our response. See Cache.transitionToE())
        // so, we check to see where A got the line from (previous owner). Since it's B but we already have C as the owner, we INV instead of INVACK.
        short currentOwner;
        if ((currentOwner = store.casOwner(id, previousOwner, owner)) == owner) {
            if (LOG.isDebugEnabled())
                LOG.debug("Got INV: Owner of line {} is now node {}", hex(id), msg.getNode());
            monitor.addOwnerWrite();
            send(Message.INVACK(msg));
        } else {
            if (LOG.isDebugEnabled())
                LOG.debug("Got INV of line {} from {}, but different owner ({}) listed so replying INV", new Object[]{hex(id), msg.getNode(), currentOwner});
            monitor.addOwnerServed();
            send(Message.INV(msg, id, currentOwner));
        }
    }

    private void handleMessageDelete(LineMessage msg) {
        final long id = msg.getLine();
        final short owner = msg.getNode();
        if (LOG.isDebugEnabled())
            LOG.debug("Line {} deleted.", hex(id));

        final Object txn = store.beginTransaction();
        try {
            store.delete(id, txn);
            store.commit(txn);
            send(Message.INVACK(msg));
        } catch (Exception e) {
            LOG.error("Exception during delete. Aborting transaction.", e);
            store.abort(txn);
            throw Throwables.propagate(e);
        }
    }

    private void handleMessageMsg(Message.MSG msg) {
        final long id = msg.getLine();

        if (handleMessageGet(msg))
            send(Message.MSG(msg.getNode(), id, msg.isMessenger(), msg.getData())); // return to sender, immediately following a PUTX
    }

    private void handleMessageBackup(BACKUP_PACKET msg) {
        final Object txn = store.beginTransaction();
        try {
            monitor.addTransaction(msg.getBackups().size());
            for (BACKUP backup : msg.getBackups()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Backing up version {} of line {} data: {}", new Object[]{backup.getVersion(), hex(backup.getLine()), backup.getData() != null ? "(" + backup.getData().remaining() + " bytes)" : "null"});
                store.write(backup.getLine(), msg.getNode(), backup.getVersion(), Persistables.toByteArray(backup.getData()), txn);
            }
            store.commit(txn);
            send(Message.BACKUP_PACKETACK(msg));
        } catch (Exception e) {
            LOG.error("Exception during DB operation. Aborting transaction.", e);
            store.abort(txn);
            throw Throwables.propagate(e);
        }
    }

    private void handleMessageAllocRef(Message.ALLOC_REF msg) {
        final int num = msg.getNum();
        final long end = refCounter.addAndGet(num);
        final long start = end - num;
        store.allocate(msg.getNode(), start, num);
        send(Message.ALLOCED_REF(msg, start, num));
        
        monitor.addAllocation(num);
    }

    @Override
    public void nodeRemoved(short node) {
        LOG.info("Node {} removed. Server now owns its lines.", node);
        store.removeOwner(node);
    }

    @Override
    public void nodeAdded(short id) {
    }

    @Override
    public void nodeSwitched(short id) {
    }
}
