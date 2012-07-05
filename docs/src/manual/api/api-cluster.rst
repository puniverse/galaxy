.. _man-api-cluster:

########
Cluster
########

The ``Cluster`` interface (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Cluster.html>`) provides information
about cluster nodes, and emits cluster events (like nodes joining or leaving the cluster). It also allows for easy configuration
management.

While an application employing Galaxy does not have to use this service, it is very useful for all clustered applications. And
while the application is free to use any other software for cluster management, this service is being used internally by Galaxy,
so when this service detects a node going down - that's when the data-store and messenger detect is as down, as well.

Internally, this service employs either `Apache ZooKeeper`_ (through Netflix's Curator_ library) or JGroups_ 
(see :ref:`man-config-cluster` for configuring Galaxy to use either option).
You can gain access to the underlying implementation by calling the ``getUnderlyingResource`` method 
(:javadoc:`Javadoc <co/paralleluniverse/galaxy/Cluster.html#getUnderlyingResource()>`)
which will return the ``CuratorZookeeperClient`` used by Galaxy for cluster management (which, in turn, can be further queried for
the underlying ``ZooKeeper`` instance) or the JGroups ``JChannel`` used for cluster management.

.. _`Apache ZooKeeper`: http://zookeeper.apache.org/
.. _`Curator`: https://github.com/Netflix/curator
.. _JGroups: http://www.jgroups.org/

.. _man-api-cluster-organization:

Cluster organization
====================

Every Galaxy node in the cluster has a unique name (a ``String``) which is automatically assigned to it, and an id (a ``short``
value) which is assigned in the local configuration file (see :ref:`man-config-cluster`) and is not unique (it is shared among
all nodes in a backup group (See :ref:`man-api-cluster-organization-backup`). 
Node id ``0`` designates server nodes (see :ref:`man-api-cluster-organization-server`).

In addition, every node may expose additional properties (such as IP addresses, ports or any other information) to all other nodes
in the cluster. Some properties are used internally by Galaxy, but you can add your own (see :ref:`man-api-cluster-info-properties`).

Each node is, at any given moment, in one of three states: **offline**, **joined** or **online**.

* An **offline** node is one that is not seen by the other nodes in the cluster, either because it is turned off, disconnected
  from the network, or suffering from a software failure.
* A **joined** node is one which is seen by all other nodes, but does not participate in the data-grid, i.e. it cannot receive
  messages or data-items. All it can do is observe other nodes' state.
* An **online** node is one which fully participates in the grid, either as a master or a slave 
  (see :ref:`man-api-cluster-organization-backup`).

When your application first starts, its node is in the offline state. When you get a grid instance (by calling ``Grid.getInstance()``)
that's when the node will try to join the cluster, and become **joined**.

To go online call ``grid.goOnline()``.

.. _man-api-cluster-organization-server:

Server node
-----------

A server node (or nodes) is a special Galaxy node that doesn't run application code, but is responsible for providing Galaxy data
with disk-based persistence. All of the data in the grid is persisted to disk on the server node, and is also automatically served
by it in case of a node failure. 

The node server always has a node id of ``0``, and, just like with regular nodes, you can have several server nodes in a backup groups
configuration for added availability.

See :ref:`man-config-cluster-common` and :ref:`man-config-server` for more information about configuring and running server node(s).

.. _man-api-cluster-organization-backup:

Backup groups
-------------

Galaxy nodes can be configured in backup groups. All nodes assigned the same node id in their configuration file will become part 
of the same backup group. At any given time, each live backup group has exactly one **master** node, and zero or more **slave**
nodes. The master node replicates all of its owned data items to its slaves so that they can take over in case it fails.

When the master node fails, one of its slaves will become the master and take over. However, a master node *can never become a slave*.
A slave node is notified of its new master status by an event (see :ref:`man-api-cluster-lifecycle`).

See :ref:`man-config-cluster-common` for information about configuring backup groups.

.. _man-api-cluster-lifecycle:

Lifecycle events
================

You can listen for important lifecycle events with the ``addLifeCycleListener`` to which you pass your ``LifeCycleListener``
(`:javadoc:`Javadoc <co/paralleluniverse/galaxy/cluster/LifecycleListener.html>`). The listener will be notified
of the following events:

* ``online`` - This event will get triggered when the node reaches the **online** state. The single boolean parameter tells
  the node is a master or a slave (see :ref:`man-api-cluster-organization-backup`).
* ``offline`` - This event will get triggered when the node goes offline for some reason. Obviously, if the node goes offline
  due to a power failure, this event will not be triggered.
* ``switchToMaster`` - This event will get triggered at a slave node when it becomes the master (when the previous master has gone
  offline.

You can also check the current state of the local node with the methods ``isOnline`` and ``isMaster``.

You remove a lifecycle listener by calling ``removeLifecycleListener``.

.. _man-api-cluster-events:

Cluster events
==============

The ``Cluster`` service also sends notifications about occuronces in other cluster nodes. 

If you hand a ``NodeChangeListener`` (:javadoc:`Javadoc <co/paralleluniverse/galaxy/cluster/NodeChangeListener.html>`) 
to ``addNodeChangeListener`` you'll be notified when a new node (actually, a backup group) comes online, when it goes offline,
and when it has a master switchover.

A ``SlaveConfigurationListener`` (:javadoc:`Javadoc <co/paralleluniverse/galaxy/cluster/SlaveConfigurationListener.html>`) 
passed to ``addSlaveConfigurationListener`` will notify you when a slave is added to or removed from your local node's backup group.

.. _man-api-cluster-info:

Node information
================

The ``Cluster`` interface has methods that return information about the local node and all other cluster nodes.

The ``getMyNodeId`` method returns the local node's id, and the ``getNodes`` method returns the ids of all online nodes (actually
all online backup groups. Remember, the node id is shared by all nodes in the group).

NodeInfo
--------
The ``NodeInfo`` interface (:javadoc:`Javadoc <co/paralleluniverse/galaxy/cluster/NodeInfo.html>`)
let's you access the configuration record published by any node in the cluster.

``getMyNodeInfo`` returns the ``NodeInfo`` of the local node, so by calling

.. code-block:: java

    grid.cluster().getMyNodeInfo().getName()
    
you can get the local node's unique name.

There are other methods in the ``Cluster`` interface that let you access the ``NodeInfo`` of just about any node in the cluster.
Refer to the Javadoc for more information.

.. _man-api-cluster-info-properties:

Custom properties
-----------------

Other than its name and id, each node's configuration record can hold any number of additional properties, which can be accessed
by ``NodeInfo``'s ``get`` or ``getProperties`` methods.

You can add a custom node property, and additionally require that nodes that have not yet publish a value for the property will not be 
in considered **online** until they do, with the ``addNodeProperty`` method (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Cluster.html#addNodeProperty(java.lang.String, boolean, boolean, co.paralleluniverse.galaxy.cluster.ReaderWriter)>`).
Refer to the Javadoc for more information.

The ``setNodeProperty`` method lets you set a property of the local node.

The ``addMasterNodePropertyListener`` (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Cluster.html#addMasterNodePropertyListener(java.lang.String, co.paralleluniverse.galaxy.cluster.NodePropertyListener)>`)
and ``addSlaveNodePropertyListener`` (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Cluster.html#addSlaveNodePropertyListener(java.lang.String, co.paralleluniverse.galaxy.cluster.NodePropertyListener)>`)
methods allow you to listen for changes in the values of configuration record properties of any master node in the cluster 
or any of your slave nodes respectively.

.. _man-api-cluster-tree:

The distributed tree
====================

Galaxy nodes publish their configuration record via a service called ``DistributedTree`` (:javadoc:`Javadoc <co/paralleluniverse/galaxy/cluster/DistributedTree.html>`) which implements a distributed filesystem-like tree of information nodes
(very similar to the way Apache ZooKeeper works, and in fact, when the cluster is configured to use ZooKeeper, the ``DistributedTree`` is just a thin layer on top of it).
``DistributedTree`` makes some strong consistency and ordering guarantees about it's data, which make it suitable for sharing critical configuration data and for
coordination tasks among nodes, such as leader election. Galaxy's ``NodeInfo`` records simply wrap the ``DistributedTree`` nodes in one of its branches (directories).

If you'd like direct access to the tree, you can obtain the ``DistributedTree`` instance by calling the ``getDistributedTree`` method.

