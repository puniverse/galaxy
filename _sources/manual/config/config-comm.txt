.. _man-config-comm:

#############################################
Configuring and Monitoring the Comm Component
#############################################

The Comm component (bean id: ``comm``) is responsible for transmitting Galaxy's internal cache-coherence protocol messages, as well as user messages
(sent with the `Messenger`) over the network. There are currently two implementations for this component. The first uses UDP, and the second uses 
JGroups and is available only if the cluster is configured to use JGroups (see :ref:`man-config-cluster-jgroups`).

.. _man-config-comm-common:

Common comm configurations
==========================

Both ``comm`` implementations share a couple of very important configuration properties.

.. _man-config-comm-common-timeout:

Timeout
-------

The ``timeout`` configuration (property, ``long``, default: ``200``), is the duration, **in milliseconds** to wait for a response to a 
massage. This, in effect, determines the time it takes for any grid operation to fail and throw a ``TimeoutException`` (See :ref:`man-api-store-ownership-deadlock` 
and :ref:`man-api-store-transactions`).

The shorter the timeout is, the faster deadlocks will be detected, but so will more operations fail spuriously due to network latency.

.. _man-config-comm-common-cloud:

No multicast (using Galaxy in the cloud)
----------------------------------------

For some internal operations (such as finding the owner of a freshly-encountered item id), both Comm implementations use multicast by default.
Some cloud platforms (like Amazon EC2) do not allow multicast, so to run Galaxy without multicast, we can configure it to use the server for
node discovery. Of course, in a situation like that a server is necessary.

In deployments where multicast is available, communicating with the server instead of multicasting can have a performance impact - sometimes for the better
and sometimes for the worse, depending mostly on your server's performance.

To tell ``comm`` to talk to the server instead of multicasting, set the bean property ``sendToServerInsteadOfMulticast`` to ``true`` (it's ``false`` by default).

Please not that if you're using JGroups for your ``cluster`` component implementation, you must configure JGroups to avoid multicast as well. 
See :ref:`man-config-cluster-jgroups-cloud` for more information.

.. _man-config-comm-common-servercomm:

The ServerComm
--------------

``comm`` makes use of another component, called ``serverComm`` to communicate with the server. At the moment, there is just one implementation of ``server-comm``,
which uses TCP (so it makes use of the optional ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties,
explained in :ref:`man-config-comm-netty`.). 

So, if your cluster has a server node, define it with the following bean (in this example we do not set the thread-pool properties, so defaults are used):

.. code-block:: xml

    <bean id="serverComm" class="co.paralleluniverse.galaxy.netty.TcpServerClientComm"/>

and link it to the ``comm`` bean by putting this line in the ``comm`` bean definition:

.. code-block:: xml

    <constructor-arg name="serverComm" ref="serverComm"/>

(actually, there is one more ``server-comm`` implementation, one that's used with something called "dumb servers". See :ref:`man-config-server-dumb` for details).

If your cluster is configured without a server, set this constructor-arg to ``null`` (see :ref:`man-config-spring-values-null`). 

.. _man-config-comm-common-slavecomm:

The SlaveComm
-------------

Just like a special component is used to communicate with the server, so too a special component is used to communicate with the slaves in the node's backup group.
The component is ``slaveComm``, and it currently has one implementation that uses TCP called ``co.paralleluniverse.galaxy.netty.TcpSlaveComm``. 

In addition to the optional ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties explained in :ref:`man-config-comm-netty`,
it has one configuration property:

``port`` (constructor-arg, ``int``)
  The TCP port used for master-slave communications. The master binds a server socket to this port (and the slaves discover the port using the distributed 
  configuration record, so in principle, this port can be different on each node, as it's used only when the node is master.)

Here's an example:

.. code-block:: xml

    <bean id="slaveComm" class="co.paralleluniverse.galaxy.netty.TcpSlaveComm">
        <constructor-arg name="port" value="${grid.slave_port}"/>
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

.. _man-config-comm-udp:

Using the UDP comm
==================

The UDP implementation of the Comm component uses UDP datagrams for cache-coherence and user messages. 

Other than the mentioned common ones, plus the ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties
explained in :ref:`man-config-comm-netty`,
this implementation has the following configuration properties:

``port`` (constructor-arg, ``int``)
  The UDP port the component will send and receive messages on. This value **does not** have to be the same for all nodes.
  (nodes know each other ports by publishing them as a configuration record in the cluster).

``multicastGroup`` (property, ``java.net.InetSocketAddress``, required if ``sendToServerInsteadOfMulticast`` is ``true``)
  The multicast IP address this node will join for multicast address, and the port to use for sending and receiving multicast
  messages. This value **must be the same** in all nodes. See the example below on how to set this property's value.

``multicastNetworkInterface`` (property, ``java.net.NetworkInterface``, default: ``null``)
  The network interface to use for multicast. If set to ``null`` (the default), the default interface will be used.

``receiveBufferSize`` (property, ``int``, default: determined by the socket; implementation specific)
  The size of the socket receive buffer (SO_RCVBUF). The SO_RCVBUF option is used by the the network implementation as a hint to size the 
  underlying network I/O buffers. The SO_RCVBUF setting may also be used by the network implementation to determine the maximum size
  of the packet that can be received on this socket.

``minimumNodesToMulticast`` (property, ``int``, default: ``3``)
  The minimum number of nodes in the cluster (not including servers) for this component to use multicast. If there are fewer
  online nodes than this value, the component will unicast messages to each node.

``resendPeriodMillisecs`` (property, ``int``, default: ``20``)
  The duration in milliseconds to wait between consecutive resending of a message if a reply has not been received.
  If ``exponentioalBackoff`` is turned on (it's turned on by default), this is the initial duration (between the first time the
  message is sent and the second).

``exponentialBackoff`` (property, ``boolean``, default: ``true``)
  If turned on (which is the default), doubles the duration between resending of messages after each re-send.

``jitter`` (property, ``boolean``, default: ``false``)
  If turned on, adds a random small jitter to the duration between resends.

``minDelayMicrosecs`` (property, ``int``, default: ``1``)
  The minimum duration, in microseconds, to wait before transmitting a packet, for other messages to be sent so that they could be
  added to the same packet.

``maxDelayMicrosecs`` (property, ``int``, default: ``10``)
  The maximum duration, in microseconds, to wait for additional messages (in case they keep arriving), before transmitting a packet.

``maxQueueSize`` (property, ``int``, default: 50)
  The maximum number of messages waiting in the ``comm`` component's message queue. If this number is reached, sending an additional
  message will block until the queue length falls beneath it.

``maxPacketSize`` (property, ``int``, default: ``4096``)
  The maximum size of a single packet the ``comm`` component will transmit. Data-item size (defined by the ``maxItemSize`` property of the ``cache``
  component; see :ref:`man-config-cache-1`) must not exceed this value (and there must also be some room left for headers).

``maxRequestOnlyPacketSize`` (property, ``int``, default: ``maxPacketSize / 2``)
  The maximum size of a packet that contains only request messages. Must be less than ``maxPacketSize``.
  The exact semantics of this property is beyond the scope of this document, but if this value is too close to ``maxPacketSize`` a deadlock condition
  may arise (it will be clearly noted in the logs, so you can recognize it if it happens), and if it's too small, performance under heavy load may suffer.

.. code-block:: xml

    <bean id="comm" class="co.paralleluniverse.galaxy.netty.UDPComm">
        <constructor-arg name="serverComm" ref="serverComm"/>
        <property name="timeout" value="500"/>
        <property name="sendToServerInsteadOfMulticast" value="false"/>
        <constructor-arg name="port" value="${grid.port}"/>
        <property name="minimumNodesToMulticast" value="2"/>
        <property name="multicastGroup">
            <bean class="java.net.InetSocketAddress">
                <constructor-arg index="0" value="225.0.0.1"/>
                <constructor-arg index="1" value="7050"/>
            </bean>
        </property>
        <property name="resendPeriodMillisecs" value="35"/>
        <property name="exponentialBackoff" value="true"/>
        <property name="jitter" value="true"/>
        <property name="minDelayMicrosecs" value="500"/>
        <property name="maxDelayMicrosecs" value="2000"/>
        <property name="maxQueueSize" value="10"/>
        <property name="maxPacketSize" value="2048"/>
        <property name="maxRequestOnlyPacketSize" value="400"/>
		<property name="workerExecutor">
            <bean class="co.paralleluniverse.galaxy.core.ConfigurableThreadPool">
                <constructor-arg name="corePoolSize" value="2"/>
                <constructor-arg name="maximumPoolSize" value="8"/>
                <constructor-arg name="keepAliveMillis" value="5000"/>
                <constructor-arg name="maxQueueSize" value="500"/>
            </bean>
        </property>
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

.. _man-config-comm-netty:

Configuring Netty Channels
==========================

Except for configuration messages used by the ``cluster`` component, all Galaxy network communication - peer-nodes to peer-nodes, peer-nodes to servers
and slaves to masters - uses the Netty_ library (unless you've decided to use JGroups for peer-to-peer communication - see :ref:`man-config-comm-jgroups`).

Netty communication channels use various thread-pools.
TCP channels use a single "boss thread" taken from a "boss" thread-pool for making or accepting connections, and possibly multiple "worker" threads
taken from a different pool, responsible for sending and receiving messages. UDP channels (they're connectionless) only use worker threads. 

Components using TCP, therefore have two properties, ``bossExecutor`` and ``workerExecutor`` taking an instance of 
``java.util.concurrent.ThreadPoolExecutor``. If you don't set these properties, each uses a default thread-pool (returned from calling 
``java.util.concurrent.Executors.newCachedThreadPool()``). Components making use of UDP don't have the ``bossThread`` property.

See :ref:`man-config-misc-threadpool-config` for the thread-pool configuration details. 

In addition, all components using Netty can optionally use another thread-pool, passed to the ``receiveExecutor`` property. This thread-pool, which
must be an instance of ``org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor`` (a subclass of ``java.util.concurrent.ThreadPoolExecutor``),
and it is used to actually process the receive messages. If you set this property to ``null`` (which is the default), all message processing will
be done on the channel's worker thread-pool.

``OrderedMemoryAwareThreadPoolExecutor`` doesn't take a ``maximumPoolSize`` argument (as its core-size is also its maximum size), but
it does take two additional arguments:

``maxChannelMemorySize`` (``long``)  
  The maximum total size, in bytes, of the queued events per channel (i.e. per cluster node we're communicating with). 
  A value of ``0`` disables this limit.

``maxTotalMemorySize`` (``long``)    
  The maximum total size, in bytes, of the queued events for this pool. 
  A value of ``0`` disables this limit.

Unfortunately, named constructor-args don't seem to work with this class, so we must use argument indexes, and create the instance like so:

.. code-block:: xml

    <bean class="org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor">
        <constructor-arg index="0" value="8"/> <!-- "corePoolSize" -->
        <constructor-arg index="1" value="0"/> <!-- "maxChannelMemorySize" -->
        <constructor-arg index="2" value="0"/> <!-- "maxTotalMemorySize" -->
        <constructor-arg index="3" value="5000"/> <!-- "keepAliveTime" -->
        <constructor-arg index="4" value="MILLISECONDS"/> <!-- "unit" -->
    </bean>

.. _Netty: http://netty.io/
	
.. _man-config-comm-jgroups:

Using the JGroups comm
======================

The JGroups ``comm`` implementation is ``co.paralleluniverse.galaxy.jgroups.JGroupsComm``. 
It is only available when JGroups is chosen as the ``cluster`` component implementation (see :ref:`man-config-cluster-jgroups`).
Because JGroups is configured in the ``cluster`` bean, this bean has no properties other than the common ``comm`` ones, and can be defined
as simply as:

.. code-block:: xml

    <bean id="comm" class="co.paralleluniverse.galaxy.jgroups.JGroupsComm">
    	<constructor-arg name="serverComm" ref="serverComm"/>
        <property name="sendToServerInsteadOfMulticast" value="false"/>
        <property name="timeout" value="200"/>
    </bean>

The ``timeout`` and ``sendToServerInsteadOfMulticast`` properties are kept at their default values in this example, so they can be dropped entirely.


.. _man-config-comm-monitoring:

Monitoring the ``comm`` component
=================================

TBD