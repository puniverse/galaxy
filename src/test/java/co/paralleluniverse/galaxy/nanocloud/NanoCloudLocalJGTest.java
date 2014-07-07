package co.paralleluniverse.galaxy.nanocloud;

import java.util.concurrent.ExecutionException;
import org.gridkit.vicluster.ViManager;
import org.gridkit.vicluster.telecontrol.jvm.JvmProps;
import static org.junit.Assert.*;
import org.junit.Test;

public class NanoCloudLocalJGTest extends BaseCloudTest {
    @Test
    public void clusterAddTest() throws InterruptedException, ExecutionException {
        cloud = createLocalCloud();
        cloud.nodes(PEER1, PEER2);
        setJvmArgs(cloud);
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_NO_SERVER_CFG));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_NO_SERVER_CFG)).get();
        assertEquals("inode's id larger than peer1", 2, largerID);
    }

    private static void setJvmArgs(final ViManager cloud) {
        String[] copyEnv = {
            "log4j.configurationFile", 
            "jgroups.bind_addr",
        };
        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-ea");
//        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-javaagent:" + System.getProperty("co.paralleluniverse.quasarJar"));
        for (String string : copyEnv)
            props = props.addJvmArg("-D" + string + "=" + System.getProperty(string));
    }
}
