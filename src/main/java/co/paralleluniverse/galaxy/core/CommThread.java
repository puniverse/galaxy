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

/**
 *
 * @author pron
 */
public class CommThread extends Thread {

    public CommThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
    }

    public CommThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public CommThread(Runnable target, String name) {
        super(target, name);
    }

    public CommThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public CommThread(String name) {
        super(name);
    }

    public CommThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public CommThread(Runnable target) {
        super(target);
    }

    public CommThread() {
    }
    
}
