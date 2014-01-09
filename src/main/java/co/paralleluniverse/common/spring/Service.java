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
package co.paralleluniverse.common.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedAttribute;

/**
 * A component that may have dependencies, and can signal its readiness to its dependents.
 */
public abstract class Service extends Component {
    private static final Logger LOG = LoggerFactory.getLogger(Service.class);
    private static final Object SERVICE_AVILABILITY_LOCK = new Object();
    private volatile boolean available; // is this service, and all dependencies, available
    private boolean dependenciesAvailable;
    private boolean ready;
    private boolean availabilityChanged;
    private final Set<Service> dependsOn = new CopyOnWriteArraySet<Service>(); // services I depend on
    private final Set<Service> dependedBy = new CopyOnWriteArraySet<Service>(); // services depending on me

    /**
     * Constructs a service with a given name.
     * @param name The service's name.
     */
    protected Service(String name) {
        super(name);
        this.ready = false;
    }

    void addDependedBy(Service service) {
        assertDuringInitialization();
        dependedBy.add(service);
    }

    void addDependsOn(Service service) {
        assertDuringInitialization();
        dependsOn.add(service);
    }

    /**
     * Adds a service this service depends on.
     * @param service The service this service depends on.
     */
    public void addDependency(Service service) {
        assertDuringInitialization();
        LOG.info("Adding a dependency on {} to {}", service.getName(), getName());
        addDependsOn(service);
        service.addDependedBy(this);
    }

    /**
     * Removes a dependency. This method <i>must</i> be called in the {@link #init() init()} method.
     * @param service 
     */
    protected void removeDependency(Service service) {
        assertDuringInitialization();
        LOG.info("Service {} is not dependent on {}", this, service);
        if (!dependsOn.remove(service))
            LOG.warn("Service {} asked to remove dependency on {}, but wasn't dependendent on it", getName(), service.getName());
    }

    @ManagedAttribute(currencyTimeLimit = 0, description = "Services that depend on this service")
    public List<String> getDependedBy() {
        final List<String> ds = new ArrayList<String>(dependedBy.size());
        for (Service s : dependedBy)
            ds.add(s.getName());
        return ds;
    }

    @ManagedAttribute(currencyTimeLimit = 0, description = "Services this service depends on")
    public List<String> getDependsOn() {
        List<String> ds = new ArrayList<String>(dependsOn.size());
        for (Service s : dependsOn)
            ds.add(s.getName());
        return ds;
    }

    @Override
    protected void init() throws Exception {
        super.init();
        LOG.info("Service {} dependencies: {}", getName(), getDependsOn());
    }

    
    @Override
    protected void postInit() throws Exception {
        super.postInit();
        checkDependenciesAvailability();
        checkAvailability();
        runAvailableMethod();
    }

    private boolean checkDependenciesAvailability() {
        synchronized (SERVICE_AVILABILITY_LOCK) {
            for (Service dep : dependsOn) {
                if (!dep.isAvailable()) {
                    dependenciesAvailable = false;
                    return false;
                }
            }
            dependenciesAvailable = true;
            return true;
        }
    }

    private boolean checkAvailability() {
        synchronized (SERVICE_AVILABILITY_LOCK) {
            boolean _available = ready && dependenciesAvailable;
            if (_available != available) {
                availabilityChanged = true;
                SERVICE_AVILABILITY_LOCK.notifyAll();
                this.available = _available;
                SERVICE_AVILABILITY_LOCK.notifyAll();
                LOG.info("SERVICE {} IS NOW {}", this, available ? "AVILABLE" : "NOT AVAILABLE");
                for (Service dependent : dependedBy)
                    dependent.dependencyChanged(this);
            }
        }

        return available;
    }

    private void runAvailableMethod() {
        // must run outside lock (or deadlock may occur, as available() is outside our control
        if (availabilityChanged) {
            available(this.available); 
            availabilityChanged = false;
            for (Service dependent : dependedBy)
                dependent.runAvailableMethod();
        }
    }

    private void dependencyChanged(Service service) {
        checkDependenciesAvailability();
        checkAvailability();
    }

    /**
     * Called when this service becomes available (it is ready and all its dependencies are available) or unavailable.
     * @param value {@code true} if this service is now available; {@code false} otherwise.
     */
    protected void available(boolean value) {
    }

    /**
     * Sets the readiness of this service. If this service is ready and all its dependencies are available, than this service becomes available.
     * @param ready
     */
    protected void setReady(boolean ready) {
        synchronized (SERVICE_AVILABILITY_LOCK) {
            LOG.info("Service {} is now {}", getName(), ready ? "READY" : "NOT READY");
            this.ready = ready;
            checkAvailability();
        }
        runAvailableMethod(); // call outside lock
    }

    /**
     * Availability changes between calls to this method are the responsibility of the user code, which must be able to handle a short interruption (e.g. comm services buffer their messages etc.).
     */
    public void awaitAvailable() throws InterruptedException {
        synchronized (SERVICE_AVILABILITY_LOCK) {
            while (!available) {
                LOG.info("Waiting for service {} to become available...", getName());
                LOG.debug(getDependencyGraph());
                SERVICE_AVILABILITY_LOCK.wait();
            }
        }
    }

    @ManagedAttribute(currencyTimeLimit = 0)
    public boolean isReady() {
        return ready;
    }

    @ManagedAttribute(currencyTimeLimit = 0)
    public boolean isAvailable() {
        return available;
    }
    
    @ManagedAttribute
    public String getDependencyGraph() {
        return getDependencyGraph(new StringBuilder(), 0).toString();
    }
    
    private StringBuilder getDependencyGraph(StringBuilder sb, int indent) {
        sb.append('\n');
        for(int i=0; i<indent*4; i++)
            sb.append(' ');
        sb.append(getName()).append(": ").append(isReady() ? "READY" : "NOT READY");
        for(Service s : dependsOn)
            s.getDependencyGraph(sb, indent + 1);
        return sb;
    }
}
