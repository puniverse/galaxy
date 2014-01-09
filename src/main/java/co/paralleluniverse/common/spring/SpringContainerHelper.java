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

import com.google.common.base.Throwables;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.support.InstantiationStrategy;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationJmxAttributeSource;
import org.springframework.jmx.export.assembler.MetadataMBeanInfoAssembler;

/**
 * Spring helper functions.
 *
 * @author pron
 */
public final class SpringContainerHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SpringContainerHelper.class);

    public static ConfigurableApplicationContext createContext(String defaultDomain, Resource xmlResource, Object properties, BeanFactoryPostProcessor beanFactoryPostProcessor) {
        LOG.info("JAVA: {} {}, {}", new Object[]{System.getProperty("java.runtime.name"), System.getProperty("java.runtime.version"), System.getProperty("java.vendor")});
        LOG.info("OS: {} {}, {}", new Object[]{System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch")});
        LOG.info("DIR: {}", System.getProperty("user.dir"));

        final DefaultListableBeanFactory beanFactory = createBeanFactory();
        final GenericApplicationContext context = new GenericApplicationContext(beanFactory);
        context.registerShutdownHook();

        final PropertyPlaceholderConfigurer propertyPlaceholderConfigurer = new PropertyPlaceholderConfigurer();
        propertyPlaceholderConfigurer.setSystemPropertiesMode(PropertyPlaceholderConfigurer.SYSTEM_PROPERTIES_MODE_OVERRIDE);

        if (properties != null) {
            if (properties instanceof Resource)
                propertyPlaceholderConfigurer.setLocation((Resource) properties);
            else if (properties instanceof Properties)
                propertyPlaceholderConfigurer.setProperties((Properties) properties);
            else
                throw new IllegalArgumentException("Properties argument - " + properties + " - is of an unhandled type");
        }
        context.addBeanFactoryPostProcessor(propertyPlaceholderConfigurer);

        // MBean exporter
        //final MBeanExporter mbeanExporter = new AnnotationMBeanExporter();
        //mbeanExporter.setServer(ManagementFactory.getPlatformMBeanServer());
        //beanFactory.registerSingleton("mbeanExporter", mbeanExporter);
        context.registerBeanDefinition("mbeanExporter", getMBeanExporterBeanDefinition(defaultDomain));

        // inject bean names into components
        context.addBeanFactoryPostProcessor(new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
                for (String beanName : beanFactory.getBeanDefinitionNames()) {
                    try {
                        final BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                        if (beanDefinition.getBeanClassName() != null) { // van be null for factory methods
                            final Class<?> beanClass = Class.forName(beanDefinition.getBeanClassName());
                            if (Component.class.isAssignableFrom(beanClass))
                                beanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, beanName);
                        }
                    } catch (Exception ex) {
                        LOG.error("Error loading bean " + beanName + " definition.", ex);
                        throw new Error(ex);
                    }
                }
            }
        });

        if (beanFactoryPostProcessor != null)
            context.addBeanFactoryPostProcessor(beanFactoryPostProcessor);

        beanFactory.registerCustomEditor(org.w3c.dom.Element.class, co.paralleluniverse.common.util.DOMElementProprtyEditor.class);

        final Map<String, Object> beans = new HashMap<String, Object>();

        beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                LOG.info("Loading bean {} [{}]", beanName, bean.getClass().getName());
                beans.put(beanName, bean);

                if (bean instanceof Service) {
                    final BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
                    Collection<String> dependencies = getBeanDependencies(beanDefinition);

                    for (String dependeeName : dependencies) {
                        Object dependee = beanFactory.getBean(dependeeName);
                        if (dependee instanceof Service) {
                            ((Service) dependee).addDependedBy((Service) bean);
                            ((Service) bean).addDependsOn((Service) dependee);
                        }
                    }
                }
                return bean;
            }

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                LOG.info("Bean {} [{}] loaded", beanName, bean.getClass().getName());
                return bean;
            }
        });

        final XmlBeanDefinitionReader xmlReader = new XmlBeanDefinitionReader((BeanDefinitionRegistry) beanFactory);
        xmlReader.loadBeanDefinitions(xmlResource);

        // start container
        context.refresh();

        // Call .postInit() on all Components
        // There's probably a better way to do this
        try {
            for (Map.Entry<String, Object> entry : beans.entrySet()) {
                final String beanName = entry.getKey();
                final Object bean = entry.getValue();
                if (bean instanceof Component) {
                    LOG.info("Performing post-initialization on bean {} [{}]", beanName, bean.getClass().getName());
                    ((Component) bean).postInit();
                }
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        return context;
    }

    public static Collection<String> getBeanDependencies(BeanDefinition beanDefinition) {
        Set<String> dependencies = new HashSet<String>();
        if (beanDefinition.getDependsOn() != null)
            dependencies.addAll(Arrays.asList(beanDefinition.getDependsOn()));

        for (ValueHolder value : beanDefinition.getConstructorArgumentValues().getGenericArgumentValues()) {
            if (value.getValue() instanceof BeanReference)
                dependencies.add(((BeanReference) value.getValue()).getBeanName());
        }
        for (ValueHolder value : beanDefinition.getConstructorArgumentValues().getIndexedArgumentValues().values()) {
            if (value.getValue() instanceof BeanReference)
                dependencies.add(((BeanReference) value.getValue()).getBeanName());
        }
        for (PropertyValue value : beanDefinition.getPropertyValues().getPropertyValueList()) {
            if (value.getValue() instanceof BeanReference)
                dependencies.add(((BeanReference) value.getValue()).getBeanName());
        }
        return dependencies;
    }

    /**
     * adds hooks to capture autowired constructor args and add them as dependencies
     *
     * @return
     */
    private static DefaultListableBeanFactory createBeanFactory() {
        return new DefaultListableBeanFactory() {
            {
                final InstantiationStrategy is = getInstantiationStrategy();
                setInstantiationStrategy(new InstantiationStrategy() {
                    @Override
                    public Object instantiate(RootBeanDefinition beanDefinition, String beanName, BeanFactory owner) throws BeansException {
                        return is.instantiate(beanDefinition, beanName, owner);
                    }

                    @Override
                    public Object instantiate(RootBeanDefinition beanDefinition, String beanName, BeanFactory owner, Constructor<?> ctor, Object[] args) throws BeansException {
                        final Object bean = is.instantiate(beanDefinition, beanName, owner, ctor, args);
                        addDependencies(bean, args);
                        return bean;
                    }

                    @Override
                    public Object instantiate(RootBeanDefinition beanDefinition, String beanName, BeanFactory owner, Object factoryBean, Method factoryMethod, Object[] args) throws BeansException {
                        final Object bean = is.instantiate(beanDefinition, beanName, owner, factoryBean, factoryMethod, args);
                        addDependencies(bean, args);
                        return bean;
                    }
                });
            }

            private void addDependencies(Object bean, Object[] args) {
                if (bean instanceof Service) {
                    for (Object arg : args) {
                        if (arg instanceof Service) {
                            ((Service) arg).addDependedBy((Service) bean);
                            ((Service) bean).addDependsOn((Service) arg);
                        }
                    }
                }
            }
        };
    }

    public static BeanDefinition defineBean(Class<?> clazz, ConstructorArgumentValues constructorArgs, MutablePropertyValues properties) {
        GenericBeanDefinition bean = new GenericBeanDefinition();
        bean.setBeanClass(clazz);
        bean.setAutowireCandidate(true);
        bean.setConstructorArgumentValues(constructorArgs);
        bean.setPropertyValues(properties);

        return bean;
    }

    private static BeanDefinition getMBeanExporterBeanDefinition(String defaultDomain) {
        final AnnotationJmxAttributeSource annotationSource = new AnnotationJmxAttributeSource();

        final GenericBeanDefinition bean = new GenericBeanDefinition();
        bean.setBeanClass(MBeanExporter.class);
        MutablePropertyValues properties = new MutablePropertyValues();
        properties.add("server", ManagementFactory.getPlatformMBeanServer());
        properties.add("autodetectMode", MBeanExporter.AUTODETECT_ASSEMBLER);
        properties.add("assembler", new MetadataMBeanInfoAssembler(annotationSource));
        properties.add("namingStrategy", new MBeanNamingStrategy(annotationSource).setDefaultDomain(defaultDomain));
        bean.setPropertyValues(properties);
        return bean;
    }

    public static ConstructorArgumentValues constructorArgs(Object... args) {
        ConstructorArgumentValues cav = new ConstructorArgumentValues();
        for (int i = 0; i < args.length; i++)
            cav.addIndexedArgumentValue(i, args[i]);
        return cav;
    }

    public static MutablePropertyValues properties(Map<String, ? extends Object> properties) {
        MutablePropertyValues mpv = new MutablePropertyValues(properties);
        return mpv;
    }

    private SpringContainerHelper() {
    }
}
