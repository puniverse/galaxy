/*
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
package co.paralleluniverse.common.concurrent;

import java.util.concurrent.Executor;

/**
 *
 * @author pron
 */
public class CurrentThreadExecutor implements Executor {
    public static final CurrentThreadExecutor instance = new CurrentThreadExecutor();

    private CurrentThreadExecutor() {
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
