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
    private Cache cache;
    
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
        this.cache = cache;
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
    public boolean startBackup() {
        return false;
    }

    @Override
    public void backup(long id, long version) {
        cache.receive(Message.BACKUPACK((short) 0, id, version).setIncoming());
    }

    @Override
    public void endBackup(boolean locked) {
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
