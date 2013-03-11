.. title:: Home

.. raw:: html

    <div class="hero-unit">

###########################################################
Galaxy: A High-Performance In-Memory Data-Grid
###########################################################

Galaxy is a high-performance in-memory data-grid (IMDG) that can serve as a basis for building distributed applications
that require fine-tuned control over data placement and/or custom distributed data-structures. 

What makes Galaxy different from other IMDGs is the way it assigns data items to cluster node. Instead of sharding the data
on one of the keys using a consistent hashing scheme, Galaxy dynamically moves  objects from one node to another as needed
using a cache-coherence protocol similar to that found in CPUs. This makes Galaxy suitable for applications with predictable 
data access patterns, i.e. applications where the data items behave according to some proximity metric, and items that are
"closer" together are more likely to be accessed together than items that are "far" apart.

Galaxy is not a key-value store, though it can be used to build one. It's meant to be used as a low-level platform for 
building distributed data structures.

Galaxy uses **ZooKeeper** or **JGroups** for cluster management, and **BerkeleyDB** or **any SQL database** for optional 
persistence.

.. attention::

  Galaxy is currently in ALPHA and considered experimental.

  Please submit bug reports and feature requests to the `issue tracker`_.


Galaxy is developed by `Parallel Universe`__ and released as free software under the GNU Lesser General Public License.

.. image:: _static/lgplv3-147x51.png

.. __: http://paralleluniverse.co
.. _`issue tracker`: https://github.com/puniverse/galaxy/issues

.. toctree::
   :maxdepth: 1

   news
   about
   start/getting-started
   manual/index

.. raw:: html

    </div>