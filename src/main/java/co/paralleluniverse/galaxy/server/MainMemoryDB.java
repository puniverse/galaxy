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
package co.paralleluniverse.galaxy.server;

/**
 *
 * @author pron
 */
public interface MainMemoryDB {
    short casOwner(long id, short oldNode, short newNode);
    void removeOwner(short node);
    
    Object beginTransaction();
    void commit(Object txn);
    void abort(Object txn);
    void write(long id, short owner, long version, byte[] data, Object txn);
    MainMemoryEntry read(long id);
    
    void delete(long id, Object txn);
    
    long getMaxId();
    void close();
    void dump(java.io.PrintStream ps);
}
