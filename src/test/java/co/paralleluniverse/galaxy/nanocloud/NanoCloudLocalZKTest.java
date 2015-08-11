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

import org.apache.curator.test.TestingServer;
import org.gridkit.nanocloud.Cloud;
import org.gridkit.vicluster.telecontrol.jvm.JvmProps;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static co.paralleluniverse.galaxy.test.GalaxyTestingUtils.startGlxServer;
import static org.junit.Assert.assertEquals;

public class NanoCloudLocalZKTest extends BaseCloudTest {
    public static final int CLIENT_PORT = 2181;
    private TestingServer testingServer;

    @Before
    public void setUp() throws Exception {
        testingServer = new TestingServer(CLIENT_PORT, new File("/tmp/zookeeper"));

        cloud.nodes(SERVER, PEER1, PEER2);

        setJvmArgs(cloud);
    }

    @After
    public void tearDown() throws IOException {
        testingServer.close();
    }

    @Test
    public void clusterAddTest() throws InterruptedException, ExecutionException {
        cloud.node(SERVER).submit(startGlxServer(SERVER_ZK_CFG, SERVER_PROPS));
        final boolean withNamaspace = false;
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_WITH_ZK_SERVER_CFG, withNamaspace));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_WITH_ZK_SERVER_CFG, withNamaspace)).get();
        assertEquals("inode's id larger than peer1", 2, largerID);
    }

    @Test
    public void clusterWithNamespaceAddTest() throws InterruptedException, ExecutionException {
        cloud.node(SERVER).submit(startGlxServer(SERVER_ZK_CFG, SERVER_WITH_ZK_NAMESPACE_PROPS));
        final boolean withNamaspace = true;
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_WITH_ZK_SERVER_CFG, withNamaspace));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_WITH_ZK_SERVER_CFG, withNamaspace)).get();
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
