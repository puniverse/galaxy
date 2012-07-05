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
package co.paralleluniverse.common.spring;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.export.metadata.JmxAttributeSource;
import org.springframework.jmx.export.metadata.ManagedResource;
import org.springframework.jmx.export.naming.ObjectNamingStrategy;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 *
 * @author pron
 */
class MBeanNamingStrategy implements ObjectNamingStrategy, InitializingBean {
    /**
     * The
     * <code>JmxAttributeSource</code> implementation to use for reading metadata.
     */
    private JmxAttributeSource attributeSource;
    private String defaultDomain;

    /**
     * Create a new
     * <code>MetadataNamingStrategy<code> which needs to be
     * configured through the {@link #setAttributeSource} method.
     */
    public MBeanNamingStrategy() {
    }

    public MBeanNamingStrategy(JmxAttributeSource attributeSource) {
        Assert.notNull(attributeSource, "JmxAttributeSource must not be null");
        this.attributeSource = attributeSource;
    }

    public void setAttributeSource(JmxAttributeSource attributeSource) {
        Assert.notNull(attributeSource, "JmxAttributeSource must not be null");
        this.attributeSource = attributeSource;
    }

    public MBeanNamingStrategy setDefaultDomain(String defaultDomain) {
        this.defaultDomain = defaultDomain;
        return this;
    }

    @Override
    public void afterPropertiesSet() {
        if (this.attributeSource == null) {
            throw new IllegalArgumentException("Property 'attributeSource' is required");
        }
    }

    @Override
    public ObjectName getObjectName(Object managedBean, String beanKey) throws MalformedObjectNameException {
        Class managedClass = AopUtils.getTargetClass(managedBean);
        ManagedResource mr = this.attributeSource.getManagedResource(managedClass);

        // Check that an object name has been specified.
        if (mr != null && StringUtils.hasText(mr.getObjectName())) {
            return ObjectName.getInstance(mr.getObjectName());
        } else {
            String domain = this.defaultDomain;
            if (domain == null)
                domain = ClassUtils.getPackageName(managedClass);
            return ObjectName.getInstance(domain + ":type=components,name=" + beanKey);
        }
    }
}
