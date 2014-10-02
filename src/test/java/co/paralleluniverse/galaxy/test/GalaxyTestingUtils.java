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
package co.paralleluniverse.galaxy.test;

import co.paralleluniverse.galaxy.Server;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.persistence.FileTxnSnapLog;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

public class GalaxyTestingUtils {
    static final boolean ci = (isEnvTrue("CI") || isEnvTrue("CONTINUOUS_INTEGRATION") || isEnvTrue("TRAVIS"));

    public static boolean isCI() {
        return ci;
    }

    public static String pathToResource(final String name) {
        return ClassLoader.getSystemClassLoader().getResource(name).getPath();
    }

    public static Runnable startGlxServer(final String serverConfig, final String serverProps) {
        return new Runnable() {
            @Override
            public void run() {
                Server.start(pathToResource(serverConfig), pathToResource(serverProps));
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException ex) {
                    System.out.println("Interrupted.....");
                }
            }
        };
    }

    public static void deleteDir(String path) throws IOException {
        if (new File(path).exists())
            Files.walkFileTree(Paths.get(path), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
    }

    public static ServerCnxnFactory startZookeeper(final String configResource, final String dataDirName) throws IOException, QuorumPeerConfig.ConfigException {
        ServerConfig sc = new ServerConfig();
        sc.parse(pathToResource(configResource));
        deleteDir(dataDirName);
        new File(dataDirName).mkdirs();
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

    private static boolean isEnvTrue(String envVar) {
        final String ev = System.getenv(envVar);
        if (ev == null)
            return false;
        try {
            return Boolean.parseBoolean(ev);
        } catch (Exception e) {
            return false;
        }
    }
}
