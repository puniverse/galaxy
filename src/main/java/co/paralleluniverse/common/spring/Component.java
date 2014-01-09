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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * An object instance initialized by the Spring container.
 * 
 * Subclasses with methods annotated with {@code org.springframework.jmx.export.annotation.ManagedAttribute} must be public for the property to 
 * be properly exposed in the MBean.
 */
@ManagedResource
public abstract class Component implements InitializingBean, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(Component.class);
    private volatile boolean initialized;
    private final String name;

    /**
     * Constructs a component with a given name.
     *
     * @param name The component's name.
     */
    protected Component(String name) {
        this.initialized = false;
        this.name = name;
    }

    @ManagedAttribute(currencyTimeLimit = -1, description = "The component's class name")
    public String getType() {
        return getClass().getName();
    }

    @ManagedAttribute(currencyTimeLimit = -1, description = "The component's name")
    public String getName() {
        return name;
    }

    @Override
    public final void afterPropertiesSet() throws Exception {
        LOG.info("Initializing component {}", name);
        try {
            init();
            LOG.info("Component {} initialized", name);
        } catch (Exception e) {
            LOG.error("Exception while initializing " + name, e);
            throw e;
        }
    }

    @Override
    public final void destroy() throws Exception {
        LOG.info("Destroying component {}", name);
        try {
            shutdown();
            LOG.info("Component {} destroyed", name);
        } catch (Exception e) {
            LOG.warn("Exception while destroying " + name, e);
            throw e;
        }
    }

    @ManagedAttribute(currencyTimeLimit = 0, description = "Whether or not this component has been fully initialized")
    public boolean isInitialized() {
        return initialized;
    }

    protected void assertDuringInitialization() {
        if (initialized)
            throw new IllegalStateException("Method must only be called during bean initialization");
    }

    protected void assertInitialized() {
        if (!initialized)
            throw new IllegalStateException("Method must only be called after bean initialization");
    }

    /**
     * Called after bean properties have been set. If a derived class overrides this methid, it <b>must</b> call {@code super.init()}
     * <b>before</b> doing anything else in the method.
     */
    protected void init() throws Exception {
        assertDuringInitialization();
    }

    /**
     * Called after bean properties have been set, and init() called. If a derived class overrides this method, it
     * <b>must</b> call {@code super.postInit()} <b>after</b> doing anything else in the method.
     */
    protected void postInit() throws Exception {
        assertDuringInitialization();
        this.initialized = true;
    }

    /**
     * Called when the container is destroyed.
     */
    protected void shutdown() {
    }

    @Override
    public String toString() {
        return name + '(' + this.getClass().getName() + ')';
    }
}
