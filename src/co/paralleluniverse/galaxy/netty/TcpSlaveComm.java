/*
 * Galaxy
 * Copyright (C) 2012 Parallel Universe Software Co.
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
package co.paralleluniverse.galaxy.netty;

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.core.Backup;
import co.paralleluniverse.galaxy.core.ClusterService;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.NodeNotFoundException;
import co.paralleluniverse.galaxy.core.SlaveComm;
import java.beans.ConstructorProperties;
import java.util.concurrent.ThreadPoolExecutor;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 *
 * @author pron
 */
public class TcpSlaveComm extends ClusterService implements SlaveComm {
    private static final Logger LOG = LoggerFactory.getLogger(TcpSlaveComm.class);
    private final TcpSlaveClientComm client;
    private final TcpSlaveServerComm server;
    
    @ConstructorProperties({"name", "cluster", "port"})
    TcpSlaveComm(String name, final Cluster cluster, int port) throws Exception {
        super(name, cluster);
        this.server = new TcpSlaveServerComm(name + "Server", cluster, port);
        this.client = new TcpSlaveClientComm(name + "Client", cluster);
    }
    
    @Override
    protected void init() throws Exception {
        super.init();

        server.init();
        client.init();
    }

    @ManagedAttribute
    public int getPort() {
        return server.getPort();
    }
    
    @Override
    protected void postInit() throws Exception {
        server.postInit();
        client.postInit();
        setReady(true);
        
        super.postInit();
    }

    @Override
    protected void available(boolean value) {
        super.available(value);
        server.available(value);
        client.available(value);
    }

    @Override
    public void setBackup(Backup backup) {
        server.setBackup(backup);
        client.setBackup(backup);
    }
    
    public void setBossExecutor(ThreadPoolExecutor executor) {
        assertDuringInitialization();
        server.setBossExecutor(executor);
        client.setBossExecutor(executor);
    }

    public void setWorkerExecutor(ThreadPoolExecutor executor) {
        assertDuringInitialization();
        server.setWorkerExecutor(executor);
        client.setWorkerExecutor(executor);
    }
    
    public void setReceiveExecutor(OrderedMemoryAwareThreadPoolExecutor executor) {
        assertDuringInitialization();
        server.setReceiveExecutor(executor);
        client.setReceiveExecutor(executor);
    }
    
    @Override
    protected void start(boolean master) {
    }

    @Override
    public boolean send(Message message) throws NodeNotFoundException {
        switch (message.getType()) {
            case BACKUP_PACKET:
                if (!getCluster().isMaster()) {
                    LOG.warn("Backup message sent while slave: {}", message);
                    return false;
                }
                return server.send(message);
            case BACKUP_PACKETACK:
                if (getCluster().isMaster()) {
                    LOG.warn("Backup ack message sent while master: {}", message);
                    return false;
                }
                client.send(message);
                break;
            case INV:
                if (!getCluster().isMaster()) {
                    LOG.warn("Invalidate message sent while slave: {}", message);
                    return false;
                }
                return server.send(message);
            case INVACK:
                if (getCluster().isMaster()) {
                    LOG.warn("Invalidate ack message sent while master: {}", message);
                    return false;
                }
                client.send(message);
                break;
            default:
                LOG.warn("Unrecognized message: {}", message);
        }
        return false;
    }
}
