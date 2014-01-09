/*
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
package co.paralleluniverse.common.monitoring;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.Date;
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
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;
import javax.management.timer.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 *
 * @author pron
 */
public abstract class PeriodicMonitor extends StandardEmitterMBean implements NotificationListener, NotificationEmitter, InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(PeriodicMonitor.class);
    private final String name;
    private boolean registered;
    private long lastCollectTime;
    private int notificationSequenceNumber;
    private WeakReference<Object> monitored;
    private boolean timerStarted;
    private int timerPeriod = 5000;
    private final Timer timer = new Timer();

    public PeriodicMonitor(Class mbeanInterface, String name) {
        super(mbeanInterface, true, new NotificationBroadcasterSupport());
        this.name = name;
        this.lastCollectTime = System.currentTimeMillis();
        this.monitored = null;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        collectAndResetCounters1();
        registerMBean();
    }

    @Override
    public void destroy() throws Exception {
        unregisterMBean();
    }

    public void setMonitoredObject(Object obj) {
        this.monitored = new WeakReference<Object>(obj);
    }

    private boolean isMonitoredObjectAlive() {
        return monitored == null || monitored.get() != null;
    }

    private void registerMBean() {
        try {
            LOG.info("Registering MBean {}", name);
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName mxbeanName = new ObjectName(name);
            mbs.registerMBean(this, mxbeanName);

            timer.addNotificationListener(this, new NotificationFilter() {
                @Override
                public boolean isNotificationEnabled(Notification notification) {
                    return "tickTimer".equals(notification.getType());
                }
            }, null);
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
            if (registered) {
                LOG.info("Unregistering MBean {}", name);
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName(name));
                timer.stop();
            }
            this.registered = false;
        } catch (Exception e) {
            LOG.warn("Exception:", e);
        }
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        String[] types = new String[]{
            AttributeChangeNotification.ATTRIBUTE_CHANGE
        };
        String _name = AttributeChangeNotification.class.getName();
        String description = "An attribute of this MBean has changed";
        MBeanNotificationInfo info = new MBeanNotificationInfo(types, _name, description);
        return new MBeanNotificationInfo[]{info};
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        if ("tickTimer".equals(notification.getType())) {
            //assert Objects.equal(handback, name);
            refresh();
        }
    }

    public void refresh() {
        collectAndResetCounters1();
        //Notification n = new AttributeChangeNotification(this, notificationSequenceNumber++, System.currentTimeMillis(), "CacheInfo changed", "PerfInfo", newValue.getClass().getName(), null, newValue);
        Notification n = new AttributeChangeNotification(this, notificationSequenceNumber++, System.currentTimeMillis(), "Info changed", "", null, null, null);
        sendNotification(n);
    }

    protected long getMillisSinceLastCollect() {
        return System.currentTimeMillis() - lastCollectTime;
    }
    
    public synchronized int getTimerPeriod() {
        return timerPeriod;
    }

    public synchronized void setTimerPeriod(int timerPeriod) {
        if (timerPeriod != this.timerPeriod) {
            this.timerPeriod = timerPeriod;
            if (timerStarted) {
                stopUpdates();
                startUpdates();
            }
        }
    }

    public synchronized boolean isUpdates() {
        return timerStarted;
    }

    public synchronized void setUpdates(boolean value) {
        if (value == timerStarted)
            return;
        if (!timerStarted)
            startUpdates();
        else
            stopUpdates();
    }

    public synchronized void startUpdates() {
        if (!timerStarted) {
            timer.addNotification("tickTimer", null, null, new Date(System.currentTimeMillis()), timerPeriod);
            this.timerStarted = true;
            timer.start();
        }
    }

    public synchronized void stopUpdates() {
        if (timerStarted) {
            try {
                timer.removeNotifications("tickTimer");
                this.timerStarted = false;
            } catch (InstanceNotFoundException ex) {
            }
            timer.stop();
        }
    }

    private void collectAndResetCounters1() {
        if (registered) {
            if (!isMonitoredObjectAlive())
                unregisterMBean();
            else {
                collectAndResetCounters();
                lastCollectTime = System.currentTimeMillis();
                return;
            }
        }
        resetCounters();
        lastCollectTime = System.currentTimeMillis();
    }

    protected void initCounters() {
    }

    protected abstract void collectAndResetCounters();

    protected abstract void resetCounters();
}
