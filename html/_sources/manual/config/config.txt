.. _man-config:

#################################
Configuring and Monitoring Galaxy
#################################

Configuring the Galaxy grid is just as important as using its API, as the API is designed to be simple, but the actions Galaxy takes under the hood are very much
determined by the way Galaxy is configured. 

Galaxy uses the `Spring Framework`_ for its configuration, and is configured from an XML file using Spring Beans. For a short tutorial on
Spring configuration, see :ref:`man-config-spring`.

.. note::

  This manual provides detailed instructions to configuring Galaxy. However, for a quicker start, we suggest you take a look at
  :ref:`start-config` in the Getting Started guide.

.. _`Spring Framework`: http://www.springsource.org/spring-framework

.. _man-config-file:

The configuration file(s)
=========================

A Galaxy node is configured from one XML file (of course, that file can reference others in accordance with the Spring Beans schema), whose path is passed to
the ``getInstance`` static method of the ``Grid`` class (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Grid.html>`).
If you pass ``null`` (or use the zero-argument version of ``getInstance``), the default configuration file, ``galaxy.xml`` is used, if it's found somewhere on the
classpath.

In addition to the XML file, you may use a Java properties file, and reference those properties in the configuration file like so: ``${prop.name}``. The name
of the properties file may also be passed to ``getInstance``. If a ``null`` name is passed (or the zero-argument version of ``getInstance`` is used), no properties
file gets loaded. The properties file is handy when you want to use the same configuration file for all nodes, with only minor differences (like node ID). 
In this case, you can just set the different properties in the separate properties file. This is the common case so properties files are recommended.

You define properties in the properties file like so (you can make up your own property names - they have no special meaning for Galaxy):

.. code-block:: properties

    cluster.nodeId=3

and you reference them from the configuration file when setting configuration properties like this:

.. code-block:: xml

	<constructor-arg name="nodeId" value="${cluster.nodeId}"/> 

The configuration file defines which implementation each Galaxy component is to use, as well as implementation specific configuration parameters for each component.

Galaxy's configuration is flexible enough to allow running multiple nodes (even server nodes) in the same physical machine. All it requires is setting IP ports
and the like carefully, where appropriate.

.. attention::

    You *must* use the documented bean ids for each component. You may not assign the beans other names, as Galaxy depends on the components having specific names. 
    In addition, Spring's ``default-autowire`` mode must be set to ``constructor``.

Here's how the configuration file should look:
	
.. code-block:: xml

    <?xml version="1.0" encoding="UTF-8"?>
    <beans xmlns="http://www.springframework.org/schema/beans"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xmlns:context="http://www.springframework.org/schema/context"
           xmlns:util="http://www.springframework.org/schema/util"

           xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
              http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-3.0.xsd
              http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util-3.0.xsd"
           default-lazy-init="false"
           default-autowire="constructor">

        <!-- bean definitions ... -->
    </beans>

.. _man-config-monitoring:

Configuring Galaxy monitoring
=============================

Most Galaxy components expose monitoring data, and most of those that do let you choose what monitor to use (in the ``monitoringType`` constructor-arg). Currently,
there are two options:

* ``METRICS`` - uses Yammer's Metrics_ library. Data can be exported to JMX MBeans, Ganglia_, or Graphite_. This option gives very detailed an high-resolution
  monitoring data, but might be heavy on resources.
* ``JMX`` - uses simple JMX MBeans to export monitoring data. The data is lower-resolution and cruder than the Metrics option, but might be lighter on resources.

.. _Metrics: http://metrics.codahale.com/
.. _Ganglia: http://ganglia.sourceforge.net/
.. _Graphite: http://graphite.wikidot.com/

The best way to view MBeans is with **VisualVM**.

.. _man-config-component-monitor:

Monitoring Galaxy's components
==============================

Most Galaxy components expose monitoring data, and the details about which information is exposed is detailed in the component's respective manual section.
Aside from component-specific monitoring, all components expose their configuration properties and basic status as MBeans. These MBeans are named
``co.paralleluniverse.galaxy:type=components,name=COMPONENT_NAME``, where ``COMPONENT_NAME`` is the Spring Bean id.

.. _man-config-logging:

Logging
=======

Galaxy uses SLF4J_ for all its logging.

.. _SLF4J: http://www.slf4j.org/

Galaxy's components
===================

.. toctree::
    :maxdepth: 1

    config-spring
    config-cluster
    config-cache
    config-messenger
    config-comm
    config-server
    config-misc
