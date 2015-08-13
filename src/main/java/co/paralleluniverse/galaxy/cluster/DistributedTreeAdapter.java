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

import java.util.List;

/**
 *
 */
public class DistributedTreeAdapter implements DistributedTree {
    private final DistributedTree tree;

    public DistributedTreeAdapter(DistributedTree tree) {
        this.tree = tree;
    }

    @Override
    public void addListener(String node, Listener listener) {
        tree.addListener(node, listener);
    }

    @Override
    public void removeListener(String node, Listener listener) {
        tree.removeListener(node, listener);
    }

    @Override
    public void create(String node, boolean ephemeral) {
        tree.create(node, ephemeral);
    }

    @Override
    public void createEphemeralOrdered(String node) {
        tree.createEphemeralOrdered(node);
    }

    @Override
    public void set(String node, byte[] data) {
        tree.set(node, data);
    }

    @Override
    public void delete(String node) {
        tree.delete(node);
    }

    @Override
    public void flush() {
        tree.flush();
    }
    
    @Override
    public boolean exists(String node) {
        return tree.exists(node);
    }

    @Override
    public byte[] get(String node) {
        return tree.get(node);
    }

    @Override
    public List<String> getChildren(String node) {
        return tree.getChildren(node);
    }

    @Override
    public void print(String node, java.io.PrintStream out) {
        tree.print(node, out);
    }

    @Override
    public void shutdown() {
        tree.shutdown();
    }
}
