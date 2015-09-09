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
package co.paralleluniverse.galaxy;

import co.paralleluniverse.common.spring.Service;
import co.paralleluniverse.common.spring.SpringContainerHelper;
import co.paralleluniverse.galaxy.core.*;
import co.paralleluniverse.galaxy.core.Cache;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.net.URL;
import java.util.Properties;

/**
 * The central access point to the grid's services (the word <i>grid</i> describes the collection distributed of software provided
 * in the physical node <i>cluster</i>).
 */
public class Grid {
    private static Grid instance = null;
    private final static Object lock = new Object();

    /**
     * Retrieves the named grid instance, as specified in the default configuration file.
     *
     * @return The grid instance.
     */
    public static Grid getInstance() throws InterruptedException {
        return getInstance(null, (Object) null);
    }

    /**
     * Retrieves the grid instance, as defined in the given configuration file.
     *
     * @param configFile The name of the configuration file containing the grid definition.
     * @param propertiesFile The name of the properties file containing the grid's properties. You may, of course use Spring's {@code <context:property-placeholder location="classpath:com/foo/bar.properties"/>}
     * but this parameter is helpful when you want to use the same xml configuration with different properties for different
     * instances.
     * @return The grid instance.
     */
    public static Grid getInstance(String configFile, String propertiesFile) throws InterruptedException {
        return getInstance(configFile == null ? null : new FileSystemResource(configFile), propertiesFile);
    }

    /**
     * Retrieves the grid instance, as defined in the given configuration file.
     *
     * @param configFile The name of the configuration file containing the grid definition.
     * @param properties A {@link Properties Properties} object containing the grid's properties to be injected into placeholders
     * in the configuration file.
     * This parameter is helpful when you want to use the same xml configuration with different properties for different
     * instances.
     * @return The grid instance.
     */
    public static Grid getInstance(String configFile, Properties properties) throws InterruptedException {
        return getInstance(configFile == null ? null : new FileSystemResource(configFile), (Object) properties);
    }

    /**
     * Retrieves the grid instance, as defined in the given configuration file.
     *
     * @param configFile The name of the configuration file containing the grid definition.
     * @param propertiesFile The name of the properties file containing the grid's properties. You may, of course use Spring's {@code <context:property-placeholder location="classpath:com/foo/bar.properties"/>}
     * but this parameter is helpful when you want to use the same xml configuration with different properties for different
     * instances.
     * @return The grid instance.
     */
    public static Grid getInstance(URL configFile, URL propertiesFile) throws InterruptedException {
        return getInstance(new UrlResource(configFile), propertiesFile);
    }

    /**
     * Retrieves the grid instance, as defined in the given configuration file.
     *
     * @param configFile The name of the configuration file containing the grid definition.
     * @param properties A {@link Properties Properties} object containing the grid's properties to be injected into placeholders
     * in the configuration file.
     * This parameter is helpful when you want to use the same xml configuration with different properties for different
     * instances.
     * @return The grid instance.
     */
    public static Grid getInstance(URL configFile, Properties properties) throws InterruptedException {
        return getInstance(new UrlResource(configFile), properties);
    }

    private static Grid getInstance(Resource configFile, Object properties) throws InterruptedException {
        synchronized (lock) {
            Grid _instance = instance;
            if (_instance == null) {
                _instance = new Grid(configFile, properties);
                if (_instance == null)
                    throw new RuntimeException("Error while creating a grid instance from configuration file " + configFile + "!");
                instance = _instance;
                if (Boolean.getBoolean("co.paralleluniverse.galaxy.autoGoOnline"))
                    instance.goOnline();

            }
            return _instance;
        }
    }
    /////////////////////////////////////
    private final ApplicationContext context;
    private final Messenger messenger;
    private final Backup backup;
    private final Cache cache;
    private final Store store;
    private final Cluster cluster;
    private final ClusterMonitor clusterMonitor;

    private Grid(Resource configFile, Object properties) throws InterruptedException {
        if (configFile == null) {
            String propertyConfigFile = System.getProperty("co.paralleluniverse.galaxy.configFile");
            if (propertyConfigFile != null) {
                configFile = new FileSystemResource(propertyConfigFile);
            } else {
                configFile = new ClassPathResource("galaxy.xml");
            }
        }
        if (properties == null)
            properties = System.getProperty("co.paralleluniverse.galaxy.propertiesFile");
        if (properties instanceof String)
            properties = new FileSystemResource((String) properties);
        if (properties instanceof URL)
            properties = new UrlResource((URL)properties);

        this.context = SpringContainerHelper.createContext("co.paralleluniverse.galaxy",
                configFile,
                properties,
                new BeanFactoryPostProcessor() {
                    @Override
                    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory1) throws BeansException {
                        final DefaultListableBeanFactory beanFactory = ((DefaultListableBeanFactory) beanFactory1);

                        // messenger
//                        BeanDefinition messengerBeanDefinition = SpringContainerHelper.defineBean(
//                                MessengerImpl.class,
//                                SpringContainerHelper.constructorArgs("messenger", new RuntimeBeanReference("cache")),
//                                null);
//                        messengerBeanDefinition.setDependsOn(new String[]{"cache"});
//                        beanFactory.registerBeanDefinition("messenger", messengerBeanDefinition);

                        //beanFactory.registerSingleton(name, object);
                    }
                });
        this.cluster = context.getBean("cluster", Cluster.class);
        this.clusterMonitor = new ClusterMonitor(cluster);

        this.backup = context.getBean("backup", Backup.class);

        this.cache = context.getBean("cache", Cache.class);
        this.store = new StoreImpl(cache);
        this.messenger = context.getBean("messenger", Messenger.class); // new MessengerImpl(cache);
    }

    /**
     * Returns the grid's distributed data-store service.
     *
     * @return The grid's distributed data-store service.
     */
    public Store store() {
        return store;
    }

    /**
     * Returns the grid's messaging service.
     *
     * @return The grid's messaging service.
     */
    public Messenger messenger() {
        return messenger;
    }

    /**
     * Returns the grid's cluster management and node lifecycle service.
     *
     * @return The grid's cluster management and node lifecycle service.
     */
    public Cluster cluster() {
        return cluster;
    }

    /**
     * Makes this node a full participant in the cluster (rather than just an observer).
     */
    public void goOnline() throws InterruptedException {
        ((Service) backup).awaitAvailable();
        ((AbstractCluster) cluster).goOnline();
        cache.awaitAvailable();
    }
}
