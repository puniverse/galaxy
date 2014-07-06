package co.paralleluniverse.galaxy.nanocloud;

import co.paralleluniverse.galaxy.Grid;
import co.paralleluniverse.galaxy.Server;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;
import org.gridkit.nanocloud.CloudFactory;
import org.gridkit.vicluster.ViManager;
import org.gridkit.vicluster.ViProps;
import org.gridkit.vicluster.telecontrol.jvm.JvmProps;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class NanoCloudLocalTest extends BaseCloudTest {
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
        cloud.node(SERVER).submit(createServer());
        cloud.node(PEER2).submit(createWaitForLargerPeer(2));
        int largerPeer = cloud.node(PEER1).submit(createWaitForLargerPeer(1)).get();
        System.out.println("PEER1 returned " + largerPeer);
        assertEquals("id of node larger than peer1", 2, largerPeer);
    }

    private static Runnable createServer() {
        return new Runnable() {
            @Override
            public void run() {
                Server.start(pathToResource("config/server.xml"), pathToResource("config/server.properties"));
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ex) {
                    System.out.println("Interrupted.....");
                }
            }
        };
    }

    private static Callable<Short> createWaitForLargerPeer(final int peerNum) {
        return new Callable<Short>() {
            @Override
            public Short call() throws IOException, InterruptedException, ExecutionException {
                System.out.println("STARTING PEER " + peerNum);
                Properties props = new Properties();
                props.load(new FileInputStream(pathToResource("config/server.properties")));
                props.setProperty("galaxy.nodeId", Integer.toString(peerNum));
                props.setProperty("galaxy.port", Integer.toString(7050 + peerNum));
                props.setProperty("galaxy.slave_port", Integer.toString(8050 + peerNum));
                props.setProperty("galaxy.multicast.address", "225.0.0.1");
                props.setProperty("galaxy.multicast.port", Integer.toString(7050));

                final Grid grid = Grid.getInstance(pathToResource("config/peer.xml"), props);
                grid.goOnline();
                final SettableFuture<Short> future = SettableFuture.create();
                grid.cluster().addNodeChangeListener(new NodeChangeListener() {
                    @Override
                    public void nodeAdded(short id) {
                        if (id > grid.cluster().getMyNodeId())
                            future.set(id);
                    }

                    @Override
                    public void nodeSwitched(short id) {
                    }

                    @Override
                    public void nodeRemoved(short id) {
                    }
                });
                for (Short node : grid.cluster().getNodes()) {
                    if (node > grid.cluster().getMyNodeId())
                        return node;
                }
                return future.get();
            }
        };
    }

    private static ViManager createLocalCloud() {
        ViManager vim = CloudFactory.createCloud();
        ViProps.at(vim.node("**")).setLocalType();
        return vim;
    }

    private static void setJvmArgs(final ViManager cloud) {
        String[] copyEnv = {
            "log4j.configurationFile", //            "jgroups.bind_addr",
        //            "galaxy.multicast.address",
        //            "galaxy.multicast.port",
        //            "co.paralleluniverse.galaxy.configFile",
        //            "co.paralleluniverse.galaxy.autoGoOnline"
        };
        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-ea");
//        JvmProps props = JvmProps.at(cloud.node("**")).addJvmArg("-javaagent:" + System.getProperty("co.paralleluniverse.quasarJar"));
        //"log4j.configurationFile", "log4j.xml"
        for (String string : copyEnv)
            props = props.addJvmArg("-D" + string + "=" + System.getProperty(string));
    }

    public static String pathToResource(final String name) {
        return ClassLoader.getSystemClassLoader().getResource(name).getPath();
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

    private static final String PEER2 = "peer2";
    private static final String PEER1 = "peer1";
    private static final String SERVER = "server";
}
