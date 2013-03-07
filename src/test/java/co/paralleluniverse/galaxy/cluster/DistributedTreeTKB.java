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

import co.paralleluniverse.galaxy.cluster.DistributedTree.Listener;
import com.google.common.base.Charsets;

/**
 *
 * @author pron
 */
public class DistributedTreeTKB {
    private final DistributedTree tree;
    private final String name;
    
    public DistributedTreeTKB(DistributedTree tree, String name) {
        this.tree = tree;
        this.name = name;
    }

    public void run() throws Exception {
        System.out.println("===== I AM " + name + " ========");
        tree.print("/", System.out);
        System.out.println("================================");
        addListener("/nodes");
        addListener("/a");
        addListener("/a/b");
        put("/a/b/c", false, "kjkhkjh");
        put("/a/b/c1", false, null);
        put("/a/b/c2", false, "dfdfs");
        put("/a/b1/chat", false, null);
        put("/a/b1/chat2", false, "lhjkll");
        put("/a/b1/chat5", false, "ddddd");
        tree.print("/", System.out);
        put("/a/b/c", false, "1111111");
        tree.delete("/a/b");

        tree.createEphemeralOrdered("/nodes/" + name);
        tree.create("/nodes/" + name + "/x", true);
        tree.set("/nodes/" + name + "/x", serialize("I am " + name));
        tree.create("/nodes/" + name + "/1", true);
        tree.set("/nodes/" + name + "/1", serialize("xxx " + name));
        tree.set("/nodes/" + name + "/1", serialize("bbb " + name));
        tree.create("/nodes/" + name + "/2", true);
        tree.set("/nodes/" + name + "/2", serialize("yyy " + name));
        tree.delete("/nodes/" + name + "/1");
        
        while (true) {
            tree.print("/", System.out);
            Thread.sleep(5000);
        }
    }

    private void put(String node, boolean ephemeral, String data) throws Exception {
        tree.create(node, ephemeral);
        tree.set(node, data != null ? serialize(data) : null);
    }

    static byte[] serialize(String object) {
        return object.getBytes(Charsets.UTF_8);
    }

    static String deserialize(byte[] array) {
        return new String(array, Charsets.UTF_8);
    }
    
    private void addListener(final String node) {
        tree.addListener(node, new Listener() {

            @Override
            public void nodeAdded(String node) {
                event("ADDED", node);
            }

            @Override
            public void nodeUpdated(String node) {
                event("UPDATED", node);
            }

            @Override
            public void nodeDeleted(String node) {
                event("DELETED", node);
//                System.err.println("XXXX DEL " + node);
//                Thread.dumpStack();
            }

            @Override
            public void nodeChildAdded(String node, String child) {
                event("ADDED", node, child);
            }

            @Override
            public void nodeChildUpdated(String node, String child) {
                event("UPDATED", node, child);
            }

            @Override
            public void nodeChildDeleted(String node, String child) {
                event("DELETED", node, child);
            }
            
            private void event(String event, String node) {
                System.out.println("LSTN " + node + ": " + event);
            }
            private void event(String event, String node, String child) {
                System.out.println("LSTN " + node + " - " + child + ": " + event);
            }
        });
    }
}
