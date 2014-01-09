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

import co.paralleluniverse.common.monitoring.Metrics;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
public class MetricsBackupMonitor implements BackupMonitor {
    private final Meter backups = Metrics.meter(metric("backups"));
    private final Meter replicationBackups = Metrics.meter(metric("replicationBackups"));
    private final Meter backupPackets = Metrics.meter(metric("backupPacketsSent"));
    private final Timer slavesAckTime = Metrics.timer(metric("slavesAckTime"));
    private final Timer serverAckTime = Metrics.timer(metric("serverAckTime"));

    protected final String metric(String name) {
        return MetricRegistry.name("co.paralleluniverse", "galaxy", "Cache", name);
    }
    @Override
    public void addReplicationBackup(int num) {
        replicationBackups.mark(num);
    }

    @Override
    public void addBackups(int num) {
        backups.mark(num);
    }

    @Override
    public void addBackupPacket() {
        backupPackets.mark();
    }

    @Override
    public void addSlaveAckTime(long nanos) {
        slavesAckTime.update(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void addServerAckTime(long nanos) {
        serverAckTime.update(nanos, TimeUnit.NANOSECONDS);
    }
}
