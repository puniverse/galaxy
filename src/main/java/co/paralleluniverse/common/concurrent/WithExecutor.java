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
 * An object implementing this interface (usually a listener) requests that all of its methods invoked be invoked through the executor.
 */
public interface WithExecutor {
    /**
     * Returns the executor on which to run the object's methods.
     * @return The executor on which to run the object's methods.
     */
    Executor getExecutor();
}
