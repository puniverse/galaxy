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
package co.paralleluniverse.galaxy;

/**
 * Thrown when an item ref was not found in the grid.
 */
public class RefNotFoundException extends RuntimeException {
    public RefNotFoundException(long ref) {
        super("Reference " + Long.toHexString(ref) + " was not found (probably deleted)!");
    }
}
