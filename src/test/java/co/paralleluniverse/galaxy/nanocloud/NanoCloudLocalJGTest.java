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
import java.util.concurrent.ExecutionException;
import org.gridkit.nanocloud.Cloud;
import org.gridkit.vicluster.telecontrol.jvm.JvmProps;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.junit.Ignore;
import org.junit.Test;

@Ignore // doesn't work yet (nanocloud related)
public class NanoCloudLocalJGTest extends BaseCloudTest {
    @Test
    public void jgroupsNoServerTest() throws InterruptedException, ExecutionException {
        assumeTrue(!isCI());

        cloud.nodes(PEER1, PEER2);
        setJvmArgs(cloud);
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_NO_SERVER_CFG));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_NO_SERVER_CFG)).get();
        assertEquals("node's id larger than peer1", 2, largerID);
    }

    @Test
    public void jgroupsWithServerTest() throws InterruptedException, ExecutionException {
        assumeTrue(!isCI());

        cloud.nodes(SERVER, PEER1, PEER2);
        setJvmArgs(cloud);
        cloud.node(SERVER).submit(startGlxServer(SERVER_JG_CFG, SERVER_PROPS));
        Thread.sleep(10000);
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_WITH_JG_SERVER_CFG));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_WITH_JG_SERVER_CFG)).get();
        assertEquals("inode's id larger than peer1", 2, largerID);
    }

    private static void setJvmArgs(Cloud cloud) {
        String[] copyEnv = {
            "log4j.configurationFile",
            "jgroups.bind_addr",};
        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-ea");
//        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-javaagent:" + System.getProperty("co.paralleluniverse.quasarJar"));
        for (String string : copyEnv)
            props = props.addJvmArg("-D" + string + "=" + System.getProperty(string));
    }

    public interface MulticastTests {
    }
}
