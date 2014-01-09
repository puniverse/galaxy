/*
 * Galaxy
 * Copyright (c) 2012-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
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
