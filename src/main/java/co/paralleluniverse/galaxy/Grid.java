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
package co.paralleluniverse.galaxy;

import co.paralleluniverse.common.spring.Service;
import co.paralleluniverse.common.spring.SpringContainerHelper;
import co.paralleluniverse.galaxy.core.AbstractCluster;
import co.paralleluniverse.galaxy.core.Backup;
import co.paralleluniverse.galaxy.core.Cache;
import co.paralleluniverse.galaxy.core.ClusterMonitor;
import co.paralleluniverse.galaxy.core.StoreImpl;
import java.util.Properties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

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
        return getInstance(configFile, (Object) propertiesFile);
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
        return getInstance(configFile, (Object) properties);
    }

    private static Grid getInstance(String configFile, Object properties) throws InterruptedException {
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

    private Grid(String configFile, Object properties) throws InterruptedException {
        if (configFile == null)
            configFile = System.getProperty("co.paralleluniverse.galaxy.configFile");
        if (properties == null)
            properties = System.getProperty("co.paralleluniverse.galaxy.propertiesFile");
        this.context = SpringContainerHelper.createContext("co.paralleluniverse.galaxy",
                configFile != null ? new FileSystemResource(configFile) : new ClassPathResource("galaxy.xml"),
                properties instanceof String ? new FileSystemResource((String) properties) : properties,
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
