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
package co.paralleluniverse.common.monitoring;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 *
 * @author pron
 */
public abstract class Monitor<T>implements InitializingBean, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(Monitor.class);
    private final String name;
    private boolean registered;
    private final WeakReference<T> monitored;

    public Monitor(String name, T monitored) {
        this.name = name;
        this.monitored = new WeakReference<T>(monitored);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        registerMBean();
    }

    @Override
    public void destroy() throws Exception {
        unregisterMBean();
    }
    
    protected T getMonitored() {
        T m  = monitored.get();
        if(m == null)
            unregisterMBean();
        return m;
    }

    protected boolean isMonitoredObjectAlive() {
        return monitored == null || monitored.get() != null;
    }

    public void registerMBean() {
        try {
            LOG.info("Registering MBean {}", name);
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
            if (registered) {
                LOG.info("Unregistering MBean {}", name);
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName(name));
            }
            this.registered = false;
        } catch (Exception e) {
            LOG.warn("Exception:", e);
        }
    }

}
