package co.paralleluniverse.galaxy.nanocloud;

import co.paralleluniverse.galaxy.Grid;
import co.paralleluniverse.galaxy.Server;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import com.google.common.util.concurrent.SettableFuture;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.gridkit.nanocloud.CloudFactory;

import org.gridkit.vicluster.ViManager;
import org.gridkit.vicluster.ViNode;
import org.gridkit.vicluster.ViNodeSet;
import org.gridkit.vicluster.ViProps;
import org.junit.After;

public abstract class BaseCloudTest {

    protected ViManager cloud;

    @After
    public void recycleCloud() {
        if (cloud != null) {
            cloud.shutdown();
        }
    }

    public static ViManager createLocalCloud() {
        ViManager vim = CloudFactory.createCloud();
        ViProps.at(vim.node("**")).setLocalType();
        return vim;
    }

    public static String pathToResource(final String name) {
        return ClassLoader.getSystemClassLoader().getResource(name).getPath();
    }

    public static Runnable startGlxServer() {
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

    public static Callable<Short> startWaitForLargerPeer(final int peerNum, final String configpeerxml) {
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
    static final String PEER_WITH_SERVER_CFG = "config/peerWithServer.xml";

}
