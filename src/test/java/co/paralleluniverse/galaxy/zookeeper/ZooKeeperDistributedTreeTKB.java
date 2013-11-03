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
package co.paralleluniverse.galaxy.zookeeper;

import co.paralleluniverse.galaxy.cluster.DistributedTreeTKB;
import java.util.Random;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 *
 * @author pron
 */
public class ZooKeeperDistributedTreeTKB {
    public static void main(String[] args) throws Exception {
        final CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1:2181", 1500, 1000, new ExponentialBackoffRetry(20, 20));
        client.start();
        try {
            Random rand = new Random();

            final String me = "node-" + Long.toHexString(rand.nextLong());
            final ZooKeeperDistributedTree tree = new ZooKeeperDistributedTree(client);
            DistributedTreeTKB tkb = new DistributedTreeTKB(tree, me);
            
            tkb.run();
        } finally {
            client.close();
        }
    }
}
