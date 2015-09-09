/*
 * Galaxy
 * Copyright (C) 2012-2014 Parallel Universe Software Co.
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
package co.paralleluniverse.galaxy.nanocloud;

import co.paralleluniverse.galaxy.Grid;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import com.google.common.util.concurrent.SettableFuture;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.gridkit.nanocloud.CloudFactory;
import org.gridkit.vicluster.ViProps;
import org.junit.After;
import org.junit.Before;
import static co.paralleluniverse.galaxy.test.GalaxyTestingUtils.*;
import org.gridkit.nanocloud.Cloud;

public abstract class BaseCloudTest {
    protected Cloud cloud;

    @Before
    public void defineCloud() {
        cloud = createLocalCloud();
    }

    @After
    public void recycleCloud() {
        if (cloud != null) {
            cloud.shutdown();
        }
    }

    public static Cloud createLocalCloud() {
        Cloud cloud = CloudFactory.createCloud();
        ViProps.at(cloud.node("**")).setLocalType();
        return cloud;
    }

    public static Callable<Short> startWaitForLargerPeer(final int peerNum, final String configpeerxml) {
        return startWaitForLargerPeer(peerNum, configpeerxml, false);
    }

    public static Callable<Short> startWaitForLargerPeer(final int peerNum, final String configpeerxml, final boolean withNamaspace) {
        return new Callable<Short>() {
            @Override
            public Short call() throws IOException, InterruptedException, ExecutionException {
                System.out.println("STARTING PEER " + peerNum);
                Properties props = new Properties();
                props.load(pathToResource(SERVER_PROPS).openStream());
                props.setProperty("galaxy.nodeId", Integer.toString(peerNum));
                props.setProperty("galaxy.port", Integer.toString(7050 + peerNum));
                props.setProperty("galaxy.slave_port", Integer.toString(8050 + peerNum));
                props.setProperty("galaxy.multicast.address", "225.0.0.1");
                props.setProperty("galaxy.multicast.port", Integer.toString(7050));
                if (withNamaspace) {
                    props.setProperty("galaxy.zkNamespace", "tests");
                }

                final Grid grid = Grid.getInstance(pathToResource(configpeerxml), props);
                grid.goOnline();
                final SettableFuture<Short> future = SettableFuture.create();
                grid.cluster().addNodeChangeListener(new NodeChangeListener() {
                    @Override
                    public void nodeAdded(short id) {
                        if (id > grid.cluster().getMyNodeId()) {
                            System.out.println("Larger peer added !!");
                            future.set(id);
                        }
                    }

                    @Override
                    public void nodeSwitched(short id) {
                    }

                    @Override
                    public void nodeRemoved(short id) {
                    }
                });
                for (Short node : grid.cluster().getNodes()) {
                    if (node > grid.cluster().getMyNodeId()) {
                        System.out.println("Larger peer have already up !!");
                        return node;
                    }
                }
                return future.get();
            }
        };
    }

    static final String SERVER = "server";
    static final String PEER2 = "peer2";
    static final String PEER1 = "peer1";
    static final String PEER_NO_SERVER_CFG = "config/peerNoServer.xml";
    static final String PEER_WITH_ZK_SERVER_CFG = "config/peerWithZKServer.xml";
    static final String PEER_WITH_JG_SERVER_CFG = "config/peerWithJGServer.xml";
    static final String SERVER_PROPS = "config/server.properties";
    static final String SERVER_WITH_ZK_NAMESPACE_PROPS = "config/serverWithZkNamespace.properties";
    static final String SERVER_ZK_CFG = "config/serverZK.xml";
    static final String SERVER_JG_CFG = "config/serverJG.xml";
}
