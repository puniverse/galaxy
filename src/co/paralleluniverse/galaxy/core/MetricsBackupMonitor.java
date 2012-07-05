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

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Timer;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author pron
 */
public class MetricsBackupMonitor implements BackupMonitor {
    private final Meter backups = Metrics.newMeter(Cache.class, "backups", "backup", TimeUnit.SECONDS);
    private final Meter replicationBackups = Metrics.newMeter(Cache.class, "replicationBackups", "replicationBackup", TimeUnit.SECONDS);
    private final Meter backupPackets = Metrics.newMeter(Cache.class, "backupPacketsSent", "backupPacket", TimeUnit.SECONDS);
    private final Timer slavesAckTime = Metrics.newTimer(Cache.class, "slavesAckTime", "slavesAck", TimeUnit.MICROSECONDS, TimeUnit.SECONDS);
    private final Timer serverAckTime = Metrics.newTimer(Cache.class, "serverAckTime", "serverAck", TimeUnit.MICROSECONDS, TimeUnit.SECONDS);

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
