.. _man-config-cache:

####################################
Configuring and Monitoring the Cache
####################################

The cache (bean id ``cache``) is the component responsible for the managements of data items in the local node's RAM, and for
the ownership cache-coherence logic.

.. _man-config-cache-1:

Configuring the cache
=====================

The cache currently has only one implementation (``co.paralleluniverse.galaxy.core.Cache``), and the following properties:

``monitoringType`` (constructor-arg, ``String``)
  Sets the monitor type to use for cache monitoring. Can be either ``METRICS`` or ``JMX`` (see :ref:`man-config-monitoring`).

``synchronous`` (property, ``boolean``, default: ``false``)
  Whether or not backups are done synchronously.
  When set to ``true``, local get operations block until the server and/or slaves have acknowledged the backup.

``maxCapacity`` (constructor-arg, ``long``)
  The maximum capacity (in bytes) to be used for storing shared items. If shared items take up more space than that, they will be evicted from the cache.
  Note that owned items are never evicted. 

``maxItemSize`` (property, ``int``, default: ``1024``)
  The maximum size, in bytes of a single data item. If ``UDPComm`` is used as the ``comm`` implementation (see :ref:`man-config-comm`), then an item must fit in
  a single UDP packet with room to spare. Ideally, it would fit in one IP packet, so for larger values of ``maxItemSize`` it's best to configure your network
  to use jumbo packets. This value must be the same in all nodes.

``rollbackSupported`` (property, ``boolean``, default: ``true``)
  Sets whether or not automatic rollbacks for transactions are supported. See :ref:`man-api-store-transactions` and the ``Store.rollback()`` 
  (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#rollback(co.paralleluniverse.galaxy.StoreTransaction)>`).

``compareBeforeWrite`` (property, ``boolean``, default: ``true``)
  Sets whether or not written items should first be compared with their old value before creating a new version (Galaxy maintains a version number for each item
  to track updates).

``reuseLines`` (property, ``boolean``, default: ``true``)
  Sets whether or not the cache should pool and reuse the data-item book-keeping objects.

``reuseSharerSets`` (property, ``boolean``, default: ``false``)
  Sets whether or not the cache should pool and reuse the objects used to store data-item sharers.


Here's an example:

.. code-block:: xml

    <bean id="cache" class="co.paralleluniverse.galaxy.core.Cache">
        <constructor-arg name="monitoringType" value="METRICS"/>
        <constructor-arg name="maxCapacity" value="100000000"/> 
        <property name="maxItemSize" value="1024"/>
        <property name="reuseLines" value="true"/>
        <property name="reuseSharerSets" value="true"/>
        <property name="rollbackSupported" value="true"/>
        <property name="compareBeforeWrite" value="true"/>
    </bean>

.. note::

  Synchronous mode is not yet implemented in this version.

.. _man-config-cache-storage:

Configuring local storage
=========================

``localStorage`` is a component related to ``cache``. It is responsible for the actual storage in RAM of the data items.
There are currently two implementations - one keeps the items in the Java heap, and one stores them in direct ``ByteBuffer`` s.

Heap local storage
------------------

The ``co.paralleluniverse.galaxy.HeapLocalStorage`` implementation stores items in plain Java byte-arrays, allowing the Java garbage-collector
to reclaim them when appropriate.

It has one property - ``monitoringType`` (constructor-arg, ``String``), which you can set to either ``METRICS`` or ``JMX`` (see :ref:`man-config-monitoring`).

This is how you configure it:

.. code-block:: xml

    <bean id="localStorage" class="co.paralleluniverse.galaxy.HeapLocalStorage">
        <constructor-arg name="monitoringType" value="METRICS"/>
    </bean>

Off-heap local storage
----------------------

The other ``localStorage`` implementation, ``co.paralleluniverse.galaxy.core.OffHeapLocalStorage``, stores data items in direct ``ByteBuffer`` s and manages
allocations and de-allocations. It manages blocks of fixed-sized memory pages, each block used for data items of certain sizes. When allocating memory for
an item of sized ``n``, the memory buffer returned will be the nearest power-of-two greater-or-equal to ``n``.

It has several configuration properties:

``monitoringType`` (constructor-arg, ``String``)
  Can be ``METRICS`` or ``JMX`` (see :ref:`man-config-monitoring`).

``pageSize`` (constructor-arg, ``int``)
  The size **in kilobytes** of each memory page used by the allocator.

``maxItemSize`` (constructor-arg, ``int``)
  The maximum size, in bytes of a single data item. Must be set to the same value as the ``maxItemSize`` property of the ``cache`` component.

``maxPagesForConcurrency`` (property, ``int``, default: ``Runtime.getRuntime().availableProcessors() * 2``)
  The maximum number of pages to allocate in each block simply for reducing contention (and not because memory is exhausted).

Here's an example:

.. code-block:: xml

    <bean id="localStorage" class="co.paralleluniverse.galaxy.core.OffHeapLocalStorage">
    	<constructor-arg name="monitoringType" value="METRICS"/> 
        <constructor-arg name="pageSize" value="1024"/>
        <constructor-arg name="maxItemSize" value="1024"/>
        <property name="maxPagesForConcurrency" value="4"/>
    </bean>


The amount of memory available for direct buffer is determined by the ``XX:MaxDirectMemorySize`` command line option passed to the JVM
(the ``java`` command). For example, to provide 512MB to direct ByteBuffers, you add the following command line option to the ``java`` command:

.. code-block:: sh

  -XX:MaxDirectMemorySize=100M

.. _man-config-cache-backup:

Configuring Backup
==================

The ``backup`` component is responsible for backing up the node's owned items after modifications to the server and/or slaves.
There is currently one implementation of ``backup`` - ``co.paralleluniverse.galaxy.core.BackupImpl"`` - and it has two configuration properties:

``monitoringType`` (constructor-arg, ``String``)
  Sets the monitor type to use for cache monitoring. Can be either ``METRICS`` or ``JMX`` (see :ref:`man-config-monitoring`).

``maxDelay`` (property, ``int``, default: ``10``)
  The maximum duration, *in milliseconds*, between flushes of backup data (to the server and/or slaves.) It is also roughly the maximum amount of time
  that can be "lost", i.e. updates that can disappear if the node goes down. If it's small, less updates can be lost in a case of failure, but both
  latency and throughput would suffer.

``serverComm` (constructor-arg, ``co.paralleluniverse.galaxy.core.ServerComm``, default: autowired)
  If you configure your cluster without a server, set this constructor-arg to ``null``(see :ref:`man-config-spring-values-null`). Otherwise,
  don't set it at all, and Spring will auto-wire it to whatever ``serverComm`` component you have defined (see :ref:`man-config-comm-common-servercomm`).

Here's a configuration example:

.. code-block:: xml

    <bean id="backup" class="co.paralleluniverse.galaxy.core.BackupImpl">
        <constructor-arg name="monitoringType" value="METRICS"/>
        <property name="maxDelay" value="200"/>
    </bean>


.. _man-config-cache-monitoring:

Monitoring the cache
====================

TBD
