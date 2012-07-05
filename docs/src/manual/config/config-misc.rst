.. _man-config-misc:

#############################
Miscellaneous Configurations
#############################


.. _man-config-misc-threadpool:

Thread pools
============

Galaxy makes extensive use of thread-pools.

.. _man-config-misc-threadpool-config:

Configuring a thread-pool
-------------------------

Several Galaxy components require you to provide a thread-pool in the form of a ``java.util.concurrent.ThreadPoolExecutor`` 
instance or a subclass of it.

These are ``ThreadPoolExecutor``'s constructor-args:

``corePoolSize``, index:0, ``int``
  The number of threads to keep in the pool, even if they are idle.

``maximumPoolSize``, index:1, ``int``
  The maximum number of threads to allow in the pool.

``keepAliveTime``, index:2, ``long``
  When the number of threads is greater than the core, this is the maximum time that excess idle threads 
  will wait for new tasks before terminating.

``unit``, index:3, ``java.util.concurrent.TimeUnit``
   The time unit for the ``keepAliveTime`` argument.
   Can be ``NONOSECONDS``, ``MICROSECONDS``, ``MILLISECONDS``, ``SECONDS``, ``MINUTES``, ``HOURS`` or ``DAYS``.

``workQueue``, index:4, ``java.util.concurrent.BlockingQueue``
  The queue to use for holding tasks before they are executed. 

For the ``workQueue`` argument, is is best to use `an instance of ``co.paralleluniverse.common.concurrent.QueueFactory``
which constructs the queue most appropriate for the given maximum size. It is defined like this:

.. code-block:: xml

  <bean class="co.paralleluniverse.common.concurrent.QueueFactory" factory-method="getInstance" c:maxSize="500"/>

And there are two special values for ``maxSize``. ``-1`` designates an unbounded queue, and ``0`` specifies a handoff "queue"
where each producer must wait for a consumer (i.e. there can be no tasks waiting in the queue).

Here's an example of an inner bean (:ref:`man-config-spring-values-inner`) defining a ``ThreadPoolExecutor``:

.. code-block:: xml

    <bean class="java.util.concurrent.ThreadPoolExecutor">
        <constructor-arg index="0" value="2"/>
        <constructor-arg index="1" value="8"/>
        <constructor-arg index="2" value="5000"/>
        <constructor-arg index="3" value="MILLISECONDS"/>
        <constructor-arg index="4">
            <bean class="co.paralleluniverse.common.concurrent.QueueFactory" factory-method="getInstance" c:maxSize="500"/>
        </constructor-arg>
    </bean>

Galaxy provides a convenience class that is a bit simpler to declare, which can be used instead of ``java.util.concurrent.ThreadPoolExecutor``
(**but not when a specific subtype is required!**)

.. code-block:: xml

    <bean class="co.paralleluniverse.galaxy.core.ConfigurableThreadPool">
        <constructor-arg name="corePoolSize" value="2"/>
        <constructor-arg name="maximumPoolSize" value="8"/>
        <constructor-arg name="keepAliveMillis" value="5000"/>
        <constructor-arg name="maxQueueSize" value="500"/>
    </bean>

Some Galaxy components may ask for a ``co.paralleluniverse.galaxy.core.NodeOrderedThreadPoolExecutor``, which is 
configured so:

.. code-block:: xml

    <bean class="co.paralleluniverse.galaxy.core.NodeOrderedThreadPoolExecutor">
        <constructor-arg name="corePoolSize" value="2"/>
        <constructor-arg name="maximumPoolSize" value="8"/>
        <constructor-arg name="keepAliveTime" value="5000"/>
        <constructor-arg name="unit" value="MILLISECONDS"/>
        <constructor-arg name="maxQueueSize" value="500"/>
        <constructor-arg name="workQueue">
            <bean class="co.paralleluniverse.common.concurrent.QueueFactory" factory-method="getInstance" c:maxSize="500"/>
        </constructor-arg>
    </bean>

.. _man-config-misc-threadpool-monitor:

Monitoring thread-pools
-----------------------

All of Galaxy's thread-pools expose monitoring information using MBeans. All of these MBeans are named 
``co.paralleluniverse:type=ThreadPoolExecutor,name=POOL_NAME``, and can be found in **VisualVM** or **JConsole** in the
MBean tree under the ``co.paralleluniverse/ThreadPoolExecutor`` node.


