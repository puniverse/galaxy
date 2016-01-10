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
import static co.paralleluniverse.common.logging.LoggingUtils.hex;
import co.paralleluniverse.common.spring.Service;
import co.paralleluniverse.common.util.DegenerateInvocationHandler;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.core.Cache.CacheLine;
import co.paralleluniverse.galaxy.core.Message.BACKUP;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKET;
import co.paralleluniverse.galaxy.core.Message.BACKUP_PACKETACK;
import co.paralleluniverse.galaxy.core.Message.INV;
import java.beans.ConstructorProperties;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 *
 * @author pron
 */
public class BackupImpl extends ClusterService implements Backup {
    // The trick here is to allow fast updates w/o copying the line buffer with each update (and generating garbage in the process).
    // We just want to mark updated lines, and copy their contents periodically during flushes.
    private static final Logger LOG = LoggerFactory.getLogger(BackupImpl.class);
    private long maxDelayNanos = TimeUnit.NANOSECONDS.convert(10, TimeUnit.MILLISECONDS);
    private final Comm serverComm;
    private final SlaveComm slaveComm;
    private Cache cache;
    //
    private final ReadWriteLock mapLock = new ReentrantReadWriteLock(); // this could become a bottleneck. consider replacing with a scalable lock
    private NonBlockingHashMapLong<BackupEntry> map;
    private final NonBlockingHashMapLong<BackupEntry> map1 = new NonBlockingHashMapLong<BackupEntry>();
    private final NonBlockingHashMapLong<BackupEntry> map2 = new NonBlockingHashMapLong<BackupEntry>();
    private volatile boolean copyImmediately;
    private final ReentrantLock currentBackupsLock = new ReentrantLock();
    private final Condition currentBackupsPossiblyReady = currentBackupsLock.newCondition();
    private final Map<Long, BACKUP> currentBackups = new HashMap<Long, BACKUP>();
    private long nextId = 100000;
    private BACKUP_PACKET lastSent;
    private volatile boolean awaitServer;
    private volatile boolean awaitSlaves;
    private boolean shouldFlush;
    private long lastFlush;
    //
    private volatile boolean completedReplication = false;
    //
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BackupMonitor monitor;

    @ConstructorProperties({"name", "cluster", "serverComm", "slaveComm", "monitoringType"})
    public BackupImpl(String name, Cluster cluster, ServerComm serverComm, SlaveComm slaveComm, MonitoringType monitoringType) {

        this(name, cluster, serverComm, slaveComm, createMonitor(monitoringType, name));
    }

    BackupImpl(String name, Cluster cluster, ServerComm serverComm, SlaveComm slaveComm, BackupMonitor monitor) {
        super(name, cluster);
        this.monitor = monitor;

        if (cluster.hasServer() && serverComm == null)
            throw new RuntimeException("Configured to have server but serverComm is null!");

        this.serverComm = serverComm;
        this.slaveComm = slaveComm;

        if (slaveComm != null)
            slaveComm.setBackup(this);

        map = map1;
    }

    static BackupMonitor createMonitor(MonitoringType monitoringType, String name) {
        if (monitoringType == null)
            return (BackupMonitor) Proxy.newProxyInstance(Cache.class.getClassLoader(), new Class<?>[]{BackupMonitor.class}, DegenerateInvocationHandler.INSTANCE);
        else
            switch (monitoringType) {
                case JMX:
                    return new JMXBackupMonitor(name);
                case METRICS:
                    return new MetricsBackupMonitor();
            }
        throw new IllegalArgumentException("Unknown MonitoringType " + monitoringType);
    }

    public void setMaxDelay(int maxDelayMillis) {
        assertDuringInitialization();
        this.maxDelayNanos = TimeUnit.NANOSECONDS.convert(maxDelayMillis, TimeUnit.MILLISECONDS);
    }

    @ManagedAttribute
    public int getMaxDelay() {
        return (int) TimeUnit.MILLISECONDS.convert(maxDelayNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void init() throws Exception {
        if (serverComm instanceof Service)
            removeDependency((Service) serverComm);
        super.init();
    }

    @Override
    protected void postInit() throws Exception {
        ((Service) getCluster()).awaitAvailable();
        // If a master already exists let the client slave-comm replicate. We'll go online when we're done (see handleReceivedBackup), and in the meantime
        // we won't present this node as a slave.
        // If not, I may become the master, or may go online shortly after another concurrently initializing node which will become the master,
        // in which case we can expect the replication to complete shortly (as the master won't have time to update that many items).
        if (getCluster().getMaster(getCluster().getMyNodeId()) == null)
            setReady(true);

        super.postInit();
    }

    @Override
    protected void start(boolean master) {
        if (master)
            startFlushThread();
    }

    @Override
    public void switchToMaster() {
        super.switchToMaster();

        if (!isAvailable() || !completedReplication) {
            LOG.info("Node has not completed replication so cannot become master. Going offline!");
            getCluster().goOffline();
        } else {
            startFlushThread();
        }
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        scheduler.shutdownNow();
    }

    @Override
    public void setCache(Cache cache) {
        assertDuringInitialization();
        this.cache = cache;
    }

    private void startFlushThread() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                flushNow();
            }
        }, maxDelayNanos, maxDelayNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean inv(long id, short owner) {
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("INV {}, {}", id, owner);
            return !slaveComm.send(Message.INV(getCluster().getMyNodeId(), id, owner));
        } catch (NodeNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean startBackup() {
        LOG.debug("start backup");
        mapLock.readLock().lock();

        if (copyImmediately) {
            currentBackupsLock.lock();
            if (!copyImmediately) // test again
                currentBackupsLock.unlock();
            else
                return true;
        }
        return false;
    }

    @Override
    public void endBackup(boolean locked) {
        LOG.debug("end backup");
        mapLock.readLock().unlock();
        if (locked) {
            currentBackupsPossiblyReady.signal();
            currentBackupsLock.unlock();
        }
    }

    /**
     * Must be called by the cache when the line is synchronized, and under a read-lock (i.e. between startBackup and endBackup)
     *
     * @param id
     * @param version
     */
    @Override
    public void backup(long id, long version) {
        if (LOG.isDebugEnabled())
            LOG.debug("Backup: {} ver: {} {}", new Object[]{hex(id), version, copyImmediately ? "(COPY)" : ""});
        if (copyImmediately) {
            currentBackups.put(id, makeBackup(cache.getLine(id), version));
            oldMap().remove(id);
        }
        else
            map.put(id, new BackupEntry(id, version));
    }

    @Override
    public void flush() {
        scheduler.submit(new Runnable() {
            @Override
            public void run() {
                flushNow();
            }
        });
    }

    private void flushNow() {
        try {
            final NonBlockingHashMapLong<BackupEntry> oldMap = map;
            mapLock.writeLock().lock(); // just to make sure we're not copying in the middle of a transaction
            try {
                if (oldMap.isEmpty())
                    return;

                switchMaps(); // we switch the maps in the hopes that oldMap is complete, and so backups can continue to work on second map
            } finally {
                mapLock.writeLock().unlock();
            }

            LOG.debug("FLUSHING");

            currentBackupsLock.lock();
            try {
                assert !copyImmediately;
                for (Iterator<BackupEntry> it = oldMap.values().iterator(); it.hasNext();) {
                    final BackupEntry be = it.next();
                    final CacheLine line = cache.getLine(be.id);
                    assert line != null;
                    synchronized (line) {
                        final Message.BACKUP backup = makeBackup(line, be.version);
                        if (backup != null) {
                            oldMap.remove(be.id);
                            if (LOG.isDebugEnabled())
                                LOG.debug("Copied {} ver {} for backup", hex(be.id), be.version);
                            currentBackups.put(be.id, backup);
                        } else {
                            if (LOG.isDebugEnabled())
                                LOG.debug("Matching version for {} ({}) not found", hex(be.id), be.version);
                            this.copyImmediately = true;
                        }
                    }
                    it.remove();
                }
            } finally {
                currentBackupsLock.unlock();
            }

            if (copyImmediately) { // backups incomplete
                LOG.debug("Incomplete backups. Completeing.");
                mapLock.writeLock().lock();
                currentBackupsLock.lock();
                try {
                    for (Iterator<BackupEntry> it = map.values().iterator(); it.hasNext();) {
                        final BackupEntry be = it.next();
                        final CacheLine line = cache.getLine(be.id);
                        assert line != null;
                        synchronized (line) {
                            Message.BACKUP backup = makeBackup(line, be.version);
                            if (backup != null) {
                                map.remove(be.id);
                                if (LOG.isDebugEnabled())
                                    LOG.debug("Copied {} ver {} for backup", hex(be.id), be.version);
                                currentBackups.put(be.id, backup);
                            } else
                                oldMap.put(be.id, be);
                        }
                        it.remove();
                    }
                } finally {
                    currentBackupsLock.unlock();
                    mapLock.writeLock().unlock();
                }

                currentBackupsLock.lock();
                try {
                    for (Iterator<BackupEntry> it = oldMap.values().iterator(); it.hasNext();) {
                        final BackupEntry be = it.next();
                        final Message.BACKUP backup = currentBackups.get(be.id);
                        if (backup != null && backup.getVersion() >= be.version)
                            it.remove();
                    }

                    while (!oldMap.isEmpty()) {
                        LOG.debug("Waiting for missing transactions: {}", oldMap);
                        currentBackupsPossiblyReady.await();
                    }
                    this.copyImmediately = false;
                } finally {
                    currentBackupsLock.unlock();
                }
            }

            final BACKUP_PACKET packet = flush1();
            if (packet != null)
                send(packet);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private BACKUP_PACKET flush1() {
        currentBackupsLock.lock();
        try {
            if (lastSent == null) {
                shouldFlush = false;
                this.lastFlush = System.nanoTime();
                if (currentBackups.isEmpty())
                    return null;
                final BACKUP_PACKET packet;
                packet = Message.BACKUP_PACKET(nextId, currentBackups.values());
                nextId++;
                lastSent = packet;
                currentBackups.clear();
                return packet;
            } else { // last backup not yet acked
                LOG.debug("Last backup not acked. Not sending.");
                final long passedMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - lastFlush, TimeUnit.NANOSECONDS);
                if (passedMillis > 2000)
                    LOG.warn("SLAVE HAS NOT ACKED IN {} MILLISECONDS. SOMETHING IS SERIOUSLY WRONG!", passedMillis);
                shouldFlush = true;
                return null;
            }
        } finally {
            currentBackupsLock.unlock();
        }
    }

    private void send(BACKUP_PACKET packet) {
        monitor.addBackupPacket();
        monitor.addBackups(packet.getBackups().size());
        try {
            awaitServer = true;
            awaitSlaves = true;
            if (serverComm != null) {
                LOG.debug("Sending backup packet to server: {}", packet);
                serverComm.send(packet);
            } else
                ack(true);
            if (!slaveComm.send(packet))
                ack(false);
            else
                LOG.debug("Sent backup packet to slaves: {}", packet);
        } catch (NodeNotFoundException e) {
            throw new RuntimeException("Server not found!", e);
        }
    }

    private void switchMaps() {
        if (map == map1)
            map = map2;
        else
            map = map1;
    }

    private NonBlockingHashMapLong<BackupEntry> oldMap() {
        return map == map1 ? map2 : map1;
    }

    private Message.BACKUP makeBackup(CacheLine line, long version) {
        if (line.getVersion() != version)
            return null;
        final Message.BACKUP backup;
        if (line.getData() == null) {
            backup = Message.BACKUP(line.getId(), line.getVersion(), null);
        } else {
            final ByteBuffer buffer = ByteBuffer.allocate(line.getData().limit()); // storage.allocateStorage(line.getData().limit());
            line.rewind();
            buffer.put(line.getData());
            line.rewind();
            buffer.flip();
            backup = Message.BACKUP(line.getId(), line.getVersion(), buffer);
        }
        LOG.debug("Copying version {} of line {} data: {}", new Object[]{backup.getVersion(), hex(backup.getLine()), backup.getData() != null ? "(" + backup.getData().remaining() + " bytes)" : "null"});
        return backup;
    }

    private void serverAck(Message message) {
        final BACKUP_PACKETACK ack = (BACKUP_PACKETACK) message;
        if (ack.getId() != lastSent.getId()) {
            LOG.warn("Received backup ack from server with id {} which is different from last sent: {}", ack.getId(), lastSent.getId());
            return;
        }
        ack(true);
    }

    @Override
    public void slavesAck(long id) {
        if (lastSent == null) {
            LOG.warn("Received backup ack from slaves with id {} but lastSent is null", id);
            return;
        }
        if (id != lastSent.getId()) {
            LOG.warn("Received backup ack from slaves with id {} which is different from last sent: {}", id, lastSent.getId());
            return;
        }
        ack(false);
    }

    @Override
    public void slavesInvAck(long id) {
        cache.receive(Message.INVACK(getCluster().getMyNodeId(), id));
    }

    private void ack(boolean server) {
        LOG.debug("Ack {}", server ? "server" : "slaves");
        BACKUP_PACKET packet = null;
        final BACKUP_PACKET _lastSent;
        currentBackupsLock.lock();
        try {
            if (server && awaitSlaves) {
                awaitServer = false;
                return;
            }
            if (!server && awaitServer) {
                awaitSlaves = false;
                return;
            }
            _lastSent = lastSent;
            lastSent = null;
            awaitServer = false;
            awaitSlaves = false;
            if (shouldFlush)
                packet = flush1();
        } finally {
            currentBackupsLock.unlock();
        }

        for (BACKUP backup : _lastSent.getBackups())
            cache.receive(Message.BACKUPACK((short) 0, backup.getLine(), backup.getVersion()).setIncoming());
        if (packet != null)
            send(packet);
    }

    @Override
    public Iterator<BACKUP> iterOwned() {
        final Iterator<Cache.CacheLine> it = cache.ownedIterator();
        return new Iterator<BACKUP>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public BACKUP next() {
                final Cache.CacheLine line = it.next();
                synchronized (line) {
                    monitor.addReplicationBackup(1);
                    return (BACKUP) Message.BACKUP(line.getId(), line.getVersion(), line.getData()).cloneDataBuffers();
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public void receive(Message message) {
        switch (message.getType()) {
            case BACKUP_PACKETACK:
                serverAck(message);
                break;
            case BACKUP_PACKET:
                if (getCluster().isMaster())
                    LOG.warn("Received backup packet while master: {}", message);
                else {
                    monitor.addBackupPacket();
                    monitor.addBackups(((BACKUP_PACKET) message).getBackups().size());
                    handleReceivedBackupPacket((BACKUP_PACKET) message);
                }
                break;
            case BACKUP:
                if (getCluster().isMaster())
                    LOG.warn("Received backup while master: {}", message);
                else {
                    monitor.addReplicationBackup(1);
                    handleReceivedBackup((BACKUP) message);
                }
                break;
            case INV:
                if (getCluster().isMaster())
                    LOG.warn("Received INV while master: {}", message);
                else
                    handleReceivedInvalidate((INV) message);
                break;
            default:
        }
    }

    private void handleReceivedBackupPacket(BACKUP_PACKET packet) {
        try {
            LOG.debug("Received backup packet: {}", packet);
            for (BACKUP backup : packet.getBackups())
                cache.receive(backup);
            slaveComm.send(Message.BACKUP_PACKETACK(packet));
        } catch (NodeNotFoundException e) {
            LOG.error("Exception while sending backup ack", e);
        }
    }

    private void handleReceivedBackup(BACKUP backup) {
        LOG.debug("Received replication backup: {}", backup);
        if (backup.getLine() < 0) {
            LOG.info("Slave node now ready! (completed replication)");
            completedReplication = true;
            setReady(true);
        } else
            cache.receive(backup);
    }

    private void handleReceivedInvalidate(INV inv) {
        try {
            LOG.debug("Received inv: {}", inv);
            cache.receive(inv);
            slaveComm.send(Message.INVACK(inv));
        } catch (NodeNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static class BackupEntry {
        public final long id;
        public final long version;

        public BackupEntry(long id, long version) {
            this.id = id;
            this.version = version;
        }

        @Override
        public String toString() {
            return "BackupEntry{" + "id: " + Long.toHexString(id) + ", version: " + version + '}';
        }
    }
}
