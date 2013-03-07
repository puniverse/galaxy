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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.common.spring.Service;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.LifecycleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 * A {@link Component component} bound to the cluster's lifecycle.
 * @author pron
 */
public abstract class ClusterService extends Service implements LifecycleListener {
    private static final Logger LOG = LoggerFactory.getLogger(ClusterService.class);
    private final Cluster cluster;
    private final Object lifecycleLock = new Object();
    private volatile boolean online; // is this node online

    public ClusterService(String name, Cluster cluster) {
        super(name);
        this.cluster = cluster;
        cluster.addLifecycleListener(this);
        synchronized (lifecycleLock) {
            online = cluster.isOnline();
        }
    }

    @Override
    protected void postInit() throws Exception {
        if (online)
            startService();

        super.postInit();
    }

    private void startService() {
        final boolean master = cluster.isMaster();
        LOG.info("Starting service {} as {}", this, master ? "master" : "slave");
        start(master);
        LOG.info("Service {} started.", this);
    }

    /**
     * Return {@code true} if this node is online; {@code false} otherwise.
     * @return {@code true} if this node is online; {@code false} otherwise.
     */
    @ManagedAttribute(currencyTimeLimit = 0)
    protected boolean isOnline() {
        return online;
    }

    /**
     * Return {@code true} if this node is currently a master; {@code false} if it's a slave.
     * @return {@code true} if this node is currently a master; {@code false} if it's a slave.
     */
    protected boolean isMaster() {
        return cluster.isMaster();
    }

    /**
     * Called when this component has finished <i>and</i> has come online.
     * @param master {@code true} if this node is currently a master; {@code false} if it's a slave.
     */
    protected abstract void start(boolean master);

    @Override
    public void joinedCluster() {
        LOG.info("JOINED CLUSTER (Service {})", this);
    }

    @Override
    public final void online(boolean master) {
        synchronized (lifecycleLock) {
            if (!online) {
                online = true;
                if (isInitialized())
                    startService();
            }
        }
    }

    @Override
    public final void offline() {
        synchronized (lifecycleLock) {
            if (online) {
                online = false;
                try {
                    LOG.info("Shutting down component {}", getName());
                    shutdown();
                    LOG.info("Component {} destroyed", getName());
                } catch (RuntimeException e) {
                    LOG.warn("Exception while shutting down " + getName(), e);
                    throw e;
                }
            }
        }
    }

    @Override
    public void switchToMaster() {
        LOG.info("Switching service {} to master", this);
    }

    /**
     * Returns the cluster.
     * @return The cluster.
     */
    public Cluster getCluster() {
        return cluster;
    }
}
