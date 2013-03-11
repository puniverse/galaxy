.. _man-api:

############
Galaxy's API
############

Galaxy's Grid API (which is fully documented in the :javadoc:`Javadoc <index.html>`) is found in the package ``co.paralleluniverse.galaxy``. 

.. man-api-instance:

Getting the grid instance
=========================

To get an instance of the galaxy grid, represented by the ``Grid`` class (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Grid.html>`), simply call  

.. code-block:: java

    Grid grid = Grid.getInstance();

Usually, your next statement would be to tell the node to go online:

.. code-block:: java

    grid.goOnline();

See :ref:`man-api-cluster-organization` for more details about node states.

.. note::

   See :ref:`man-config` for information about configuring the grid.
    
Grid services
=============

The Galaxy grid provides three services:

.. toctree::
    :maxdepth: 1

    api-store
    api-messenger
    Cluster Management <api-cluster>
    
The :ref:`data store <man-api-store>` service (accessed through the interface ``Store``) provides all operations on grid data items.
To obtain an instance of the ``Store``, call

.. code-block:: java

    Store store = grid.store();

The :ref:`messenger <man-api-messenger>` service (accessed through the interface ``Messenger``) allows sending point-to-point 
messages over the grid that work in concert with the data store service.
You can obtain an instance of the ``Messenger``, by calling

.. code-block:: java

    Messenger messenger = grid.messenger();

The :ref:`cluster <man-api-cluster>` service (accessed through the ``Cluster`` interface), is used internally by Galaxy to manage 
the cluster (handle membership changes, leader election etc.) but can be used by client application as well. 
It provides a nice mechanism for sharing configuration data among nodes.
The ``Cluster`` instance is obtained so:

.. code-block:: java

    Cluster cluster = grid.cluster();


