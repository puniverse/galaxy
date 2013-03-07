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
