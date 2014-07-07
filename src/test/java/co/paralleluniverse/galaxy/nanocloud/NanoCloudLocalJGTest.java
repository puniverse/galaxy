package co.paralleluniverse.galaxy.nanocloud;

import static co.paralleluniverse.galaxy.testing.GalaxyTestingUtils.*;
import java.util.concurrent.ExecutionException;
import org.gridkit.vicluster.ViManager;
import org.gridkit.vicluster.telecontrol.jvm.JvmProps;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Test;


@Ignore // travis doesn't support mmulticast
public class NanoCloudLocalJGTest extends BaseCloudTest {
    @Test
    public void jgroupsNoServerTest() throws InterruptedException, ExecutionException {
        cloud.nodes(PEER1, PEER2);
        setJvmArgs(cloud);
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_NO_SERVER_CFG));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_NO_SERVER_CFG)).get();
        assertEquals("node's id larger than peer1", 2, largerID);
    }

    @Test
    public void jgroupsWithServerTest() throws InterruptedException, ExecutionException {
        cloud.nodes(SERVER, PEER1, PEER2);
        setJvmArgs(cloud);
        cloud.node(SERVER).submit(startGlxServer(SERVER_JG_CFG, SERVER_PROPS));
        Thread.sleep(10000);
        cloud.node(PEER2).submit(startWaitForLargerPeer(2, PEER_WITH_JG_SERVER_CFG));
        int largerID = cloud.node(PEER1).submit(startWaitForLargerPeer(1, PEER_WITH_JG_SERVER_CFG)).get();
        assertEquals("inode's id larger than peer1", 2, largerID);
    }

    private static void setJvmArgs(final ViManager cloud) {
        String[] copyEnv = {
            "log4j.configurationFile",
            "jgroups.bind_addr",};
        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-ea");
//        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-javaagent:" + System.getProperty("co.paralleluniverse.quasarJar"));
        for (String string : copyEnv)
            props = props.addJvmArg("-D" + string + "=" + System.getProperty(string));
    }
    public interface MulticastTests {}

}
