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

import static co.paralleluniverse.galaxy.test.GalaxyTestingUtils.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.gridkit.nanocloud.Cloud;
import org.gridkit.vicluster.telecontrol.jvm.JvmProps;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class NanoCloudLocalZKTest extends BaseCloudTest {
    private ServerCnxnFactory zkCnxnFactory;

    @Before
    public void setUp() throws InterruptedException, QuorumPeerConfig.ConfigException, IOException {
        zkCnxnFactory = startZookeeper("config/zoo.cfg", "/tmp/zookeeper/");
        cloud = createLocalCloud();
    }

    @After
    public void tearDown() throws InterruptedException {
        zkCnxnFactory.shutdown();
    }

    @Test
    public void clusterAddTest() throws InterruptedException, ExecutionException {
        cloud.nodes(SERVER, PEER1, PEER2);
        setJvmArgs(cloud);
        cloud.node(SERVER).submit(startGlxServer(SERVER_ZK_CFG, SERVER_PROPS));
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_WITH_ZK_SERVER_CFG));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_WITH_ZK_SERVER_CFG)).get();
        assertEquals("inode's id larger than peer1", 2, largerID);
    }

    private static void setJvmArgs(Cloud cloud) {
        String[] copyEnv = {
            "log4j.configurationFile",
        };
        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-ea");
//        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-javaagent:" + System.getProperty("co.paralleluniverse.quasarJar"));
        for (String string : copyEnv)
            props = props.addJvmArg("-D" + string + "=" + System.getProperty(string));
    }
}
