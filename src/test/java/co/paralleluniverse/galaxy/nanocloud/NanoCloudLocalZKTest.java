package co.paralleluniverse.galaxy.nanocloud;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.gridkit.vicluster.ViManager;
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

    private static void setJvmArgs(final ViManager cloud) {
        String[] copyEnv = {
            "log4j.configurationFile",
        };
        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-ea");
//        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-javaagent:" + System.getProperty("co.paralleluniverse.quasarJar"));
        for (String string : copyEnv)
            props = props.addJvmArg("-D" + string + "=" + System.getProperty(string));
    }


    private static ServerCnxnFactory startZookeeper(final String configResource, final String dataDirName) throws IOException, QuorumPeerConfig.ConfigException {
        ServerConfig sc = new ServerConfig();
        sc.parse(pathToResource(configResource));
        final File dataDir = new File(dataDirName);
        FileUtils.deleteDirectory(dataDir);
        dataDir.mkdirs();
        FileTxnSnapLog txnLog = null;
        try {
            ZooKeeperServer zkServer = new ZooKeeperServer();

            txnLog = new FileTxnSnapLog(new File(sc.getDataDir()), new File(
                    sc.getDataDir()));
            zkServer.setTxnLogFactory(txnLog);
            zkServer.setTickTime(sc.getTickTime());
            zkServer.setMinSessionTimeout(sc.getMinSessionTimeout());
            zkServer.setMaxSessionTimeout(sc.getMaxSessionTimeout());
            ServerCnxnFactory cnxnFactory = ServerCnxnFactory.createFactory();
            cnxnFactory.configure(sc.getClientPortAddress(),
                    sc.getMaxClientCnxns());
            cnxnFactory.startup(zkServer);
            return cnxnFactory;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (txnLog != null) {
                txnLog.close();
            }
        }
    }
}
