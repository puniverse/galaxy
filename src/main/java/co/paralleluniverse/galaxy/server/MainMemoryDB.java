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
    
    void allocate(short owner, long start, int num);
    short findAllocation(long ref);
    
    long getMaxId();
    void close();
    void dump(java.io.PrintStream ps);
}
