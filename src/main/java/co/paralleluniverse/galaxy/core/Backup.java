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

import co.paralleluniverse.galaxy.core.Message.BACKUP;
import java.util.Iterator;

/**
 *
 * @author pron
 */
public interface Backup {

    void setCache(Cache cache);

    boolean startBackup();

    /**
     * Must be called by the cache when the line is synchronized, and under a read-lock (i.e. between startBackup and endBackup)
     *
     * @param id
     * @param version
     */
    void backup(long id, long version);

    void endBackup(boolean locked);

    boolean inv(long id, short owner);

    void flush();

    Iterator<BACKUP> iterOwned();

    void receive(Message message);

    void slavesAck(long id);

    void slavesInvAck(long id);

}
