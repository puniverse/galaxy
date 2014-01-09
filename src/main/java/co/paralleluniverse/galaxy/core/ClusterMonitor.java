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

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.LifecycleListener;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.cluster.SlaveConfigurationListener;
import co.paralleluniverse.galaxy.monitoring.ClusterMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.management.AttributeChangeNotification;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;

/**
 *
 * @author pron
 */
public class ClusterMonitor extends StandardEmitterMBean implements ClusterMXBean, LifecycleListener, NodeChangeListener, SlaveConfigurationListener, NotificationEmitter {
    private final String name;
    private final Cluster cluster;
    private boolean registered;
    private int notificationSequenceNumber;

    public ClusterMonitor(Cluster cluster) {
        super(ClusterMXBean.class, true, new NotificationBroadcasterSupport());
        this.name = "co.paralleluniverse.galaxy:type=Cluster";
        this.cluster = cluster;
        registerMBean();
    }

    private void registerMBean() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName mxbeanName = new ObjectName(name);
            mbs.registerMBean(this, mxbeanName);
            this.registered = true;
        } catch (InstanceAlreadyExistsException ex) {
            throw new RuntimeException(ex);
        } catch (MBeanRegistrationException ex) {
            throw new RuntimeException(ex);
        } catch (NotCompliantMBeanException ex) {
            throw new AssertionError(ex);
        } catch (MalformedObjectNameException ex) {
            throw new AssertionError(ex);
        }
    }

    public void unregisterMBean() {
        try {
            if (registered)
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName(name));
            this.registered = false;
        } catch (InstanceNotFoundException ex) {
            throw new RuntimeException(ex);
        } catch (MBeanRegistrationException ex) {
            throw new RuntimeException(ex);
        } catch (MalformedObjectNameException ex) {
            throw new AssertionError(ex);
        }
    }

    @Override
    public synchronized MBeanNotificationInfo[] getNotificationInfo() {
        String[] types = new String[]{
            AttributeChangeNotification.ATTRIBUTE_CHANGE
        };
        String _name = AttributeChangeNotification.class.getName();
        String description = "An attribute of this MBean has changed";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, _name, description);
        return new MBeanNotificationInfo[]{info};
    }

    @Override
    public SortedMap<String, String> getMyNodeInfo() {
        return toMap(cluster.getMyNodeInfo());
    }

    @Override
    public boolean isOnline() {
        return cluster.isOnline();
    }

    @Override
    public boolean isMaster() {
        return cluster.isMaster();
    }

    @Override
    public void shutdown() {
        cluster.goOffline();
    }

    @Override
    public boolean hasServer() {
        return cluster.hasServer();
    }

    @Override
    public List<SortedMap<String, String>> getNodes() {
        return toMapList(cluster.getMasters());
    }

    @Override
    public SortedMap<String, String> getMyMaster() {
        return toMap(cluster.getMyMaster());
    }

    @Override
    public List<SortedMap<String, String>> getMySlaves() {
        return toMapList(cluster.getMySlaves());
    }

    private List<SortedMap<String, String>> toMapList(Collection<NodeInfo> nis) {
        SortedSet<NodeInfo> nis1 = new TreeSet<NodeInfo>(new Comparator<NodeInfo>() {
            @Override
            public int compare(NodeInfo o1, NodeInfo o2) {
                return o1.getNodeId() - o2.getNodeId();
            }
        });
        nis1.addAll(nis);
        List<SortedMap<String, String>> list = new ArrayList<SortedMap<String, String>>(nis.size());
        for (NodeInfo ni : nis1)
            list.add(toMap(ni));
        return list;
    }

    private SortedMap<String, String> toMap(NodeInfo ni) {
        SortedMap<String, String> map = new TreeMap<String, String>();
        map.put("_id", Short.toString(ni.getNodeId()));
        map.put("_name", ni.getName());
        for (String p : ni.getProperties())
            map.put(p, ni.get(p).toString());
        return map;
    }

    private void notifyAttributeChange(String message, String attrName) {
        try {
            Object newValue = this.getAttribute(attrName);
            Notification n = new AttributeChangeNotification(this, notificationSequenceNumber++, System.currentTimeMillis(), message, attrName, null, null, newValue);
            sendNotification(n);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void joinedCluster() {
        notifyAttributeChange("Now joined", "online");
    }
    
    @Override
    public void offline() {
        notifyAttributeChange("Now offline", "online");
    }

    @Override
    public void online(boolean master) {
        notifyAttributeChange("Now online as " + (master ? "master" : "slave"), "online");
    }

    @Override
    public void switchToMaster() {
        notifyAttributeChange("Switched to master", "master");
    }

    @Override
    public void nodeAdded(short id) {
        notifyAttributeChange("Node " + id + " added", "nodes");
    }

    @Override
    public void nodeRemoved(short id) {
        notifyAttributeChange("Node " + id + " removed", "nodes");
    }

    @Override
    public void nodeSwitched(short id) {
        notifyAttributeChange("Node " + id + " switched", "nodes");
    }

    @Override
    public void newMaster(NodeInfo node) {
        notifyAttributeChange("New master", "myMaster");
    }

    @Override
    public void slaveAdded(NodeInfo node) {
        notifyAttributeChange("Slave added", "mySlaves");
    }

    @Override
    public void slaveRemoved(NodeInfo node) {
        notifyAttributeChange("Slave removed", "mySlaves");
    }
}
