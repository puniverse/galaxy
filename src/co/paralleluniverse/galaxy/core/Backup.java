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

import co.paralleluniverse.galaxy.core.Message.BACKUP;
import java.util.Iterator;

/**
 *
 * @author pron
 */
public interface Backup {

    void setCache(Cache cache);

    void startBackup();

    /**
     * Must be called by the cache when the line is synchronized, and under a read-lock (i.e. between startBackup and endBackup)
     *
     * @param id
     * @param version
     */
    void backup(long id, long version);

    void endBackup();

    boolean inv(long id, short owner);

    void flush();

    Iterator<BACKUP> iterOwned();

    void receive(Message message);

    void slavesAck(long id);

    void slavesInvAck(long id);

}
