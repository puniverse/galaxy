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
}
