.. _man-api-store:

###########
Data Store
###########

The grid's shared data store is accessed through the ``Store`` class (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html>`), 
which is used for all operations on data items.

.. contents::
    :depth: 2
    :local:
    :backlinks: none

.. _man-api-store-items:

Data items
==========

Galaxy data items are simple byte arrays, which are assigned a unique ``long`` identifier by the grid. You cannot choose the id
given to a data item (Galaxy is *not* a key value store), so you are responsible for storing the item ids in your object graph 
(you can think of item ids as the grid version of references). This is all because Galaxy is meant to be used to implement any kind 
of distributed data structure (you can implement a distributed map on top of Galaxy and thus build your own key-value store).

To get an item's id, you would either read it from another item (like following a reference), or read it from a message 
(see :ref:`man-api-messenger`). However, all cluster nodes need a way to easily find the root (or roots) of the object graph, and 
for this purpose, Galaxy provides root items.

Root items
----------

A root item is a data item which you'd like to access without knowing its id in advance. Roots are found using string identifiers
of your own choosing. When a root is first located, it will be allocated, but only by one of the nodes accessing it. So if several
cluster nodes are all accessing the same root, one will be responsible for initializing it (if it has not already been created before),
and the rest of the nodes will observe the initialized root. This ensures that any node will either find an initialized root, or
be assigned the task of initializing it (this will only happen once for each root).

Finding a root is done by calling  the ``getRoot`` method within a transaction 
(``getRoot`` is the only ``Store`` operation that requires a transaction. 
For all other operations, transactions are optional. Transactions are fully explained later in this chapter), 
like so:

.. code-block:: java

    long root = -1;
    StoreTransaction txn = store.beginTransaction();
    try {
        root = store.getRoot("myRootName", txn);
        if (store.isRootCreated(root, txn) 
            store.set(root, initialRootData(), txn); // initialize root
        store.commit(txn);
    } catch(Exception ex) {
        store.rollback(txn);
        store.abort(txn);
    }

Locating a root by its name can be costly, so only locate a root once (during application startup) and store its id for future
accesses.

.. note::
    Do not use the root mechanism as a general key-value store. Roots were designed to be accessed by their string identifiers
    only rarely (usually only when the application starts). Locating a root by its name is a costly operation.

Serialization and Persistables
------------------------------
In order to represent application objects, you can use any serialization mechanism, such as
``java.io`` serialization, `Protocol Buffers`_, Kryo_ or any other. However, for best serialization performance, it is best to 
have your data objects implement the ``Persistable`` interface.

The ``Persistable`` interface (:javadoc:`Javadoc <co/paralleluniverse/common/io/Persistable.html>`) 
provides direct access to galaxy's internal ``ByteBuffers``,
and eschews copying data to and from byte arrays. All of the ``Store``'s data methods (``get``, ``set``, ``put`` etc.)
have versions that work with ``Persistables``.
Just make sure never to modify the ``ByteBuffer``'s contents inside your implementation of ``Persistable``'s
``read`` method.

.. _`Protocol Buffers`: http://code.google.com/p/protobuf/
.. _Kryo: http://code.google.com/p/kryo/

.. _man-api-store-items-allocation:

Allocating items
----------------
Root items are allocated automatically. All other items must be allocated explicitly using one of the ``put`` methods or with
the ``alloc`` method.

The ``alloc`` method (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#alloc(int, co.paralleluniverse.galaxy.StoreTransaction)>`) 
allocates one or more items. 
The items allocated are empty (i.e. contain nulls), and can be set with one of the
``set`` methods. This method is mostly intended for allocating arrays - a block of items with consecutive ids. The return value is
the identifier for the first allocated item, with subsequent ids assigned to the following allocated items.

The ``put`` methods allocates a new item and sets its value 
(there are variants taking values of different types - array, ``ByteBuffer`` and ``Persistable``). 
It returns the newly allocated item id.

Reading items
-------------

To read an items value, simply pass its id to one of the ``get`` methods.

* To learn about reading items within transactions, see :ref:`man-api-store-transactions`.
* To learn about reading items asynchronously, see :ref:`man-api-store-async`.
* To learn about the effect `get` has over item ownership, see :ref:`man-api-store-ownership`.

Hinted reads
------------

Sometimes your application may know which node likely owns a certain item (say if this information was conveyed in a 
:ref:`message <man-api-messenger>`) or that an item is likely owned by the same node that currently owns a different item
(if this is how your distributed data structure behaves). In such cases, you can provide hints to the ``get`` method as to
the item's owner, which may sometimes be helpful in improving the running time of the operation (however, even if the hint is
wrong and the item is not, in fact, stored on the hinted node, the operation will still perform correctly and find the item 
wherever it is). 

Some variants of the ``get`` method take a ``nodeHint`` parameter (a ``short`` value) that names the (supposed) owning node.

The ``getFromOwner`` methods take a second item id that points to an item which is likely owned by the same node that owns
the requested item. Note that calling this method may only improve performance if the hinting item (the second parameter) is
found on the local node (and so its owner is already known).

Writing items
-------------

To write an item's value, use one of the ``set`` methods.

* To learn about writing items within transactions, see :ref:`man-api-store-transactions`.
* To learn about writing items asynchronously, see :ref:`man-api-store-async`.
* To learn about the effect `set` has over item ownership, see :ref:`man-api-store-ownership`.

Deleting items
--------------

An item can be deleted with the ``del`` method (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#del(long, co.paralleluniverse.galaxy.StoreTransaction>`).

Trying to access (``get`` or ``set``) a deleted item will result in an exception, but you should not rely on that to detect deleted 
items (making sure an item is deleted might be costly). Instead, try to only delete items when they are no longer "referenced" by
any other item (i.e., they are unreachable).

.. _man-api-store-ownership:

Item ownership
==============

As explained in the :ref:`introduction <man-intro-architecture>`, Galaxy is different from other IMDGs in that item ownership
can move between cluster nodes during normal operation. This will now be explained in further detail.

Owned items and shared items
----------------------------

Whenever you access a Galaxy data item in your application, it is sent to the cluster node your code is running on. The item
is then stored in RAM in one of two states: **owned** or **shared**.

Every Galaxy data item is **owned** by exactly one node at any point in time, but can be **shared** by many. All nodes
**sharing** an item can read its value, but only the **owning** node can write it. The owning node and sharing nodes for each
item change based on the operations the program performs. You can check whether an item is shared, owned or non-existent in 
any particular node by calling the ``getState`` method (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#getState(long)>`).

Sharing an item
---------------

When you call the ``get`` method (any of its variants), if the item is not found on the local node (in either a shared or an owned
state), it will be fetched from the owning node, and kept in RAM in the shared state, until the owning node invalidates it (when
the item value is changed). Any further reads (with ``get``) will complete immediately with no required network operations.

The ``gets`` method (all of its variants) is very similar to ``get``, except that the item will remain shared on the current node
until it is explicitly released. In other words, the item is *pinned* to this node in the shared state. You shouldn't keep the
item pinned for long, because as long as it's pinned to the local node, it's value cannot be changed by the owning node! 
(this is not exactly true - see :ref:`Inner Workings <man-api-store-ownership-inner>`)

To release a pinned item, you must call the ``release`` method (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#release(long)>`)
or use the ``gets`` method in a :ref:`transaction <man-api-store-transactions>`.

Owning an item
--------------

In order for an item to be written (with the ``set`` method), it must be owned by the local node, and it must not be shared by
any other node (we then say that the item is **exclusive** in the calling node). So, when you call the ``set`` method, ownership
of the item is transferred to the calling node, and all sharing nodes are asked to invalidate their copies of the item.

The ``getx`` method (all of its variants) reads an item's value, but first it obtains ownership over it, and invalidates all sharers.
In other words, it *pins* the item to the local node in the **exclusive** state. As long as the item is pinned, no other node can
*read or write* the item (this, too, is not exactly true - see :ref:`Inner Workings <man-api-store-ownership-inner>`), so you
should release it as soon as possible. ``getx`` is essentially a "get for write" operation, used to read the the item
with the intent to soon modify it with ``set``.

To release a pinned item, you must call the ``release`` method (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#release(long)>`)
or use the ``getx`` method in a :ref:`transaction <man-api-store-transactions>`.

.. _man-api-store-ownership-deadlock:

Deadlocks
---------

Because ``getx`` and ``gets`` pin an item to the local node until it is explicitly released, pinning more than one item can 
result in a deadlock. For example if node A pins item X in a shared mode (using ``gets``) and then wishes to pin item Y in the
exclusive mode (with ``getx``), while, at the same time node B pins Y in the shared mode and wishes to pin X in the exclusive mode,
a conflict may occur which will result in both nodes A and B unable to complete their operation. This is called a **deadlock**.

When deadlock occurs, the failed operation will throw a ``TimeoutException``. If this happens, you must undo all writes
that have succeeded to relevant items and release all pinned items in order to allow the other node to complete its operation. 
Then, you may retry the operation. 
:ref:`transactions <man-api-store-transactions>` make dealing with timeouts easier.

See :ref:`man-config-comm-common-timeout` for instructions on setting the timeout duration.

.. _man-api-store-ownership-inner:

.. note:: Inner Workings
  
    When ``set`` or ``getx`` are called, the caller does not actually wait for all sharers to invalidate the items before
    modifying it. Galaxy assumes that if a tree falls in the forest and no one is around to hear it, it does not make a sound,
    so some lengthy operations are allowed to proceed as long as no other node can have access to the item.
    
    Therefore, ``set`` or ``getx`` will complete before all sharers have invalidated their copies, but the item's new value
    will not be made available to other nodes until they do so. In fact, this is also done with high availability backup data
    (to the server or slave nodes). Writes do not wait for the server or slaves to acknowledge the backup, but other nodes cannot
    read the item's new value until the backup has been completed.
    
    Neither is it entirely true that items pinned in the exclusive mode (with ``getx``) cannot be read by other nodes. In fact
    Galaxy allows nodes to read an exclusively pinned item's old value (as it had been before it was pinned), provided that
    the item is found on that node (because it was once a sharer or an owner of the item), and provided that reading the value
    will not violate consistency guarantees. In any case, Galaxy never allows reading (or writing) an item in a way that will violate
    consistency.

.. _man-api-store-listeners:

Listeners
=========

You can listen for changes in an item's value by providing a listener to the method ``setListener`` 
(:javadoc:`Javadoc <co/paralleluniverse/galaxy/Store.html#setListener(long, co.paralleluniverse.galaxy.CacheListener)>`),
which will get notified of events pertaining to a specific item. Only one listener can be set for a given item, and it may be removed
by passing a ``null`` listener to ``setListener``.

A listener may be useful, say, for updating a deserialized representation of the item.

The listener implements three methods:

* ``received`` - called when a new value for the item has been received by the node when a ``get`` completes after the node's value
  has been changed by another node. Received will not be called when the value is modified by the local node, nor will it be called
  when another node updates the item, but the local node has not requested its value with a ``get`` (or ``gets/x``). 
  That is, the listener does   not listen for all modifications done to the item, only those which are of interest to this node, 
  namely only when a ``get/s/x`` has been issued.
* ``invalidated`` - called when the item's owner requested the item be invalidated by the local node (because it wants an exclusive
  ownership for an update). Note that this does not necessarily mean that the item may not be read by the local node, as sometimes
  Galaxy allows stale reads as long as they don't break consistency (see :ref:`Inner Workings <man-api-store-ownership-inner>`).
* ``evicted`` - called when the item has been evicted entirely from the local node, either because it was a shared item that was not
  accessed recently and Galaxy evicted it to conserve memory, because the item has been deleted, or because Galaxy has determined that 
  it can no longer be read without violating consistency.
  
.. _man-api-store-transactions:

Transactions
============

Transactions are used to make multi-item atomic operations easier to use. An atomic multi-item operation is one that potentially
modifies more than one item, and allows other nodes to observe the items' values either as they were before the transaction started
or as they are once the transaction has completed. Internally, transaction simply track which items were pinned, and allows releasing
all of them with one simple method call (remember, an item pinned with ``getx`` cannot be observed by other nodes).

A transaction is started with the ``beginTransaction`` method, completed with the ``commit`` or ``abort`` method, and is used so:

.. code-block:: java

    StoreTransaction txn = store.beginTransaction();
    try {
    	byte[] valX = store.gets(x, txn);
    	byte[] valY = store.getx(y, txn);
    	store.set(y, process(valX, valY), txn);
        store.commit(txn);
    } catch(TimeoutException e) {
    	store.rollback(txn); // or undo writes manually with a series of sets.
        store.abort(txn);
    }

Note how you must explicitly undo your changes if the transaction fails - either using ``rollback`` or manually using ``set``.
By default, transactions support the rollback operation, but this makes them slower (and consume more memory) as they must remember
items' old values. You can disable this "redo log" in the configuration file (See :ref:`man-config-cache`).

See :ref:`man-config-comm-common-timeout` for instructions on setting the timeout duration.

.. _man-api-store-async:

Asynchronous operations
=======================

Galaxy works best when most data operations access items that are already stored on the local node (see `man-api-store-performance`).
However, occasionally operations do require network hops (for ownership transfer etc.), and so may block.

The data-store API provides non-blocking versions to all data operations (called ``getAsync``, ``getsAsync``, ``getxAsync`` etc.)
that do not block, but instead return a ``Future``. This is especially useful (and will give a significant performance boost)
when performing several operations that don't each require the result of the previous one. In the worst case (when network IO is
required) this will result in all network requests being sent together instead of each being sent only after the previous has 
completed.

Here's an example:


.. code-block:: java

    ListenableFuture<byte[]> valX = store.getsAsync(x, txn);
    ListenableFuture<byte[]> valY = store.getxAsync(y, txn);
    store.set(y, process(valX.get(), valY.get()), txn); // this call is synchronous
    
When used in a transaction, ``commit`` (and ``abort``) will automatically wait for all futures returned within the transactions (and will
so guarantee they are all complete when the transaction ends.

.. code-block:: java

    StoreTransaction txn = store.beginTransaction();
    
    try {
    	ListenableFuture<byte[]> valX = store.getsAsync(x, txn);
    	ListenableFuture<byte[]> valY = store.getxAsync(y, txn);
    	store.setAsync(y, process(valX.get(), valY.get()), txn);
        store.commit(txn);
    } catch(TimeoutException e) {
    	store.rollback(txn); // or undo writes manually with a series of sets.
        store.abort(txn);
    }


.. _man-api-store-multithreading:

Multithreading
==============

All of ``Store``'s methods are thread safe, and the ``Store`` instance may safely be used by multiple threads. However, Galaxy
was built to provide inter-node synchronization - not intra-node synchronization - and so pinning an item to the local node
entails no locking. Meaning, an item that was pinned with ``getx`` on one thread, will result in ``getx`` succeeding immediately
when called from another. Even transactions (which are a thin management layer over pinning) will easily trample over each other
if they touch the same items on different threads. Any synchronization among threads (such as locking) must be done by the 
application (or another layer of middleware on top of Galaxy).

By leaving locking to the application, Galaxy provides a lot of flexibility. For example, if used carefully, several threads
may cooperate in running the same Galaxy transaction.

.. _man-api-store-performance:

Performance
===========

To fully enjoy Galaxy's low-latency processing, abide by the following advice:

* Reduce contention - just like in all distributed systems (and even inside your CPU), contention *invariably* requires communication
  and communication invariably increases latency. Try to avoid multiple nodes all competing to update the same items.
* The more nodes share an item, the less often it should be updated - even if an item is usually updated by the same node, if the item
  is shared (for read access), by a large number of nodes, updating it will increase latency (in the reader nodes, not the writer node).
* Trees are good - Tree data structures (like B-trees and tries) often have the property that the higher up a tree-node is, it will be
  shared more, but will be updated less often. That's a great property.
* Keep your transactions short - this will also reduce contention. Try not to do any blocking operation while in a Galaxy transaction.
* Use asynchronous operations when appropriate.
