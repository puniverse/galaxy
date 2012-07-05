.. _man-config-cluster:

#################################
Configuring the Cluster Component
#################################

The cluster component (defined by the Spring Bean named ``cluster``) is the most fundamental of Galaxy's components. 
It defines how the Galaxy nodes discover each other and exchange configuration data.

There are currently two implementations of the cluster, one employing Apache ZooKeeper, and the other using JGroups. You may choose whichever you prefer. Note,
however, that ZooKeeper requires special server nodes while JGroups doesn't.

.. _man-config-cluster-common:

Servers and Backup Groups
=========================

Both ``cluster`` implementations share a couple of properties. 

The first sets the ``nodeId`` (constructor-arg, ``short``). This is a required property.  
The special node id of ``0`` is reserved for server nodes (see below).

The other common property is ``hasServer`` (property, ``boolean``, default:``true``). 
It is an optional property (with the default value ``true``) that specifies whether the cluster has
server nodes.

There are two mechanisms by which Galaxy provides high-availability in the face of node failures: server nodes and backup groups.

.. _man-config-cluster-organization-server:

Server node
-----------

A server node (or nodes) is a special Galaxy node that doesn't run application code, but is responsible for providing Galaxy data
with disk-based persistence. All of the data in the grid is persisted to disk on the server node, and is also automatically served
by it in case of a node failure. 

The node server always has a node id of ``0``, and, just like with regular nodes, you can have several server nodes in a 
backup group (see below) configuration for added availability.

See :ref:`man-config-server` for more information about configuring and running server node(s).

.. _man-config-cluster-organization-backup:

Backup Groups
-------------

Galaxy nodes can be configured in backup groups. All nodes assigned the same node id in their configuration file will become part 
of the same backup group. At any given time, each live backup group has exactly one **master** node, and zero or more **slave**
nodes. The master node replicates all of its owned data items to its slaves so that they can take over in case it fails.

When the master node fails, one of its slaves will become the master and take over. However, a master node *can never become a slave*.
A slave node is notified of its new master status by an event (see :ref:`man-api-cluster-lifecycle`).

See :ref:`man-api-cluster-organization` for more information.

.. attention::

  This version of Galaxy does not yet support a backup group for the server. This feature will be ready by the time version
  1.0 is finalized.

  This version of Galaxy supports backup groups of size 2 only (i.e. one master and one backup for each peer node).
  Larger backup groups will not be supported in Galaxy version 1.0.

.. _man-config-cluster-organization-serverless:

Should you use a server?
------------------------

Short answer: **yes!**

While you can configure a Galaxy cluster without a server, such configuration entails a somewhat different cache-coherence protocol.
When not using a server node, if two node failures occur (from two different backup groups) within a very short time period, 
this may result in lost data items (though not in data conflicts). 

In addition, it is not yet clear how dependable the grid could be without a server, and how useful a server-less configuration is.
It is possible that the server-less configuration will be discontinued in a future version.

.. attention::

  In this version, the server-less configuration is not dependable, and may result in lost data, and possibly data conflicts,
  even if *a single node fails!*

.. _man-config-cluster-zookeeper:

Using ZooKeeper
===============

The ``cluster`` component can use `Apache ZooKeeper`_ for cluster management. Galaxy uses ZooKeeper through the Netflix's Curator_ library
which simplifies ZooKeeper use. Please refer to the ZooKeeper documentation on how to set up ZooKeeper servers.

The ZooKeeper implementation of the ``cluster`` component is called ``co.paralleluniverse.galaxy.zookeeper.ZooKeeperCluster``
and has (in addition to the common ``nodeId`` and ``hasServer``) the following configuration properties:

``zkConnectString`` (constructor-arg, ``String``)
  The ZooKeeper connection string, which tells the node how to connect to the ZooKeeper servers. See the `ZooKeeper Programmers Guide`_ for details.

``sessionTimeoutMs`` (property, ``int``, default: ``15000``)
  The ZooKeeper session timeout, in milliseconds. The ZooKeeper documentation has the details.

``connectionTimeoutMs`` (property, ``int``, default: ``10000``)
  The Curator connection timeout, in milliseconds.

``retryPolicy`` (property, ``com.netflix.curator.retry.ExponentialBackoffRetry``, default: ``new ExponentialBackoffRetry(20, 20)``)
  The Curator retry policy for failed ZooKeeper operations. See the example below on how to set this property.
  Refer to the `Curator documentation`_ for details.

.. _`Apache ZooKeeper`: http://zookeeper.apache.org/
.. _`ZooKeeper Programmers Guide`: http://zookeeper.apache.org/doc/trunk/zookeeperProgrammers.html
.. _Curator: https://github.com/Netflix/curator
.. _`Curator documentation`: https://github.com/Netflix/curator/wiki

Using ZooKeeper in the Cloud
----------------------------

There generally shouldn't be a problem running Galaxy with ZooKeeper in the cloud. However, using ZooKeeper requires that the
UDP implementation of the ``comm`` component be used, and that should be configured correctly to work in cloud environments.
Please refer to :ref:`man-config-comm-common-cloud` for information.

ZooKeeper Configuration Example
-------------------------------

.. code-block:: xml

    <bean id="cluster" class="co.paralleluniverse.galaxy.zookeeper.ZooKeeperCluster">
        <constructor-arg name="nodeId" value="${grid.nodeId}"/>
        <property name="hasServer" value="true"/>
        <constructor-arg name="zkConnectString" value="127.0.0.1:2181"/>
        <property name="sessionTimeoutMs" value="1500"/>
        <property name="connectionTimeoutMs" value="1000"/>
        <property name="retryPolicy">
            <bean class="com.netflix.curator.retry.ExponentialBackoffRetry">
                <constructor-arg index="0" value="20"/>
                <constructor-arg index="1" value="20"/>
            </bean>
        </property>
    </bean>

.. _man-config-cluster-jgroups:

Using JGroups
=============

Instead of ZooKeeper, the ``cluster`` component can used an implementation (called ``co.paralleluniverse.galaxy.jgroups.JGroupsCluster``) 
that employs JGroups_ for cluster management. Unlike ZooKeeper, JGroups manages the cluster in a purely peer-to-peer fashion, and thus
does not require any special servers.

In addition to ``nodeId`` and ``hasServer``, the JGroups ``cluster`` bean requires a few more properties, both must be identical in all 
Galaxy nodes.

The first, ``jgroupsClusterName`` (constructor-arg, ``String``), is a ``String`` identifier you choose to give the JGroups cluster. 
This property must be identical in all of the cluster nodes.

The second is contains the detailed JGroups cluster configuration, and this property, too, must be identical in all nodes.

There are two options for setting it. 
You can either set the ``jgroupsConfXML`` property to point to a JGroups XML configuration file,
or set the ``jgroupsConf`` property and embed within it the JGroups XML configuration, as in the example below.
You must set one of these properties, but not both.

`This section`_ of the JGroups manual explains the JGroups configuration in detail, but the JGroups jar file contains several complete
XML configuration files you can use as a basis.

Any valid JGroups configuration would do, but you must make two important addition to ensure proper operation of Galaxy (either in the embedded
configuration or in the separate XML file).

First, you must add the `SEQUENCER <http://www.jgroups.org/manual-3.x/html/protlist.html#SEQUENCER>`__  protocol to the configuration, 
so that a complete ordering of configuration messages is enforced. This is done by adding the following XML element to the JGroups configuration,
somewhere towards the bottom:

.. code-block:: xml

    <SEQUENCER />

Second, you must add the `COUNTER <http://www.jgroups.org/manual-3.x/html/protlist.html#COUNTER>`__ protocol.
Add the following XML element at the very bottom of the JGroups configuration:

.. code-block:: xml

    <COUNTER bypass_bundling="true"  timeout="5000"/>

Third, if the cluster is configured not to use a server (``hasServer`` is set to ``false``), a locking protocol must be added
to the bottom of JGroups configuration:

.. code-block:: xml

  <CENTRAL_LOCK num_backups="1"/>

.. attention::
  Do not forget to add the ``SEQUENCER`` and ``COUNTER`` (and if no server is used then ``CENTRAL_LOCK`` as well) 
  protocols to the JGroups configuration!

The third property, ``jgroupsThreadPool`` (property, ``java.util.concurrent.ThreadPoolExecutor``, required), creates the thread pool used by 
JGroups. Please refer to :ref:`man-config-misc-threadpool` to learn how to configure thread-pools, or look at the example below.

.. _JGroups: http://www.jgroups.org/

.. _`This section`: http://www.jgroups.org/manual-3.x/html/user-advanced.html#d0e2199

.. _man-config-cluster-jgroups-cloud:

Using JGroups in the Cloud
--------------------------

Some cloud environments (like Amazon EC2) prohibit multicast, so JGroups must be configured to not use multicast
if you're running Galaxy in such an environment.

There are generally two options to use in such cases. The first is to use the `UDP <http://www.jgroups.org/manual-3.x/html/protlist.html#UDP>`__ 
JGroups transport, but disable multicasting (by setting to ``false`` the ``ip_mcast`` property). In addition, you must use a discovery protocol
that does not employ multicasting, such as ``FILE_PING``, ``JDBC_PING``, ``RACKSPACE_PING`` or ``S3_PING``. 
See `Initial membership discovery <http://www.jgroups.org/manual-3.x/html/protlist.html#DiscoveryProtocols>`__ in the JGroups documentation
for more information on discovery protocols.

The other option is to use the `TCP <http://www.jgroups.org/manual-3.x/html/protlist.html#TCP>`__ JGroups transport with either the 
``TCPPING`` or ``TCPGOSSIP`` discovery protocols (or any of the ones mentioned above).

.. note::
  When using Galaxy and JGroups in environments that do not support multicasting, you must also configure the ``comm`` component appropriately.
  See :ref:`man-config-comm-common-cloud`.


JGroups Configuration Example
-----------------------------

.. code-block:: xml

    <bean id="cluster" class="co.paralleluniverse.galaxy.jgroups.JGroupsCluster">
        <constructor-arg name="nodeId" value="${grid.nodeId}"/>
        <property name="hasServer" value="true"/>
        <constructor-arg name="jgroupsClusterName" value="cluster1"/>
        <property name="jgroupsConf">
            <value>
                <![CDATA[
                <config xmlns="urn:org:jgroups"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:schemaLocation="urn:org:jgroups http://www.jgroups.org/schema/JGroups-3.1.xsd">
                    <UDP
                        mcast_port="${jgroups.udp.mcast_port:45588}"
                        tos="8"
                        ucast_recv_buf_size="20M"
                        ucast_send_buf_size="640K"
                        mcast_recv_buf_size="25M"
                        mcast_send_buf_size="640K"
                        loopback="true"
                        discard_incompatible_packets="true"
                        max_bundle_size="64K"
                        max_bundle_timeout="30"
                        ip_ttl="${jgroups.udp.ip_ttl:8}"
                        enable_bundling="true"
                        enable_diagnostics="true"
                        thread_naming_pattern="cl"
    
                        timer_type="new"
                        timer.min_threads="4"
                        timer.max_threads="10"
                        timer.keep_alive_time="3000"
                        timer.queue_max_size="500"/>
    
                    <PING timeout="2000"
                          num_initial_members="4"/>
                    <MERGE3 max_interval="30000"
                            min_interval="10000"/>
                    <FD_SOCK/>
                    <FD_ALL/>
                    <VERIFY_SUSPECT timeout="1500"  />
                    <BARRIER />
                    <pbcast.NAKACK2 xmit_interval="1000"
                                    xmit_table_num_rows="100"
                                    xmit_table_msgs_per_row="2000"
                                    xmit_table_max_compaction_time="30000"
                                    max_msg_batch_size="500"
                                    use_mcast_xmit="false"
                                    discard_delivered_msgs="true"/>
                    <UNICAST />
                    <pbcast.STABLE stability_delay="1000" desired_avg_gossip="50000"
                                   max_bytes="4M"/>
                    <pbcast.GMS print_local_addr="true" join_timeout="3000"
                                view_bundling="true"/>
                    <SEQUENCER />
                    <UFC max_credits="2M"
                         min_threshold="0.4"/>
                    <MFC max_credits="2M"
                         min_threshold="0.4"/>
                    <FRAG2 frag_size="60K"  />
                    <pbcast.STATE_TRANSFER />
                    <COUNTER bypass_bundling="true" 
                             timeout="5000"/>
                </config>
                ]]>
            </value>
        </property>
        <property name="jgroupsThreadPool">
            <bean class="co.paralleluniverse.galaxy.core.ConfigurableThreadPool">
                <constructor-arg name="corePoolSize" value="2"/>
                <constructor-arg name="maxPoolSize" value="8"/>
                <constructor-arg name="keepAliveMillis" value="5000"/>
                <constructor-arg name="maxQueueSize" value="500"/>
            </bean>
        </property>
    </bean>

