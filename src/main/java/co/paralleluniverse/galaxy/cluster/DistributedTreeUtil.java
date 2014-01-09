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
package co.paralleluniverse.galaxy.cluster;

import java.util.Iterator;
import java.util.Scanner;

/**
 * Some utility functions for working with a {@link DistributedTree}.
 */
public final class DistributedTreeUtil {
    
    /**
     * Returns the parent tree node of a given node.
     * @param node A tree node's full path.
     * @return The full path of the given node's parent.
     */
    public static String parent(String node) {
        final int index = node.lastIndexOf('/');
        if (index < 0)
            return null;
        return node.substring(0, index);
    }

    /**
     * Returns the last component of a node's full path (i.e. its child name in its parent).
     * @param node A tree node's full path.
     * @return The last component of a node's full path.
     */
    public static String child(String node) {
        final int index = node.lastIndexOf('/');
        if (index < 0 || index == node.length() - 1)
            return null;
        return node.substring(index + 1, node.length());
    }
    
    /**
     * Returns the full path of the given node, but if it's the root, returns the empty string.
     * @param node A tree-node's full path.
     * @return The full path of the given node, but if it's the root, returns the empty string.
     */
    public static String correctForRoot(String node) {
        return node.equals("/") ? "" : node;
    }
    
    /**
     * Returns an {@link Iterable} over a node's path components.
     * @param node A tree-node's full path.
     * @return An {@link Iterable} over a node's path components.
     */
    public static Iterable<String> iterate(final String node) {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new Scanner(node).useDelimiter("/");
            }
        };
    }
    
    private DistributedTreeUtil() {
    }
}
