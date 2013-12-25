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

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.core.Message.BACKUP;
import java.beans.ConstructorProperties;
import java.util.Collections;
import java.util.Iterator;

/**
 *
 * @author pron
 */
public class BackupNullImpl extends ClusterService implements Backup {
    @ConstructorProperties({"name", "cluster", "serverComm", "slaveComm"})
    public BackupNullImpl(String name, Cluster cluster, ServerComm serverComm, SlaveComm slaveComm) {
        super(name, cluster);

        if (slaveComm != null)
            slaveComm.setBackup(this);
    }

    @Override
    protected void postInit() throws Exception {
        setReady(true);
        super.postInit();
    }

    @Override
    protected void start(boolean master) {
    }

    @Override
    public void setCache(Cache cache) {
    }

    @Override
    public boolean inv(long id, short owner) {
        return true;
    }

    @Override
    public Iterator<BACKUP> iterOwned() {
        return (Iterator<BACKUP>) Collections.EMPTY_SET.iterator();
    }

    @Override
    public void startBackup() {
    }

    @Override
    public void backup(long id, long version) {
    }

    @Override
    public void endBackup() {
    }

    @Override
    public void flush() {
    }

    @Override
    public void receive(Message message) {
    }

    @Override
    public void slavesAck(long id) {
    }

    @Override
    public void slavesInvAck(long id) {
    }
}
