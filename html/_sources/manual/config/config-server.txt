.. _man-config-server:

##############################################
Configuring, Running and Monitoring the Server
##############################################

The Galaxy server node (or nodes, if more than one is configured in the backup group) provides two features:

* Disk persistence
* High availability in case of node failure.

A Galaxy cluster does not have to be configured to have a server. If disk persistence is not required, you can rely on backup groups
alone for high-availability (see :ref:`man-api-cluster-organization-backup`). However, in some circumstances, server nodes can provide
other benefits, such as improved performance.


There are two kinds of servers a Galaxy cluster can use. The first, which we'll call "real servers", and the other we'll call "dumb servers".
We will refer to non-server nodes as "peer nodes".

.. _man-config-server-real:

Real servers
============

Real servers are server nodes that run the Galaxy server software and (usually) access an embedded database.

Configuring real servers
------------------------

A real server has a similar configuration file to that used in peer nodes, but with some shared and some different components:

``cluster`` 
  This is the same as in the peer nodes. See :ref:`man-config-cluster` for instructions on how to configure this component.

``comm``
  This is the equivalent of the peer nodes' ``comm`` component (see :ref:`man-config-comm`), but it's (currently) sole implementation
  (``co.paralleluniverse.galaxy.netty.TcpServerServerComm``) - the server side of the peers' TCP ``serverComm`` - takes a ``port`` 
  property (constructor-arg, ``int``), 
  plus, optionally, the ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties
  explained in :ref:`man-config-comm-netty`.

  Here's a configuration example:

  .. code-block:: xml

      <bean id="comm" class="co.paralleluniverse.galaxy.netty.TcpServerServerComm">
          <constructor-arg name="port" value="9675"/>
          <property name="receiveExecutor">
              <bean class="org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor">
                  <constructor-arg index="0" value="8"/> <!-- name="corePoolSize" -->
                  <constructor-arg index="1" value="0"/> <!-- name="maxChannelMemorySize" -->
                  <constructor-arg index="2" value="0"/> <!-- name="maxTotalMemorySize" -->
                  <constructor-arg index="3" value="5000"/> <!-- name="keepAliveTime" -->
                  <constructor-arg index="4" value="MILLISECONDS"/> <!-- name="unit" -->
              </bean>
          </property>
      </bean>

``memory``
  This is the equivalent of the peers' ``cache`` component, and it's responsible for the server's data-item logic. It has one implementation
  (``co.paralleluniverse.galaxy.core.MainMemory``) that takes just a ``monitoringType`` property (see :ref:`man-config-monitoring`).
  Here's how it's defined:

  .. code-block:: xml

      <bean id="memory" class="co.paralleluniverse.galaxy.core.MainMemory">
          <constructor-arg name="monitoringType" value="METRICS"/> <!-- METRICS/JMX -->
      </bean>

``store``
  This component is responsible for persisting and retrieving data items using a database. 
  It's configuration is explained :ref:`below <man-config-server-store>`.

Running the server
------------------

To run the server, simply run the executable Java class ``co.paralleluniverse.galaxy.Server``, and optionally pass one or two command line
arguments specifying the configuration file and, optionally, the properties file, too (see :ref:`man-config-file` for an explanation).

Optionally, if for some reason you'd like to embed the Galaxy server in your own Java process, you may start the server by calling
``Server``'s ``start`` method and optionally pas the configuration and properties files.

If no configuration file is specified, the server uses the file called ``galaxy.xml`` if it's found somewhere on the classpath.

You may refer to the Server class :javadoc:`Javadoc <co/paralleluniverse/galaxy/Server.html>` for more details.

.. _man-config-server-dumb:

Dumb servers
============

Dumb servers are machines running some sort of a database server that can be used as Galaxy servers without being Galaxy nodes themselves,
i.e., they do not run any Galaxy code - just the database server. Databases that provide network access can be used as dumb servers.
When using a dumb server, the logic for accessing and communicating with them is hosted on the peer nodes.

To configure a dumb server, you must make some changes and additions to the peers' components.

``serverPipe``
  This simple additional bean is responsible for piping all messages sent to the server to the local server proxy. It's defined thus:

  .. code-block:: xml
  
  <bean id="serverPipe" class="co.paralleluniverse.galaxy.server.CommPipe"/>

``serverComm``
  Instead of a TCP connection to a real server, we will now be directing messages to the server through the ``serverPipe`` so ``serverComm``
  is now defined so:

  .. code-block:: xml

      <bean id="serverComm" factory-bean="serverPipe" factory-method="getComm1">
          <constructor-arg index="0" value="${grid.nodeId}"/>
      </bean>

``memory``
  The memory component (responsible for server logic) now sits at the peers, so it has to be added to the peer configuration, and it receives
  messages from the other end of the ``serverPipe``:

  .. code-block:: xml
  
      <bean id="memory" class="co.paralleluniverse.galaxy.core.MainMemory">
          <constructor-arg name="comm">
              <bean factory-bean="serverPipe" factory-method="getComm2">
                  <constructor-arg index="0" value="0"/>
              </bean>
          </constructor-arg>
          <constructor-arg name="monitoringType" value="METRICS"/>
      </bean>

``store``
  The store is now configured at the peers. See :ref:`man-config-server-store` for instructions on how to configure the store.

.. _man-config-server-store:

Configuring the store
=====================

The store is the component responsible for data-item persistence, and is usually implemented on top of some database.
At the moment there are two store implementations, one that uses **BerkeleyDB Java Edition**, and that uses any RDBMS with a JDBC driver.

.. _man-config-server-store-bdb:

Using BerkeleyDB 
----------------

`BerkeleyDB Java Edition`_ (BDB JE) can be used as Galaxy's store. Because BDB JE is an embedded database and does not have a network interface,
it can only be used as a dumb server - only as part of a real server. 
The ``store`` implementation that uses BDB JE is ``co.paralleluniverse.galaxy.berkeleydb.BerkeleyDB``, and it has two configuration properties:

``envHome`` (constructor-arg, ``String``)
  The path to the directory which will contain the BDB files.

``truncate`` (property, ``boolean``, default: ``false``)
  Whether or not the database will be truncated (i.e., all the data-item data be deleted) when the server starts.

``durability`` (property, ``com.sleepycat.je.Durability.SyncPolicy``, default: ``WRITE_NO_SYNC``)
  Defines the disk synchronization policy to be used when committing a transaction. There are three possible values:
  ``SYNC``, ``WRITE_NO_SYNC``, or ``NO_SYNC``, that are fully explained in the BDB JE Javadocs `here <http://docs.oracle.com/cd/E17277_02/html/java/index.html>`__.

Tuning of BerkeleyDB JE is possible by setting properties in the ``je.properties`` file, placed at the environment home directory.
Details about BDB JE tuning can be found in the JE documentation `here <http://docs.oracle.com/cd/E17277_02/html/GettingStartedGuide/administration.html>`__. 

Here's a configuration example:

.. code-block:: xml

    <bean id="store" class="co.paralleluniverse.galaxy.berkeleydb.BerkeleyDB">
        <constructor-arg name="envHome" value="/usr/bdb/galaxy"/>
        <property name="truncate" value="true"/>
    </bean>


.. _`BerkeleyDB Java Edition`: http://www.oracle.com/technetwork/database/berkeleydb/overview/index-093405.html

.. _man-config-server-store-jdbc:

Using SQL
---------

Any SQL database that supports transactions and has a JDBC driver can be used as the store. Those that have a network interface can also
become dumb servers. The ``store`` implementation that uses JDBC is ``co.paralleluniverse.galaxy.jdbc.SQLDB`` and here are it's configuration
properties:

``dataSource`` (constructor-arg, ``javax.sql.DataSource``)
  The ``DataSource`` instance used to construct DB connections. See the example below on how to set this property.

``maxItemSize`` (property, ``int``, default: ``1024``)
  The maximum size, in bytes, of a data-item. Must be the same as the ``maxItemSize`` set in the ``cache`` component (see :ref:`man-config-cache-1`).

``useUpdateableCursors`` (property, ``boolean``, default: ``false``)
  Whether updateable cursors should be used in some atomic transactions. Might have a positive, or negative performance impact, depending
  on the database and driver implementation.

``schema`` (property, ``String``, default: ``pugalaxy``)
  The schema that will host the Galaxy table.

``tableName`` (property, ``String``, default: ``memory``)
  The name of the table that will store the data-items.

``bigintType`` (property, ``String``, default: queried with ``DatabaseMetaData`` if possible)
  The name of the database's SQL type for JDBC's ``BIGINT``. Should be set if automatic detection does not work.
 
``smallintType`` (property, ``String``, default: queried with ``DatabaseMetaData`` if possible)
  The name of the database's SQL type for JDBC's ``SMALLINT``. Should be set if automatic detection does not work.

``varbinaryType`` (property, ``String``, default: queried with ``DatabaseMetaData`` if possible)
  The name of the database's SQL type for JDBC's ``VARBINARY``. Should be set if automatic detection does not work.

.. code-block:: xml

    <bean id="store" class="co.paralleluniverse.galaxy.jdbc.SQLDB">
        <constructor-arg name="dataSource">
            <bean class="org.apache.derby.jdbc.ClientDataSource40">
                <property name="serverName" value="mydbhost"/>
                <property name="portNumber" value="1527"/>
                <property name="databaseName" value="galaxydb"/>
                <property name="createDatabase" value="create"/>
            </bean>
        </constructor-arg>
        <property name="maxItemSize" value="1024"/>
        <property name="useUpdateableCursors" value="false"/>
    </bean>

When using a SQL database, if the entire grid is taken down, you must manually either clear the Galaxy table (which is called 
``pugalaxy.memory`` by default) if you'd like to dispose of the data, or, if you'd like to keep it, you must assign ownership of all
data items to the server by running the following SQL command:

.. code-block:: sql

  UPDATE pugalaxy.memory SET owner=0

.. attention::

  **Do not forget** to clear the database table or set the owner in all rows to ``0`` before re-starting the grid.
  If you don't, havoc will ensue.


