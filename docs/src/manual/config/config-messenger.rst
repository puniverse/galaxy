.. _man-config-messenger:

########################################
Configuring and Monitoring the Messenger
########################################

The Messenger component (bean: ``messenger``) is responsible for sending and receiving user messages on the grid
(see :ref:`man-api-messenger`).

.. _man-config-messenger-config:

Configuring the messenger
=========================

``messenger`` has only one implementation - ``co.paralleluniverse.galaxy.core.MessengerImpl``, and it takes just one configuration property:
``threadPool`` (property, ``co.paralleluniverse.galaxy.core.NodeOrderedThreadPoolExecutor``, required). ``NodeOrderedThreadPoolExecutor``
is a special king of ``java.util.concurrent.ThreadPoolExecutor``, and to learn more about configuring it, please see :ref:`man-config-misc-threadpool`
for instructions on how to configure a thread-pool, or just take a look at this example:

.. code-block:: xml

    <bean id="messenger" class="co.paralleluniverse.galaxy.core.MessengerImpl">
        <constructor-arg name="threadPool">
            <bean class="co.paralleluniverse.galaxy.core.NodeOrderedThreadPoolExecutor">
                <constructor-arg name="corePoolSize" value="2"/>
                <constructor-arg name="maximumPoolSize" value="8"/>
                <constructor-arg name="keepAliveTime" value="5000"/>
                <constructor-arg name="unit" value="MILLISECONDS"/>
                <constructor-arg name="maxQueueSize" value="500"/>
                <constructor-arg name="workQueue">
                    <bean class="co.paralleluniverse.common.concurrent.SimpleBlockingQueue" c:maxSize="500"/>
                </constructor-arg>
            </bean>
        </constructor-arg>
    </bean>
