---
layout: default
title: Galaxy
description: "Galaxy is a cache-coherent in-memory data grid for horizontal scalability."
---

# Overview

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

{:.alert .alert-warn}
**Note**: Galaxy is currently in ALPHA and considered experimental. Please submit bug reports and feature requests to the [issue tracker](https://github.com/puniverse/galaxy/issues).

Quasar is developed by [Parallel Universe] and released as free software, dual-licensed under the Eclipse Public License and the GNU Lesser General Public License.

[Parallel Universe]: http://paralleluniverse.co

## News

### July 23, 2014

Galaxy 1.4 has been released.

### January 22, 2014

Galaxy 1.3 has been released.

### August 9, 2012

[Galaxy's Networking](http://blog.paralleluniverse.co/post/29085615915/galaxy-internals-part-3). Part 3 of the Galaxy Internals blog post series.

### August 3, 2012

[How Galaxy Handles Failures](http://blog.paralleluniverse.co/post/28635713418/how-galaxy-handles-failures). Part 2 of the Galaxy Internals blog post series.

### July 26, 2012

[How Galaxy Maintains Data Consistency in the Grid](http://blog.paralleluniverse.co/post/28062434301/galaxy-internals-part-1).

### July 10, 2012

The first public version of Galaxy, **1.0-alpha1**, has been released.

See the announcement [on our blog](http://blog.paralleluniverse.co/post/26909672264/on-distributed-memory).

# Getting Started

### System requirements

Galaxy requires that the Java Runtime Environment (JRE) version 7 will be installed on your machine.

### Downloading Galaxy

You can download the Galaxy distribution from here:
[Release 1.0](http://paralleluniverse.co/docs/galaxy-1.0-SNAPSHOT.zip)

### Building Galaxy

To build galaxy, simply `cd` to the Galaxy directory, then run:

~~~ sh
gradle
~~~

If you don't have gradle installed on your machine then run instead:

~~~ sh
./gradlew
~~~

To build the documentation, you need to have [Sphinx] and [lessc] installed. Then run:

~~~ sh
gradle generateDocs
~~~

[Sphinx]: http://sphinx.pocoo.org/
[lessc]: http://lesscss.org/

 
### Using Maven

Add the following dependency to Maven:

~~~ xml
<dependency>
    <groupId>co.paralleluniverse</groupId>
    <artifactId>galaxy</artifactId>
    <version>{{site.version}}</version>
</dependency>
~~~

### Using the pre-built configurations {#pre-built}

The user manual explains, at length, how to configure Galaxy (see [Configuring and Monitoring Galaxy](#config)). However, for your convenience, a number of pre-built configurations are included with the Galaxy distribution, and can be found in the `config` directory.

While normally you'd have one large XML file with Galaxy's configuration, these sample configurations are divided into snippet XML files (those XML files starting with an underscore in the `config` directory) that you can mix and match.

#### Peer configuration

Regular Galaxy cluster nodes are called "peers", and to configure a peer, take a look at `peer.xml` in the `config` directory. It contains three or four XML `import` elements:

1. `_peer.xml`. This import is required for all peer configurations.

2. Cluster management product. Can be one of:
+ `_jgroups.xml` - to use JGroups.
+ `_zookeeper.xml` - to use Apache ZooKeeper. If selected, configure `zkConnectString` in `_zookeeper.xml` to match your ZooKeeper configuration.

3. One of: 
    * `_with_server.xml` - if you'd like your cluster to have a server node for persistence (see [Configuring, Running and Monitoring the Server](#config-server) for an explanation about servers).
    * `_with_cloud_server.xml` - if you want a server but are running Galaxy in a cloud environment that does not allow multicast. In that case, you must use `_zookeeper.xml`.* `_with_dumb_server` - if you'd like to use a server that isn't a Galaxy node but a simple SQL database for persistence.
     In this case, you must also import `_sql.xml` as item 4 (see [Dumb servers](#config-server-dumb) for an explanation about dumb servers).
    * `_no_server.xml`- if you don't want a server at all.

4. Possibly `_sql.xml`, if and only if you've chosen `_with_dumb_server`.If chosen, edit `_sql.xml` with your database connection information. See [Using SQL](#config-server-store-jdbc) for more information about configuring the RDBMS integration.

{:.alert .alert-info}
**Note**: It is recommended that you configure your cluster to use a server node or a dumb server. See [Should you use a server?](#config-cluster-organization-serverless).

In addition to this file, you'll need to edit an additional `.properties` file. You must provide these properties (you can edit `peer.properties` in the `config` directory):

1. `galaxy.nodeId` - this will identify the node in the cluster. Two or more nodes with the same id will form a "backup group" (see [Backup groups](#api-cluster-organization-backup)).

2. `galaxy.port` - this is the UDP port that Galaxy will use to send messages among peer nodes.

3. `galaxy.slave_port` - if there are more than one nodes with the same id, the slaves of the peer group will connect to this TCP port on the master to receive backup data.

4. `galaxy.multicast.address` - the IP address to use for multicast (not used when `_with_cloud_server` is chosen).

5. `galaxy.multicast.port` - the IP port to use for multicast (not used when `_with_cloud_server` is chosen).

Properties 4 and 5 must be the same for all peer nodes. Properties 2 and 3 may be different for each node (this is especially useful when running several nodes on the same machine for testing). The `nodeId` property should be different or each node (but the same for nodes in the same backup group).

{:.alert .alert-info}
**Note**: If you create several peer properties files with different ports, you can run several peers on a single machine!

#### Server configuration

If you heed our recommendation and want to use Galaxy with a server node (or more, though, currently, only a single server node is supported), you're going to need to configure it. Just like with peers, you should start by looking at the `server.xml` file that's in the `config` directory. It is comprised of three XML `import` elements:

1. `_server.xml`. This import is required for all peer configurations.

2. Cluster management product. Can be one of:
* `_jgroups.xml` - to use JGroups.
* `_zookeeper.xml` - to use ZooKeeper. If selected, configure `zkConnectString` in `_zookeeper.xml` to match your ZooKeeper configuration.

3. Persistence layer. Can be one of:
* `_bdb.xml` - to use Berkeley DB, Java Edition as the persistence engine. If you choose to use BDB JE, you might want to change the `envHome` property, defined in the
`_bdb.xml` file, to point to the directory where you want to place the BDB files, and the `truncate` property (which can be `true` or `false`) depending on whether or not you want the database truncated (cleared) upon server startup.
See [Using BerkeleyDB](#config-server-store-bdb) for more information about configuring BDB.
* `_sql.xml` - to use a SQL database with a JDBC driver for persistence.If chosen, edit `_sql.xml` with your database connection information.
See [Using SQL](#config-server-store-jdbc) for more information about configuring the RDBMS integration.
Because you're using a server node and peers would need to access it over the network, it's best that you runthe server on the same machine running the DB to save the extra network hop. This should actually provide better performance than using the DB as a dumb server as explained above.

For the server, too, you'll need to edit `server.properties` in the `config` directory. Leave `galaxy.nodeId` set to `0` - this is what identifies the node as the server. But set `galaxy.port` to the TCP port you want the peers to use when connecting to the server.

### Running Galaxy

The Galaxy server runs as a standalone process. The peers are your application code that calls into the Galaxy library. Note that if you're using ZooKeeper, you must start the ZooKeeper servers before starting any Galaxy nodes.

#### Running the server

To run the server, simply run the executable Java class `co.paralleluniverse.galaxy.Server`, and pass it two command-line arguments: the XML configuration file and the properties file, like so:

~~~ sh
java -classpath galaxy.jar co.paralleluniverse.galaxy.Server config/server.xml config/server.properties
~~~

#### Using the peers

In your application code, you need to get an instance of the `Grid` [class](#api) , which is the entry point to Galaxy's API. You do it by calling the `getInstance` static method, and passing it the location of the XML and properties files you have configured in the [configuration step](#pre-built), like this:

~~~ java
Grid grid = Grid.getInstance("config/peer.xml", "config/peer1.properties");
~~~

(depending on your current directory you may need to provide a different path to the XML and properties files.)

Usually, your next statement would be to tell the node to go online:

~~~ java
grid.goOnline();
~~~

Now you should read the [API section](#api) of the manual to learn how to use the Galaxy API.

# User Manual

This document explains how to use and configure Galaxy. Running Galaxy is about equal parts configuration and API so you're
advised to read this manual in its entirety.
                    
## Introduction to Galaxy

Galaxy is an in-memory data-grid. It's main function is to distribute data objects (stored as simple byte arrays) among cluster 
nodes for distributed processing. It also provides point-to-point messaging with guaranteed delivery and guaranteed ordering, as well as a cluster configuration
management service.

### Galaxy Features and Architecture {#intro-architecture}

**RAM storage and code/data co-location**

Application code runs on the same cluster nodes (called **peer nodes**), and processes the data objects which are kept in RAM. Unlike other IMDGs that partition data items, and distribute them in such a way that each object is the responsibility of a single node, Galaxy nodes exchange messages to transfer ownership of objects from one node to another. This means that if the application code in one of the nodes regularly updates some data items, those items will be owned by the node, and will be available for processing and update without any network I/O. As the application runs, items will migrate from one node to another (though items can reside on several nodes at once for read-only access). This gives the application precise control over the location of the data in the cluster for maximum performance. 

This flexibility, however, is suitable when data access patterns can be predicted and so the data can be optimally placed. If data access is completely random, other IMDGs may be a better choice as they can retrieve or update any item at a cost of one network hop, while Galaxy might need to search the cluster for an unexpected item access.
  
Because the application code is co-located with the data, and because all of the data is kept in RAM, Galaxy is suitable for low-latency applications.
  
**Consistency**

Galaxy stores data in a fully consistent manner, meaning that if data item B has been modified after a change to data item A, no node in the cluster will see A's new value but B's old one.
  
Galaxy achieves consistency by using a [cache-coherence protocol](http://en.wikipedia.org/wiki/Cache_coherence) similar tothe protocols used to coordinate memory access among CPU cores. However, as Galaxy can guarantee the ordering of coordination messages between nodes no memory-fence operations are requires (as they are in CPUs) to achieve consistency.
  
**Disk persistence and server nodes**

The data items can optionally be persisted to disk on one or more special nodes called [server nodes](#config-server).
  
However, in order to keep latency low, Galaxy (even when configured to use a persistent server node), is not durable. This means that a failure in one or more of the nodes may result in a permanent loss of some *recent* updates. However, even in cases of such failures the data remains consistent, meaning the lost updates will be lost to all of the nodes (or to none).
  
**High Availability**

Galaxy can withstand a failure in one or more of the nodes, providing high-availability. This is achieved by either running Galaxy with a server node (which persists all of the grid data to disk) or by running a slave node (or more) for each of the peers, or both.
  
If only a server node is used, data is not lost when a peer node fails (except for possibly some recent updates as explained above), and all the data items owned by the failed node are still accessible. However, as the server reads those items from the disk, latency might suffer until all items have been accessed by the peers and kept in RAM.

Alternately, or in combination with a server, you can run one or more slave-nodes for each of the peers, that mirror the data stored in them, so that upon failure of a peer, one of its slaves will take over, already having all of the necessary data items in RAM.
  
A server node may also have slaves that will take over when it fails.
  
**Messaging**

Galaxy provides a point-to-point messaging service that guarantees message delivery and ordering. A message can be sent to a known node or to the unknown (to the application) owner of a given data item. So if Galaxy's data-item migration makes moving data to code simple, Galaxy's messages make moving an operation (code) to data just as simple.
  
The application messages are delivered by the same communication channel that delivers Galaxy's internal coherence protocol messages, so messages are also  guaranteed to be ordered with data operations. Say that node A updates data item X, and then sends a message to the owner of data item Y (which happens to be node B), as a result of which node B reads the value of X. In this case node B is guaranteed to read the value of X after the update done by A before sending the message.
  
**Monitoring**

All of Galaxy's components are monitored to enable full diagnoses of failure or performance problems.
  
Galaxy's aim is to give the application full control over data location and processing in the grid, and in-order to provide maximal flexibility with a simple API, it is relatively low-level. It provides no query mechanism whatsoever, imposes no structure on the data (which is kept in memory and on disk as byte arrays), and provides no locking of elements to coordinate complex transactions among different threads on a single node (although each operation is atomic by itself). All of that must be provided
by the application.

{:.alert .alert-info}
**Note**: Use data-store operations to move data to code; use messages to move code to data.

### Galaxy Internals

A series of blog posts detailing Galaxy's inner workings can be found here: 
[part 1](http://blog.paralleluniverse.co/post/28062434301/galaxy-internals-part-1), 
[part 2](http://blog.paralleluniverse.co/post/28635713418/how-galaxy-handles-failures), 
[part 3](http://blog.paralleluniverse.co/post/29085615915/galaxy-internals-part-3).

### Tips for Achieving High-Performance with Galaxy

* Reduce contention - just like in all distributed systems (and even inside your CPU), contention *invariably* requires communication
  and communication invariably increases latency. Try to avoid multiple nodes all competing to update the same items.
* The more nodes share an item, the less often it should be updated - even if an item is usually updated by the same node, if the item
  is shared (for read access), by a large number of nodes, updating it will increase latency (in the reader nodes, not the writer node).
* Trees are good - Tree data structures (like B-trees and tries) often have the property that the higher up a tree-node is, it will be
  shared more, but will be updated less often. That's a great property.
* Keep your transactions short - this will also reduce contention. Try not to do any blocking operation while in a Galaxy transaction.
* Consider configuring your Ethernet network to use jumbo frames - if your network supports it, jumbo frames may improve communication
  speed among Galaxy nodes.

## Galaxy's API {#api}

Galaxy's Grid API (which is fully documented in the [Javadoc](http://puniverse.github.io/galaxy/javadoc/index.html)) is found in the package `co.paralleluniverse.galaxy`. 

### Getting the grid instance

To get an instance of the galaxy grid, represented by the `Grid` class ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Grid.html)), simply call  

~~~ java
Grid grid = Grid.getInstance();
~~~

Usually, your next statement would be to tell the node to go online:

~~~ java
grid.goOnline();
~~~

See [Cluster organization](#api-cluster-organization) for more details about node states.

{:.alert .alert-info}
**Note**: See [Configuring and Monitoring Galaxy](#config) for information about configuring the grid. 
    
### Grid services

The Galaxy grid provides three services:

* [Data Store](#api-store) 
* [Messenger](#api-messenger) 
* [Cluster Management](#api-cluster) 
    
The [data store](#data-store) service (accessed through the interface `Store`) provides all operations on grid data items. To obtain an instance of the `Store`, call

~~~ java
Store store = grid.store();
~~~

The [messenger](#api-messenger) service (accessed through the interface `Messenger`) allows sending point-to-point messages over the grid that work in concert with the data store service. You can obtain an instance of the `Messenger`, by calling

~~~ java
Messenger messenger = grid.messenger();
~~~

The [cluster](#api-cluster) service (accessed through the `Cluster` interface), is used internally by Galaxy to manage the cluster (handle membership changes, leader election etc.) but can be used by client application as well. It provides a nice mechanism for sharing configuration data among nodes. The `Cluster` instance is obtained so:

~~~ java
Cluster cluster = grid.cluster();
~~~

#### Data Store {#api-store}

The grid's shared data store is accessed through the `Store` class ([Javaoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html))
which is used for all operations on data items.

##### Data items {#api-store-items}

Galaxy data items are simple byte arrays, which are assigned a unique `long` identifier by the grid. You cannot choose the id given to a data item (Galaxy is *not* a key value store), so you are responsible for storing the item ids in your object graph (you can think of item ids as the grid version of references). This is all because Galaxy is meant to be used to implement any kind of distributed data structure (you can implement a distributed map on top of Galaxy and thus build your own key-value store).

To get an item's id, you would either read it from another item (like following a reference), or read it from a message 
(see [Messenger](#api-messenger)). However, all cluster nodes need a way to easily find the root (or roots) of the object graph, and for this purpose, Galaxy provides root items.

###### Root items

A root item is a data item which you'd like to access without knowing its id in advance. Roots are found using string identifiers of your own choosing. When a root is first located, it will be allocated, but only by one of the nodes accessing it. So if several cluster nodes are all accessing the same root, one will be responsible for initializing it (if it has not already been created before), and the rest of the nodes will observe the initialized root. This ensures that any node will either find an initialized root, or be assigned the task of initializing it (this will only happen once for each root).

Finding a root is done by calling  the `getRoot` method within a transaction (`getRoot` is the only `Store` operation that requires a transaction. For all other operations, transactions are optional. Transactions are fully explained later in this chapter), like so:

~~~ java
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
~~~

Locating a root by its name can be costly, so only locate a root once (during application startup) and store its id for future accesses.

{:.alert .alert-warn}
**Note**: Do not use the root mechanism as a general key-value store. Roots were designed to be accessed by their string identifiers only rarely (usually only when the application starts). Locating a root by its name is a costly operation.

###### Serialization and Persistables

In order to represent application objects, you can use any serialization mechanism, such as `java.io` serialization, [Protocol Buffers], [Kryo] or any other. However, for best serialization performance, it is best to have your data objects implement the `Persistable` interface.

The `Persistable` interface ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/common/io/Persistable.html)) provides direct access to galaxy's internal `ByteBuffers`, and eschews copying data to and from byte arrays. All of the `Store`'s data methods (`get`, `set`, `put` etc.) have versions that work with `Persistables`.
Just make sure never to modify the `ByteBuffer`'s contents inside your implementation of `Persistable`'s
`read` method.

[Protocol Buffers]: http://code.google.com/p/protobuf/
[Kryon]: http://code.google.com/p/kryo/

###### Allocating items {#api-store-items-allocation}

Root items are allocated automatically. All other items must be allocated explicitly using one of the `put` methods or with the `alloc` method.

The `alloc` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#alloc(int, co.paralleluniverse.galaxy.StoreTransaction)))
allocates one or more items. The items allocated are empty (i.e. contain nulls), and can be set with one of the`set` methods. This method is mostly intended for allocating arrays - a block of items with consecutive ids. The return value is the identifier for the first allocated item, with subsequent ids assigned to the following allocated items.

The `put` methods allocates a new item and sets its value (there are variants taking values of different types - array, `ByteBuffer` and `Persistable`). 
It returns the newly allocated item id.

###### Reading items

To read an items value, simply pass its id to one of the `get` methods.

* To learn about reading items within transactions, see [Transactions](#api-store-transactions).
* To learn about reading items asynchronously, see [Asynchronous operations](#api-store-async).
* To learn about the effect `get` has over item ownership, see [Item ownership](#api-store-ownership).

###### Hinted reads

Sometimes your application may know which node likely owns a certain item (say if this information was conveyed in a [message](#api-messenger)) or that an item is likely owned by the same node that currently owns a different item (if this is how your distributed data structure behaves). In such cases, you can provide hints to the `get` method as to the item's owner, which may sometimes be helpful in improving the running time of the operation (however, even if the hint is wrong and the item is not, in fact, stored on the hinted node, the operation will still perform correctly and find the item wherever it is). 

Some variants of the `get` method take a `nodeHint` parameter (a `short` value) that names the (supposed) owning node.

The `getFromOwner` methods take a second item id that points to an item which is likely owned by the same node that owns the requested item. Note that calling this method may only improve performance if the hinting item (the second parameter) is found on the local node (and so its owner is already known).

###### Writing items

To write an item's value, use one of the `set` methods.

* To learn about writing items within transactions, see [Transactions](#api-store-transactions).
* To learn about writing items asynchronously, see [Asynchronous operations](#api-store-async).
* To learn about the effect `set` has over item ownership, see [Item ownership](#api-store-ownership).

##### Deleting items

An item can be deleted with the `del` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#del(long, co.paralleluniverse.galaxy.StoreTransaction))).

Trying to access (`get` or `set`) a deleted item will result in an exception, but you should not rely on that to detect deleted items (making sure an item is deleted might be costly). Instead, try to only delete items when they are no longer "referenced" by any other item (i.e., they are unreachable).

##### Item ownership {#api-store-ownership}

As explained in the [introduction](#intro-architecture), Galaxy is different from other IMDGs in that item ownership can move between cluster nodes during normal operation. This will now be explained in further detail.

###### Owned items and shared items

Whenever you access a Galaxy data item in your application, it is sent to the cluster node your code is running on. The item
is then stored in RAM in one of two states: **owned** or **shared**.

Every Galaxy data item is **owned** by exactly one node at any point in time, but can be **shared** by many. All nodes
**sharing** an item can read its value, but only the **owning** node can write it. The owning node and sharing nodes for each
item change based on the operations the program performs. You can check whether an item is shared, owned or non-existent in 
any particular node by calling the `getState` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#getState(long))).

###### Sharing an item

When you call the `get` method (any of its variants), if the item is not found on the local node (in either a shared or an owned state), it will be fetched from the owning node, and kept in RAM in the shared state, until the owning node invalidates it (when the item value is changed). Any further reads (with `get`) will complete immediately with no required network operations.

The `gets` method (all of its variants) is very similar to `get`, except that the item will remain shared on the current node until it is explicitly released. In other words, the item is *pinned* to this node in the shared state. You shouldn't keep the item pinned for long, because as long as it's pinned to the local node, it's value cannot be changed by the owning node! (this is not exactly true - see [Inner Workings](#api-store-ownership-inner).

To release a pinned item, you must call the `release` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#release(long))) or use the `gets` method in a [transaction](#api-store-transactions).

###### Owning an item

In order for an item to be written (with the `set` method), it must be owned by the local node, and it must not be shared by any other node (we then say that the item is **exclusive** in the calling node). So, when you call the `set` method, ownership of the item is transferred to the calling node, and all sharing nodes are asked to invalidate their copies of the item.

The `getx` method (all of its variants) reads an item's value, but first it obtains ownership over it, and invalidates all sharers. In other words, it *pins* the item to the local node in the **exclusive** state. As long as the item is pinned, no other node can *read or write* the item (this, too, is not exactly true - see [Inner Workings](#api-store-ownership-inner), so you should release it as soon as possible. `getx` is essentially a "get for write" operation, used to read the the item with the intent to soon modify it with `set`.

To release a pinned item, you must call the `release` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#release(long))) or use the `getx` method in a [transaction](#api-store-transactions).

###### Deadlocks {#api-store-ownership-deadlock}

Because ``getx`` and ``gets`` pin an item to the local node until it is explicitly released, pinning more than one item can 
result in a deadlock. For example if node A pins item X in a shared mode (using ``gets``) and then wishes to pin item Y in the
exclusive mode (with ``getx``), while, at the same time node B pins Y in the shared mode and wishes to pin X in the exclusive mode,
a conflict may occur which will result in both nodes A and B unable to complete their operation. This is called a **deadlock**.

When deadlock occurs, the failed operation will throw a ``TimeoutException``. If this happens, you must undo all writes
that have succeeded to relevant items and release all pinned items in order to allow the other node to complete its operation. 
Then, you may retry the operation. [Transactions](: #api-store-transactions) make dealing with timeouts easier.

See [Timeout](#config-comm-common-timeout) for instructions on setting the timeout duration.

{:.alert .alert-info} 
**Note**{:#api-store-ownership-inner}: **Inner Workings-**
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

##### Listeners {#api-store-listeners}

You can listen for changes in an item's value by providing a listener to the method ``setListener`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#setListener(long, co.paralleluniverse.galaxy.CacheListener))),
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
  Galaxy allows stale reads as long as they don't break consistency (see [Inner Workings](#api-store-ownership-inner)).
* ``evicted`` - called when the item has been evicted entirely from the local node, either because it was a shared item that was not
  accessed recently and Galaxy evicted it to conserve memory, because the item has been deleted, or because Galaxy has determined that 
  it can no longer be read without violating consistency.
  
##### Transactions {#api-store-transactions}

Transactions are used to make multi-item atomic operations easier to use. An atomic multi-item operation is one that potentially
modifies more than one item, and allows other nodes to observe the items' values either as they were before the transaction started
or as they are once the transaction has completed. Internally, transaction simply track which items were pinned, and allows releasing
all of them with one simple method call (remember, an item pinned with ``getx`` cannot be observed by other nodes).

A transaction is started with the ``beginTransaction`` method, completed with the ``commit`` or ``abort`` method, and is used so:

~~~ java
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
~~~

Note how you must explicitly undo your changes if the transaction fails - either using ``rollback`` or manually using ``set``.
By default, transactions support the rollback operation, but this makes them slower (and consume more memory) as they must remember
items' old values. You can disable this "redo log" in the configuration file (See [Configuring and Monitoring the Cache](#config-cache)).

See [Timeout](#config-comm-common-timeout) for instructions on setting the timeout duration.

##### Asynchronous operations {#api-store-async}

Galaxy works best when most data operations access items that are already stored on the local node (see [Performance](#api-store-performance)). However, occasionally operations do require network hops (for ownership transfer etc.), and so may block.

The data-store API provides non-blocking versions to all data operations (called ``getAsync``, ``getsAsync``, ``getxAsync`` etc.)
that do not block, but instead return a ``Future``. This is especially useful (and will give a significant performance boost)
when performing several operations that don't each require the result of the previous one. In the worst case (when network IO is
required) this will result in all network requests being sent together instead of each being sent only after the previous has 
completed.

Here's an example:

~~~ java
ListenableFuture<byte[]> valX = store.getsAsync(x, txn);
ListenableFuture<byte[]> valY = store.getxAsync(y, txn);
store.set(y, process(valX.get(), valY.get()), txn); // this call is synchronous
~~~
    
When used in a transaction, ``commit`` (and ``abort``) will automatically wait for all futures returned within the transactions (and will
so guarantee they are all complete when the transaction ends.

~~~ java
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
~~~

##### Multithreading {#api-store-multithreading}

All of ``Store``'s methods are thread safe, and the ``Store`` instance may safely be used by multiple threads. However, Galaxy
was built to provide inter-node synchronization - not intra-node synchronization - and so pinning an item to the local node
entails no locking. Meaning, an item that was pinned with ``getx`` on one thread, will result in ``getx`` succeeding immediately
when called from another. Even transactions (which are a thin management layer over pinning) will easily trample over each other
if they touch the same items on different threads. Any synchronization among threads (such as locking) must be done by the 
application (or another layer of middleware on top of Galaxy).

By leaving locking to the application, Galaxy provides a lot of flexibility. For example, if used carefully, several threads
may cooperate in running the same Galaxy transaction.


##### Performance {#api-store-performance}

To fully enjoy Galaxy's low-latency processing, abide by the following advice:

* Reduce contention - just like in all distributed systems (and even inside your CPU), contention *invariably* requires communication
  and communication invariably increases latency. Try to avoid multiple nodes all competing to update the same items.
* The more nodes share an item, the less often it should be updated - even if an item is usually updated by the same node, if the item
  is shared (for read access), by a large number of nodes, updating it will increase latency (in the reader nodes, not the writer node).
* Trees are good - Tree data structures (like B-trees and tries) often have the property that the higher up a tree-node is, it will be
  shared more, but will be updated less often. That's a great property.
* Keep your transactions short - this will also reduce contention. Try not to do any blocking operation while in a Galaxy transaction.
* Use asynchronous operations when appropriate.

#### Messenger {#api-messenger}

The ``Messenger`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Messenger.html))
lets you send point-to-point messages to other nodes in the cluster, and is used alongside the [Data store](#api-store) to distribute your application in the grid. While Galaxy's data-store moves data around the grid to be processed at the appropriate node (moving data to code), messages are used to request nodes to carry out operations
without migrating data (moving code to data).

There are two ways of routing your messages. You can send a message to a known node, or you can send it to the (unknown) owner
of a specific data item.

##### Message topics {#api-messenger-topics}

Messages are sent and received by **topics**. Topics are the mechanism by which messages are delivered to the appropriate 
recipient within the node. There are two kinds of topics you can use. ``long`` topics and ``String`` topics.

To receive messages, you register a listener that will receive all incoming messages sent to a specific topic in the local node.
You register receivers like so:

~~~ java
messenger.addMessageListener(topic, myListener);
~~~
    
With ``topic`` being either a ``String`` or a ``long``, and ``myListener`` is an object implementing ``MessageListener``
([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/MessageListener.html)).

When a message sent to ``myTopic`` on the local node is received, ``myListener``'s ``messageReceived`` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/MessageListener.html#messageReceived(short, byte[]))) will be called, and passed the message payload and the sending node.

You remove a listener by calling ``removeMessageListener``.

##### Routing messages {#api-messenger-routing}

One way of addressing messages is sending them to a well known node. You would usually use this option when replying to received
messages, in which case the original sender is passed to the ``MessageListener``'s ``messageReceived`` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/MessageListener.html#messageReceived(short, byte[]))).

To send a message to a well known node use one of the ``send`` methods, and pass it the node id (a ``short``), the topic (either
a ``long`` or a ``String``), and the message payload, like so:

~~~ java
messenger.send(node, topic, message);
~~~

The other way of addressing messages is sending them to the owner of a known data item. To do that, use one of the ``sendToOwnerOf``
methods, and pass it the item id, the topic and the message payload, like so:

~~~ java
messenger.sendToOwnerOf(itemId, topic, message);
~~~

##### Delivery guarantees {#api-messenger-delivery}

Messages are guaranteed to be delivered, and to arrive in the order they were sent (i.e. two messages M and N that are sent in this 
order from node A to B, will be received by B in the same order, regardless of the messages' topics).

Messages are delivered by the same communication channel that delivers Galaxy's internal coherence protocol messages used to move
data items around, so messages are also guaranteed to be ordered with data operations. 

Say that node A updates data item X, and then sends a message M to the owner of data item Y (which happens to be node B), 
and as a result of receiving M, node B reads the value of X. 
In this case node B is guaranteed to read the value of X after the update done by A before sending the message.

These guarantees make it simple to distribute data processing in the grid.

#### Cluster Management {#api-cluster}

The ``Cluster`` interface ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Cluster.html)) provides information
about cluster nodes, and emits cluster events (like nodes joining or leaving the cluster). It also allows for easy configuration
management.

While an application employing Galaxy does not have to use this service, it is very useful for all clustered applications. And
while the application is free to use any other software for cluster management, this service is being used internally by Galaxy,
so when this service detects a node going down - that's when the data-store and messenger detect is as down, as well.

Internally, this service employs either [Apache ZooKeeper] (through Netflix's [Curator] library) or [JGroups] 
(see [Configuring the Cluster Component](#config-cluster) for configuring Galaxy to use either option).
You can gain access to the underlying implementation by calling the ``getUnderlyingResource`` method 
([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Cluster.html#getUnderlyingResource())) which will return the ``CuratorZookeeperClient`` used by Galaxy for cluster management (which, in turn, can be further queried for the underlying ``ZooKeeper`` instance) or the JGroups ``JChannel`` used for cluster management.

[Apache ZooKeeper]: http://zookeeper.apache.org/
[Curator]: https://github.com/Netflix/curator
[JGroups]: http://www.jgroups.org/

##### Cluster organization {#api-cluster-organization}

Every Galaxy node in the cluster has a unique name (a ``String``) which is automatically assigned to it, and an id (a ``short``
value) which is assigned in the local configuration file (see [Configuring the Cluster Component](#config-cluster)) and is not unique (it is shared among
all nodes in a backup group (see [Backup groups](#api-cluster-organization-backup)). 
Node id ``0`` designates server nodes (see [Server node](#api-cluster-organization-server)).

In addition, every node may expose additional properties (such as IP addresses, ports or any other information) to all other nodes
in the cluster. Some properties are used internally by Galaxy, but you can add your own (see [Custom properties](#api-cluster-info-properties)).

Each node is, at any given moment, in one of three states: **offline**, **joined** or **online**.

* An **offline** node is one that is not seen by the other nodes in the cluster, either because it is turned off, disconnected
  from the network, or suffering from a software failure.
* A **joined** node is one which is seen by all other nodes, but does not participate in the data-grid, i.e. it cannot receive
  messages or data-items. All it can do is observe other nodes' state.
* An **online** node is one which fully participates in the grid, either as a master or a slave 
  (see [Backup groups](#api-cluster-organization-backup).

When your application first starts, its node is in the offline state. When you get a grid instance (by calling ``Grid.getInstance()``)
that's when the node will try to join the cluster, and become **joined**.

To go online call ``grid.goOnline()``.

###### Server node {#api-cluster-organization-server}

A server node (or nodes) is a special Galaxy node that doesn't run application code, but is responsible for providing Galaxy data
with disk-based persistence. All of the data in the grid is persisted to disk on the server node, and is also automatically served
by it in case of a node failure. 

The node server always has a node id of ``0``, and, just like with regular nodes, you can have several server nodes in a backup groups
configuration for added availability.

See [Servers and Backup Groups](#config-cluster-common) and [Configuring, Running and Monitoring the Server](#config-server) for more information about configuring and running server node(s).

###### Backup groups {#api-cluster-organization-backup}

Galaxy nodes can be configured in backup groups. All nodes assigned the same node id in their configuration file will become part 
of the same backup group. At any given time, each live backup group has exactly one **master** node, and zero or more **slave**
nodes. The master node replicates all of its owned data items to its slaves so that they can take over in case it fails.

When the master node fails, one of its slaves will become the master and take over. However, a master node *can never become a slave*.
A slave node is notified of its new master status by an event (see [Lifecycle events](#api-cluster-lifecycle)).

See [Servers and Backup Groups](#config-cluster-common) for information about configuring backup groups.

##### Lifecycle events {#api-cluster-lifecycle}

You can listen for important lifecycle events with the ``addLifeCycleListener`` to which you pass your ``LifeCycleListener`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/cluster/LifecycleListener.html)). The listener will be notified
of the following events:

* ``online`` - This event will get triggered when the node reaches the **online** state. The single boolean parameter tells
  the node is a master or a slave (see [Backup groups](#api-cluster-organization-backup)).
* ``offline`` - This event will get triggered when the node goes offline for some reason. Obviously, if the node goes offline
  due to a power failure, this event will not be triggered.
* ``switchToMaster`` - This event will get triggered at a slave node when it becomes the master (when the previous master has gone
  offline.

You can also check the current state of the local node with the methods ``isOnline`` and ``isMaster``.

You remove a lifecycle listener by calling ``removeLifecycleListener``.

##### Cluster events {#api-cluster-events}

The ``Cluster`` service also sends notifications about occuronces in other cluster nodes. 

If you hand a ``NodeChangeListener`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/cluster/NodeChangeListener.html))
to ``addNodeChangeListener`` you'll be notified when a new node (actually, a backup group) comes online, when it goes offline,
and when it has a master switchover.

A ``SlaveConfigurationListener`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/cluster/SlaveConfigurationListener.html)) 
passed to ``addSlaveConfigurationListener`` will notify you when a slave is added to or removed from your local node's backup group.

##### Node information {#api-cluster-info}

The ``Cluster`` interface has methods that return information about the local node and all other cluster nodes.

The ``getMyNodeId`` method returns the local node's id, and the ``getNodes`` method returns the ids of all online nodes (actually
all online backup groups. Remember, the node id is shared by all nodes in the group).

###### NodeInfo

The ``NodeInfo`` interface ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/cluster/NodeInfo.html)) let's you access the configuration record published by any node in the cluster.

``getMyNodeInfo`` returns the ``NodeInfo`` of the local node, so by calling

~~~ java
grid.cluster().getMyNodeInfo().getName()
~~~
    
you can get the local node's unique name.

There are other methods in the ``Cluster`` interface that let you access the ``NodeInfo`` of just about any node in the cluster.
Refer to the Javadoc for more information.

###### Custom properties {#api-cluster-info-properties}

Other than its name and id, each node's configuration record can hold any number of additional properties, which can be accessed
by ``NodeInfo``'s ``get`` or ``getProperties`` methods.

You can add a custom node property, and additionally require that nodes that have not yet publish a value for the property will not be 
in considered **online** until they do, with the ``addNodeProperty`` method ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Cluster.html#addNodeProperty(java.lang.String, boolean, boolean, co.paralleluniverse.galaxy.cluster.ReaderWriter))).
Refer to the Javadoc for more information.

The ``setNodeProperty`` method lets you set a property of the local node.

The ``addMasterNodePropertyListener`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Cluster.html#addMasterNodePropertyListener(java.lang.String, co.paralleluniverse.galaxy.cluster.NodePropertyListener))) and ``addSlaveNodePropertyListener`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Cluster.html#addSlaveNodePropertyListener(java.lang.String, co.paralleluniverse.galaxy.cluster.NodePropertyListener))) 
methods allow you to listen for changes in the values of configuration record properties of any master node in the cluster or any of your slave nodes respectively.

##### The distributed tree {#api-cluster-tree}

Galaxy nodes publish their configuration record via a service called ``DistributedTree`` ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/cluster/DistributedTree.html)) which implements a distributed filesystem-like tree of information nodes
(very similar to the way Apache ZooKeeper works, and in fact, when the cluster is configured to use ZooKeeper, the ``DistributedTree`` is just a thin layer on top of it).
``DistributedTree`` makes some strong consistency and ordering guarantees about it's data, which make it suitable for sharing critical configuration data and for
coordination tasks among nodes, such as leader election. Galaxy's ``NodeInfo`` records simply wrap the ``DistributedTree`` nodes in one of its branches (directories).

If you'd like direct access to the tree, you can obtain the ``DistributedTree`` instance by calling the ``getDistributedTree`` method.

## Configuring and Monitoring Galaxy {#config}

Configuring the Galaxy grid is just as important as using its API, as the API is designed to be simple, but the actions Galaxy takes under the hood are very much
determined by the way Galaxy is configured. 

Galaxy uses the [Spring Framework] for its configuration, and is configured from an XML file using Spring Beans. For a short tutorial on
Spring configuration, see [A Spring Primer](#config-spring).

{:.alert .alert-info}
**Note**: This manual provides detailed instructions to configuring Galaxy. However, for a quicker start, we suggest you take a look at
[Using the pre-built configurations](#start-config) in the Getting Started guide.

[Spring Framework]: http://www.springsource.org/spring-framework

### The configuration file(s) {#config-file}

A Galaxy node is configured from one XML file (of course, that file can reference others in accordance with the Spring Beans schema), whose path is passed to
the ``getInstance`` static method of the ``Grid`` class ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Grid.html)).
If you pass ``null`` (or use the zero-argument version of ``getInstance``), the default configuration file, ``galaxy.xml`` is used, if it's found somewhere on the
classpath.

In addition to the XML file, you may use a Java properties file, and reference those properties in the configuration file like so: ``${prop.name}``. The name
of the properties file may also be passed to ``getInstance``. If a ``null`` name is passed (or the zero-argument version of ``getInstance`` is used), no properties
file gets loaded. The properties file is handy when you want to use the same configuration file for all nodes, with only minor differences (like node ID). 
In this case, you can just set the different properties in the separate properties file. This is the common case so properties files are recommended.

You define properties in the properties file like so (you can make up your own property names - they have no special meaning to Galaxy:

~~~ properties
galaxy.nodeId=3
~~~

and you reference them from the configuration file when setting configuration properties like this:

~~~ xml
<constructor-arg name="nodeId" value="${galaxy.nodeId}"/> 
~~~

Alternatively, you can define properties as JVM system properties by the same name.

The configuration file defines which implementation each Galaxy component is to use, as well as implementation specific configuration parameters for each component.

Galaxy's configuration is flexible enough to allow running multiple nodes (even server nodes) in the same physical machine. All it requires is setting IP ports
and the like carefully, where appropriate.

{:.alert .alert-warn}
**Note**: You *must* use the documented bean ids for each component. You may not assign the beans other names, as Galaxy depends on the components having specific names. 
In addition, Spring's ``default-autowire`` mode must be set to ``constructor``.

Here's how the configuration file should look:
	
~~~ xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:util="http://www.springframework.org/schema/util"

       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
          http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-3.0.xsd
          http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util-3.0.xsd"
       default-lazy-init="false"
       default-autowire="constructor">

    <!-- bean definitions ... -->
</beans>
~~~

### Configuring Galaxy monitoring {#config-monitoring}

Most Galaxy components expose monitoring data, and most of those that do let you choose what monitor to use (in the ``monitoringType`` constructor-arg). Currently,
there are two options:

* ``METRICS`` - uses Yammer's [Metrics] library. Data can be exported to JMX MBeans, [Ganglia], or [Graphite]. This option gives very detailed an high-resolution
  monitoring data, but might be heavy on resources.
* ``JMX`` - uses simple JMX MBeans to export monitoring data. The data is lower-resolution and cruder than the Metrics option, but might be lighter on resources.

[Metrics]: http://metrics.codahale.com/
[Ganglia]: http://ganglia.sourceforge.net/
[Graphite]: http://graphite.wikidot.com/

The best way to view MBeans is with **VisualVM**.

### Monitoring Galaxy's components {#config-component-monitor}


Most Galaxy components expose monitoring data, and the details about which information is exposed is detailed in the component's respective manual section.
Aside from component-specific monitoring, all components expose their configuration properties and basic status as MBeans. These MBeans are named
``co.paralleluniverse.galaxy:type=components,name=COMPONENT_NAME``, where ``COMPONENT_NAME`` is the Spring Bean id.

### Logging {#config-logging}

Galaxy uses [SLF4J] for all its logging.

[SLF4J]: http://www.slf4j.org/

### Galaxy's components

#### A Spring Primer {#config-spring}

The [Spring Framework] lets applications configure and wire their components in an XML file (it does many other things as well, but Galaxy only uses Spring for configuration).
Using Spring or configuration allows very fine-tined control over many aspects of the application, but it can be quite verbose.
This section is a short tutorial explaining how to configure and wire components using Spring XML. 
For a more detailed reference of Spring configuration please see [Spring's IoC Container Documentation].

[Spring Framework]: http://www.springsource.org/spring-framework
[Spring's IoC Container Documentation]: http://static.springsource.org/spring/docs/3.1.x/spring-framework-reference/html/beans.html

##### Spring XML {#config-spring-xml}

A Spring configuration file is an XML file containing bean definitions (beans are explained in the next section).
Here's the structure of the configuration file, as should be used for Galaxy configuration. It contains some required Spring schemas
as well as a few optional ones that provide some convenient features:
	
~~~ xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:context="http://www.springframework.org/schema/context"
       xmlns:util="http://www.springframework.org/schema/util"

       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
       http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-3.0.xsd
       http://www.springframework.org/schema/util http://www.springframework.org/schema/util/spring-util-3.0.xsd"
       default-lazy-init="false"
       default-autowire="constructor">

    <!-- bean definitions ... -->
</beans>
~~~

##### Instantiating beans {#config-spring-beans}

Spring **beans** are application components declared, configured in wired in the Spring XML configuration file as ``bean`` XML elements. 
Each bean corresponds to an instance of a Java object. A bean has a unique ID, which names the component, and a means to instantiate it.
Usually, a bean is instantiated by constructing a simple object of a given class, in which case the bean definition would look like this.

~~~ xml
<bean id="myBean" class="com.mycompany.myClass"/>
~~~

This would instantiate an instance (called ``myBean``) of the class ``com.mycompany.myClass``.

Sometimes, beans are constructed not using a simple constructor but by calling a factory static method. In that case, the bean definition
would look like this:

~~~ xml
<bean id="myBean" class="com.mycompany.myFactoryClass" factory-method="createInstance"/>
~~~

This would instantiate a bean (of some unspecified class) by calling the ``createInstance`` static method of the ``com.mycompany.myFactoryClass`` class.

##### Configuring beans {#config-spring-config}

Usually, a bean definition includes some configuration of the bean instance. 
There are two kinds of configuration hooks: **constructor-args** and **properties**. 
Each bean definition can include constructor-args as well as properties.

###### constructor-args

**constructor-args** are passed as either constructor arguments to the class constructor or as arguments to the static factory method, 
according to the instantiation mechanism used. They are usually required for a successful bean instantiation.

constructor-args can be passed by name, by index, or by type. Here's how we use them to instantiate a bean, using names:

~~~ xml
<bean id="myBean" class="com.mycompany.myClass">
      <constructor-arg name="myName" value="foo" />
      <constructor-arg name="size" value="5" />        
</bean>
~~~

Here we've passed the value ``"foo"`` to the ``String`` parameter ``myName`` of the constructor, and the value ``5`` to the ``size`` parameter
(in the next section we'll see how Spring knows which value type to use).

Unfortunately, specifying constructor-args by name does not always work - it requires either relevant debug information in the class file or the
use of a specific annotation by the class's author. (Note: all Galaxy components described in this documentation, i.e. those that belong to a
``co.paralleluniverse.*`` packages, are annotated and allow specifying constructor-args by name). If named constructor-args don't work, we can
specify them using indices like so:

~~~ xml
<bean id="myBean" class="com.mycompany.myClass">
      <constructor-arg index="0" value="foo" />
      <constructor-arg index="1" value="5" />        
</bean>
~~~

This also passes the String value ``"foo"`` as the first argument of the constructor (which is supposedly ``myName``), and the
value ``5`` as the second argument.

If each parameter is of a different type, we can also pass constructor-args without specifying either name or index, like so:

~~~ xml
<bean id="myBean" class="com.mycompany.myClass">
      <constructor-arg value="foo" />
      <constructor-arg value="5" />        
</bean>
~~~ 

This will achieve the same effect, but only if Spring can figure out unambiguously the arguments' types and which parameter they correspond to;
if not, Spring will issue an error.

###### properties


**properties** are configurations that are usually optional, and if not included in the bean definition, the bean will use some default values.
Internally, properties are set by calling Java Bean setter methods, and can therefore always be specified by name, like so:

~~~ xml
<bean id="otherBean" class="com.mycompany.otherClass">
      <property name="foo" value="bar" />
      <property name="colorName" value="blue" />
      <property name="width" value="10" />
      <property name="height" value="100" />
</bean>
~~~

Of course, some beans combine both constructor-args and properties.

##### The p- and c- namespaces {#config-spring-p-c}

Spring provides two special XML namespaces called ``p`` and ``c`` that allow for a less verbose configuration in many cases.

Using the ``p`` and ``c`` namespaces, you can specify configuration values as XML attributes rather than as child elements.
So the following definition, using the ``p`` and ``c`` namespaces:

~~~ xml
<bean id="myBean" class="a.b.Foo" c:text="Hello World!" p:height="300" p:color="blue" />
~~~

is identical to:

~~~ xml
<bean id="myBean" class="a.b.Foo">
      <constructor-arg name="text" value="Hello World!" />
      <property name="height" value="300" />
      <property name="color" value="blue" />
</bean>
~~~

If constructor-arg names do not work with a given class, you can use indices with the ``c`` namespace, with

~~~ xml
<bean id="myBean" class="a.b.Bar" c:_0="100" c:_1="200" />
~~~

being the same as

~~~ xml
    <bean id="myBean" class="a.b.Bar">
        <constructor-arg index="0" value="100" />
        <constructor-arg index="0" value="200" />
    </bean>
~~~

##### Values {#config-spring-values}

This section explains how Spring interprets property (or constructor-arg) values.

###### Primitives, strings and enums {#config-spring-values-primitives}

Spring uses Java reflection to figure out the type of the property (or constructor-arg), and it attempts to convert the string
value in the XML document into the appropriate type. So ``value="5"`` would become the integer ``5`` if the property's type is
``int``,  ``5.0`` if it's ``double``, or the string ``"5"`` if the property is of type ``String``; 
The values ``true`` and ``false`` are thus converted to ``boolean`` if the property is of the ``boolean`` type,
and strings are passed verbatim if the property is of type ``String``.

If a property's type is a Java enum, then its value is converted to the appropriate enum value according to its name,
so ``value="NFC"`` would become ``java.text.Normalizer.Form.NFC`` if the property's type is ``java.text.Normalizer.Form``.

###### Null values {#config-spring-values-null}

If a property (or a constructor-arg), accept a Java reference, then the ``null`` value can be passed like this:

~~~ xml
<property name="foo"><null/></property>
~~~

###### Referencing other beans {#config-spring-values-ref}

When a property (or constructor-arg) take an object, we can pass it one of the beans we've defined.
So, if we have this bean:

~~~ xml
<bean id="fooBean" class="a.b.Foo" />
~~~

and the ``Bar`` class has a property (or constructor-arg) taking a ``Foo``, we can pass it ``fooBean`` like so:

~~~ xml
<bean id="myBar" class="a.b.Bar" />
      <property name="foo" ref="fooBean" />
</bean>
~~~

Notice the use of the ``ref`` attribute rather than ``value``.

The same could be done with the c/p- namespaces: ``p:foo-ref="myFoo"``, ``c:foo-ref="myFoo"`` or ``c:_0-ref="myFoo"``.

###### Compound values (inner beans) {#config-spring-values-inner}

When a property (or a constructor-arg) take an object, we could use a ``ref``, but if the object is to be used only for
the purpose of setting this particular property, rather than define a new bean and reference it, it is less verbose,
less cluttered, and generally preferable to use an "inner bean". 

Inner beans are bean definitions local to a single property (or constructor arg), that don't have an ``id``, and therefore
cannot be referenced anywhere else.

Here's an example from a Galaxy configuration file of setting a property that takes a value of type ``java.net.InetSocketAddress``:

~~~ xml
<property name="multicastGroup">
    <bean class="java.net.InetSocketAddress">
          <constructor-arg index="0" value="225.0.0.1"/>
          <constructor-arg index="1" value="7050"/>
    </bean>
</property>
~~~

Or, more succinctly:

~~~ xml
<property name="multicastGroup">
    <bean class="java.net.InetSocketAddress" c:_0="225.0.0.1" c:_1="7050" />
</property>
~~~

#### Configuring the Cluster Component {#config-cluster}

The cluster component (defined by the Spring Bean named ``cluster``) is the most fundamental of Galaxy's components. 
It defines how the Galaxy nodes discover each other and exchange configuration data.

There are currently two implementations of the cluster, one employing Apache ZooKeeper, and the other using JGroups. You may choose whichever you prefer. Note,
however, that ZooKeeper requires special server nodes while JGroups doesn't.

##### Servers and Backup Groups {#config-cluster-common}

Both ``cluster`` implementations share a couple of properties. 

The first sets the ``nodeId`` (constructor-arg, ``short``). This is a required property.  
The special node id of ``0`` is reserved for server nodes (see below).

The other common property is ``hasServer`` (property, ``boolean``, default:``true``). 
It is an optional property (with the default value ``true``) that specifies whether the cluster has
server nodes.

There are two mechanisms by which Galaxy provides high-availability in the face of node failures: server nodes and backup groups.

###### Server node {#config-cluster-organization-server}

A server node (or nodes) is a special Galaxy node that doesn't run application code, but is responsible for providing Galaxy data
with disk-based persistence. All of the data in the grid is persisted to disk on the server node, and is also automatically served
by it in case of a node failure. 

The node server always has a node id of ``0``, and, just like with regular nodes, you can have several server nodes in a 
backup group (see below) configuration for added availability.

See [Configuring, Running and Monitoring the Server](#config-server) for more information about configuring and running server node(s).

###### Backup Groups {#config-cluster-organization-backup}

Galaxy nodes can be configured in backup groups. All nodes assigned the same node id in their configuration file will become part 
of the same backup group. At any given time, each live backup group has exactly one **master** node, and zero or more **slave**
nodes. The master node replicates all of its owned data items to its slaves so that they can take over in case it fails.

When the master node fails, one of its slaves will become the master and take over. However, a master node *can never become a slave*.
A slave node is notified of its new master status by an event (see [Lifecycle events](#api-cluster-lifecycle)).

See [Cluster organization](#api-cluster-organization) for more information.

{:.alert .alert-warn}
**Note**: This version of Galaxy does not yet support a backup group for the server. This version of Galaxy supports backup groups of size 2 only (i.e. one master and one backup for each peer node). 

###### Should you use a server? {#config-cluster-organization-serverless}

Short answer: **yes!**

While you can configure a Galaxy cluster without a server, such configuration entails a somewhat different cache-coherence protocol.
When not using a server node, if two node failures occur (from two different backup groups) within a very short time period, 
this may result in lost data items (though not in data conflicts). 

In addition, it is not yet clear how dependable the grid could be without a server, and how useful a server-less configuration is.
It is possible that the server-less configuration will be discontinued in a future version.

{:.alert .alert-warn}
**Note**: In this version, the server-less configuration is not dependable, and may result in lost data, and possibly data conflicts,
even if *a single node fails!*

##### Using ZooKeeper {#config-cluster-zookeeper}

The ``cluster`` component can use [Apache ZooKeeper] for cluster management. Galaxy uses ZooKeeper through the Netflix's [Curator] library
which simplifies ZooKeeper use. Please refer to the ZooKeeper documentation on how to set up ZooKeeper servers.

The ZooKeeper implementation of the ``cluster`` component is called ``co.paralleluniverse.galaxy.zookeeper.ZooKeeperCluster``
and has (in addition to the common ``nodeId`` and ``hasServer``) the following configuration properties:

``zkConnectString`` (constructor-arg, ``String``) <br>
  The ZooKeeper connection string, which tells the node how to connect to the ZooKeeper servers. See the [ZooKeeper Programmers Guide] for details.

``zkNamespace`` (constructor-arg, ``String``) <br>
  The ZooKeeper namespace string, which means which directory ZooKeeper Cluster will use for its operations. Its just a prefix for all ZooKeeper cluster internal actions which is created automatically if itsn't exist yet. This property should be the same for all cluster nodes.

``sessionTimeoutMs`` (property, ``int``, default: ``15000``) <br>
  The ZooKeeper session timeout, in milliseconds. The ZooKeeper documentation has the details.

``connectionTimeoutMs`` (property, ``int``, default: ``10000``) <br>
  The Curator connection timeout, in milliseconds.
 
``retryPolicy`` (property, ``com.netflix.curator.retry.ExponentialBackoffRetry``, default: ``new ExponentialBackoffRetry(20, 20)``) <br>
  The Curator retry policy for failed ZooKeeper operations. See the example below on how to set this property.
  Refer to the [Curator documentation] for details.

[Apache ZooKeeper]: http://zookeeper.apache.org/
[ZooKeeper Programmers Guide]: http://zookeeper.apache.org/doc/trunk/zookeeperProgrammers.html
[Curator]: https://github.com/Netflix/curator
[Curator documentation]: https://github.com/Netflix/curator/wiki

###### Using ZooKeeper in the Cloud

There generally shouldn't be a problem running Galaxy with ZooKeeper in the cloud. However, using ZooKeeper requires that the
UDP implementation of the ``comm`` component be used, and that should be configured correctly to work in cloud environments.
Please refer to [No multicast (using Galaxy in the cloud)](#config-comm-common-cloud) for information.

###### ZooKeeper Configuration Example

~~~ xml
<bean id="cluster" class="co.paralleluniverse.galaxy.zookeeper.ZooKeeperCluster">
      <constructor-arg name="nodeId" value="${galaxy.nodeId}"/>
      <property name="hasServer" value="true"/>
      <constructor-arg name="zkConnectString" value="127.0.0.1:2181"/>
      <!--all zookeeper cluster internal operations will be prefixed with this namespace-->
      <constructor-arg name="zkNamespace" value="production-galaxy"/>
      <property name="sessionTimeoutMs" value="1500"/>
      <property name="connectionTimeoutMs" value="1000"/>
      <property name="retryPolicy">
          <bean class="com.netflix.curator.retry.ExponentialBackoffRetry">
              <constructor-arg index="0" value="20"/>
              <constructor-arg index="1" value="20"/>
          </bean>
      </property>
</bean>
~~~

##### Using JGroups {#config-cluster-jgroups}

Instead of ZooKeeper, the ``cluster`` component can used an implementation (called ``co.paralleluniverse.galaxy.jgroups.JGroupsCluster``) 
that employs [JGroups] for cluster management. Unlike ZooKeeper, JGroups manages the cluster in a purely peer-to-peer fashion, and thus
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

[This section] of the JGroups manual explains the JGroups configuration in detail, but the JGroups jar file contains several complete
XML configuration files you can use as a basis.

Any valid JGroups configuration would do, but you must make two important addition to ensure proper operation of Galaxy (either in the embedded
configuration or in the separate XML file).

First, you must add the [SEQUENCER](http://www.jgroups.org/manual-3.x/html/protlist.html#SEQUENCER) protocol to the configuration, 
so that a complete ordering of configuration messages is enforced. This is done by adding the following XML element to the JGroups configuration,
somewhere towards the bottom:

~~~ xml
<SEQUENCER />
~~~

Second, you must add the [COUNTER](http://www.jgroups.org/manual-3.x/html/protlist.html#COUNTER) protocol.
Add the following XML element at the very bottom of the JGroups configuration:

~~~ xml
<COUNTER bypass_bundling="true"  timeout="5000"/>
~~~

Third, if the cluster is configured not to use a server (``hasServer`` is set to ``false``), a locking protocol must be added
to the bottom of JGroups configuration:

~~~ xml
<CENTRAL_LOCK num_backups="1"/>
~~~

{:.alert .alert-warn}
**Note**:
Do not forget to add the ``SEQUENCER`` and ``COUNTER`` (and if no server is used then ``CENTRAL_LOCK`` as well) 
protocols to the JGroups configuration!

The third property, ``jgroupsThreadPool`` (property, ``java.util.concurrent.ThreadPoolExecutor``, required), creates the thread pool used by 
JGroups. Please refer to [Thread pools](#config-misc-threadpool) to learn how to configure thread-pools, or look at the example below.

[JGroups]: http://www.jgroups.org/

[This section]: http://www.jgroups.org/manual-3.x/html/user-advanced.html#d0e2199

###### Using JGroups in the Cloud {#config-cluster-jgroups-cloud}

Some cloud environments (like Amazon EC2) prohibit multicast, so JGroups must be configured to not use multicast
if you're running Galaxy in such an environment.

There are generally two options to use in such cases. The first is to use the [UDP](http://www.jgroups.org/manual-3.x/html/protlist.html#UDP)
JGroups transport, but disable multicasting (by setting to ``false`` the ``ip_mcast`` property). In addition, you must use a discovery protocol
that does not employ multicasting, such as ``FILE_PING``, ``JDBC_PING``, ``RACKSPACE_PING`` or ``S3_PING``. 
See [Initial membership discovery](http://www.jgroups.org/manual-3.x/html/protlist.html#DiscoveryProtocols) in the JGroups documentation
for more information on discovery protocols.

The other option is to use the [TCP](http://www.jgroups.org/manual-3.x/html/protlist.html#TCP) JGroups transport with either the 
``TCPPING`` or ``TCPGOSSIP`` discovery protocols (or any of the ones mentioned above).

{:.alert .alert-info}
**Note**:
When using Galaxy and JGroups in environments that do not support multicasting, you must also configure the ``comm`` component appropriately.
See [No multicast (using Galaxy in the cloud)](#config-comm-common-cloud).


###### JGroups Configuration Example

~~~ xml
<bean id="cluster" class="co.paralleluniverse.galaxy.jgroups.JGroupsCluster">
    <constructor-arg name="nodeId" value="${galaxy.nodeId}"/>
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
~~~

#### Configuring and Monitoring the Cache {#config-cache}

The cache (bean id ``cache``) is the component responsible for the managements of data items in the local node's RAM, and for
the ownership cache-coherence logic.

##### Configuring the cache {#config-cache-1}

The cache currently has only one implementation (``co.paralleluniverse.galaxy.core.Cache``), and the following properties:

``monitoringType`` (constructor-arg, ``String``) <br>
  Sets the monitor type to use for cache monitoring. Can be either ``METRICS`` or ``JMX`` (see [Configuring Galaxy monitoring](#config-monitoring)).

``synchronous`` (property, ``boolean``, default: ``false``) <br>
  Whether or not backups are done synchronously.
  When set to ``true``, local get operations block until the server and/or slaves have acknowledged the backup.

``maxCapacity`` (constructor-arg, ``long``) <br>
  The maximum capacity (in bytes) to be used for storing shared items. If shared items take up more space than that, they will be evicted from the cache.
  Note that owned items are never evicted. 

``maxItemSize`` (property, ``int``, default: ``1024``) <br>
  The maximum size, in bytes of a single data item. If ``UDPComm`` is used as the ``comm`` implementation (see [Configuring and Monitoring the Comm Component](#config-comm)), then an item must fit in a single UDP packet with room to spare. Ideally, it would fit in one IP packet, so for larger values of ``maxItemSize`` it's best to configure your network to use jumbo packets. This value must be the same in all nodes.

``rollbackSupported`` (property, ``boolean``, default: ``true``) <br>
  Sets whether or not automatic rollbacks for transactions are supported. See [Transactions](#api-store-transactions) and the ``Store.rollback()`` 
  ([Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Store.html#rollback(co.paralleluniverse.galaxy.StoreTransaction))).

``compareBeforeWrite`` (property, ``boolean``, default: ``true``) <br>
  Sets whether or not written items should first be compared with their old value before creating a new version (Galaxy maintains a version number for each item
  to track updates).

``reuseLines`` (property, ``boolean``, default: ``true``) <br>
  Sets whether or not the cache should pool and reuse the data-item book-keeping objects.

``reuseSharerSets`` (property, ``boolean``, default: ``false``) <br>
  Sets whether or not the cache should pool and reuse the objects used to store data-item sharers.

``maxStaleReadMillis`` (property, ``long``, default: ``500``) <br>
  The maximum amount of time (in milliseconds) allowed to elapse since an item has been invalidated while still allowing the **get** operation
  to return the old value. Note that if a **get** could result in any inconsistency, the fresh value will always be retrieved from the owning 
  node no matter what the value of this property is.

Here's an example:

~~~ xml
<bean id="cache" class="co.paralleluniverse.galaxy.core.Cache">
      <constructor-arg name="monitoringType" value="METRICS"/>
      <constructor-arg name="maxCapacity" value="100000000"/> 
      <property name="maxItemSize" value="1024"/>
      <property name="reuseLines" value="true"/>
      <property name="reuseSharerSets" value="true"/>
      <property name="rollbackSupported" value="true"/>
      <property name="compareBeforeWrite" value="true"/>
</bean>
~~~

{:.alert .alert-info}
**Note**: Synchronous mode is not yet implemented in this version.

##### Configuring local storage {#config-cache-storage}

``localStorage`` is a component related to ``cache``. It is responsible for the actual storage in RAM of the data items.
There are currently two implementations - one keeps the items in the Java heap, and one stores them in direct ``ByteBuffer`` s.

###### Heap local storage

The ``co.paralleluniverse.galaxy.HeapLocalStorage`` implementation stores items in plain Java byte-arrays, allowing the Java garbage-collector
to reclaim them when appropriate.

It has one property - ``monitoringType`` (constructor-arg, ``String``), which you can set to either ``METRICS`` or ``JMX`` (see [Configuring Galaxy monitoring](#config-monitoring)).

This is how you configure it:

~~~ xml
<bean id="localStorage" class="co.paralleluniverse.galaxy.HeapLocalStorage">
      <constructor-arg name="monitoringType" value="METRICS"/>
</bean>
~~~

###### Off-heap local storage

The other ``localStorage`` implementation, ``co.paralleluniverse.galaxy.core.OffHeapLocalStorage``, stores data items in direct ``ByteBuffer`` s and manages
allocations and de-allocations. It manages blocks of fixed-sized memory pages, each block used for data items of certain sizes. When allocating memory for
an item of sized ``n``, the memory buffer returned will be the nearest power-of-two greater-or-equal to ``n``.

It has several configuration properties:

``monitoringType`` (constructor-arg, ``String``) <br>
  Can be ``METRICS`` or ``JMX`` (see [Configuring Galaxy monitoring](#config-monitoring)).

``pageSize`` (constructor-arg, ``int``) <br>
  The size **in kilobytes** of each memory page used by the allocator.

``maxItemSize`` (constructor-arg, ``int``) <br>
  The maximum size, in bytes of a single data item. Must be set to the same value as the ``maxItemSize`` property of the ``cache`` component.

``maxPagesForConcurrency`` (property, ``int``, default: ``Runtime.getRuntime().availableProcessors() * 2``) <br>
  The maximum number of pages to allocate in each block simply for reducing contention (and not because memory is exhausted).

Here's an example:

~~~ xml
<bean id="localStorage" class="co.paralleluniverse.galaxy.core.OffHeapLocalStorage">
    	<constructor-arg name="monitoringType" value="METRICS"/> 
      <constructor-arg name="pageSize" value="1024"/>
      <constructor-arg name="maxItemSize" value="1024"/>
      <property name="maxPagesForConcurrency" value="4"/>
</bean>
~~~

The amount of memory available for direct buffer is determined by the ``XX:MaxDirectMemorySize`` command line option passed to the JVM
(the ``java`` command). For example, to provide 512MB to direct ByteBuffers, you add the following command line option to the ``java`` command:

~~~ sh
-XX:MaxDirectMemorySize=100M
~~~

##### Configuring Backup {#config-cache-backup}

The ``backup`` component is responsible for backing up the node's owned items after modifications to the server and/or slaves.
There is currently one implementation of ``backup`` - ``co.paralleluniverse.galaxy.core.BackupImpl"`` - and it has two configuration properties:

``monitoringType`` (constructor-arg, ``String``) <br>
  Sets the monitor type to use for cache monitoring. Can be either ``METRICS`` or ``JMX`` (see [Configuring Galaxy monitoring](#config-monitoring)).

``maxDelay`` (property, ``int``, default: ``10``) <br>
  The maximum duration, *in milliseconds*, between flushes of backup data (to the server and/or slaves.) It is also roughly the maximum amount of time
  that can be "lost", i.e. updates that can disappear if the node goes down. If it's small, less updates can be lost in a case of failure, but both
  latency and throughput would suffer.

``serverComm`` (constructor-arg, ``co.paralleluniverse.galaxy.core.ServerComm``, default: autowired) <br>
  If you configure your cluster without a server, set this constructor-arg to ``null``(see [Null values](#config-spring-values-null)). Otherwise,
  don't set it at all, and Spring will auto-wire it to whatever ``serverComm`` component you have defined (see [The ServerComm](#config-comm-common-servercomm)).

Here's a configuration example:

~~~ xml
<bean id="backup" class="co.paralleluniverse.galaxy.core.BackupImpl">
      <constructor-arg name="monitoringType" value="METRICS"/>
      <property name="maxDelay" value="200"/>
</bean>
~~~

##### Monitoring the cache {#config-cache-monitoring}

TBD

#### Configuring and Monitoring the Messenger {#config-messenger}

The Messenger component (bean: ``messenger``) is responsible for sending and receiving user messages on the grid
(see [Messenger](#api-messenger)).

##### Configuring the messenger {#config-messenger-config}

``messenger`` has only one implementation - ``co.paralleluniverse.galaxy.core.MessengerImpl``, and it takes just one configuration property:
``threadPool`` (property, ``co.paralleluniverse.galaxy.core.NodeOrderedThreadPoolExecutor``, required). ``NodeOrderedThreadPoolExecutor``
is a special king of ``java.util.concurrent.ThreadPoolExecutor``, and to learn more about configuring it, please see [Thread pools](#config-misc-threadpool)
for instructions on how to configure a thread-pool, or just take a look at this example:

~~~ xml
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
~~~

#### Configuring and Monitoring the Comm Component {#config-comm}

The Comm component (bean id: ``comm``) is responsible for transmitting Galaxy's internal cache-coherence protocol messages, as well as user messages
(sent with the `Messenger`) over the network. There are currently two implementations for this component. The first uses UDP, and the second uses 
JGroups and is available only if the cluster is configured to use JGroups (see [Using JGroups](#config-cluster-jgroups)).

##### Common comm configurations {#config-comm-common}

Both ``comm`` implementations share a couple of very important configuration properties.

###### Timeout {#config-comm-common-timeout}

The ``timeout`` configuration (property, ``long``, default: ``200``), is the duration, **in milliseconds** to wait for a response to a 
massage. This, in effect, determines the time it takes for any grid operation to fail and throw a ``TimeoutException`` (See [Deadlocks](#api-store-ownership-deadlock) and [Transactions](#api-store-transactions)).

The shorter the timeout is, the faster deadlocks will be detected, but so will more operations fail spuriously due to network latency.

###### No multicast (using Galaxy in the cloud) {#config-comm-common-cloud}

For some internal operations (such as finding the owner of a freshly-encountered item id), both Comm implementations use multicast by default.
Some cloud platforms (like Amazon EC2) do not allow multicast, so to run Galaxy without multicast, we can configure it to use the server for
node discovery. Of course, in a situation like that a server is necessary.

In deployments where multicast is available, communicating with the server instead of multicasting can have a performance impact - sometimes for the better
and sometimes for the worse, depending mostly on your server's performance.

To tell ``comm`` to talk to the server instead of multicasting, set the bean property ``sendToServerInsteadOfMulticast`` to ``true`` (it's ``false`` by default).

Please not that if you're using JGroups for your ``cluster`` component implementation, you must configure JGroups to avoid multicast as well. 
See [Using JGroups in the Cloud](#config-cluster-jgroups-cloud) for more information.

###### The ServerComm {#config-comm-common-servercomm}

``comm`` makes use of another component, called ``serverComm`` to communicate with the server. At the moment, there is just one implementation of ``server-comm``,
which uses TCP (so it makes use of the optional ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties,
explained in [Configuring Netty Channels](#config-comm-netty). 

So, if your cluster has a server node, define it with the following bean (in this example we do not set the thread-pool properties, so defaults are used):

~~~ xml
<bean id="serverComm" class="co.paralleluniverse.galaxy.netty.TcpServerClientComm"/>
~~~

and link it to the ``comm`` bean by putting this line in the ``comm`` bean definition:

~~~ xml
<constructor-arg name="serverComm" ref="serverComm"/>
~~~

(Actually, there is one more ``server-comm`` implementation, one that's used with something called "dumb servers". See [Dumb servers](#config-server-dumb) for details).

If your cluster is configured without a server, set this constructor-arg to ``null`` (see [Null values](#config-spring-values-null)). 

###### The SlaveComm {#config-comm-common-slavecomm}

Just like a special component is used to communicate with the server, so too a special component is used to communicate with the slaves in the node's backup group.
The component is ``slaveComm``, and it currently has one implementation that uses TCP called ``co.paralleluniverse.galaxy.netty.TcpSlaveComm``. 

In addition to the optional ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties explained [Configuring Netty Channels](#config-comm-netty),
it has one configuration property:

``port`` (constructor-arg, ``int``) <br>
  The TCP port used for master-slave communications. The master binds a server socket to this port (and the slaves discover the port using the distributed 
  configuration record, so in principle, this port can be different on each node, as it's used only when the node is master.)

Here's an example:

~~~ xml
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
~~~ 

##### Using the UDP comm {#config-comm-udp}

The UDP implementation of the Comm component uses UDP datagrams for cache-coherence and user messages. 

Other than the mentioned common ones, plus the ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties
explained in [Configuring Netty Channels](#config-comm-netty, this implementation has the following configuration properties:

``port`` (constructor-arg, ``int``) <br>
  The UDP port the component will send and receive messages on. This value **does not** have to be the same for all nodes.
  (nodes know each other ports by publishing them as a configuration record in the cluster).

``multicastGroup`` (property, ``java.net.InetSocketAddress``, required if ``sendToServerInsteadOfMulticast`` is ``true``) <br>
  The multicast IP address this node will join for multicast address, and the port to use for sending and receiving multicast
  messages. This value **must be the same** in all nodes. See the example below on how to set this property's value.

``multicastNetworkInterface`` (property, ``java.net.NetworkInterface``, default: ``null``) <br>
  The network interface to use for multicast. If set to ``null`` (the default), the default interface will be used.

``receiveBufferSize`` (property, ``int``, default: determined by the socket; implementation specific) <br>
  The size of the socket receive buffer (SO_RCVBUF). The SO_RCVBUF option is used by the the network implementation as a hint to size the 
  underlying network I/O buffers. The SO_RCVBUF setting may also be used by the network implementation to determine the maximum size
  of the packet that can be received on this socket.

``minimumNodesToMulticast`` (property, ``int``, default: ``3``) <br>
  The minimum number of nodes in the cluster (not including servers) for this component to use multicast. If there are fewer
  online nodes than this value, the component will unicast messages to each node.

``resendPeriodMillisecs`` (property, ``int``, default: ``20``) <br>
  The duration in milliseconds to wait between consecutive resending of a message if a reply has not been received.
  If ``exponentioalBackoff`` is turned on (it's turned on by default), this is the initial duration (between the first time the
  message is sent and the second).

``exponentialBackoff`` (property, ``boolean``, default: ``true``) <br>
  If turned on (which is the default), doubles the duration between resending of messages after each re-send.

``jitter`` (property, ``boolean``, default: ``false``) <br>
  If turned on, adds a random small jitter to the duration between resends.

``minDelayMicrosecs`` (property, ``int``, default: ``1``) <br>
  The minimum duration, in microseconds, to wait before transmitting a packet, for other messages to be sent so that they could be
  added to the same packet.

``maxDelayMicrosecs`` (property, ``int``, default: ``10``) <br>
  The maximum duration, in microseconds, to wait for additional messages (in case they keep arriving), before transmitting a packet.

``maxQueueSize`` (property, ``int``, default: ``50``) <br>
  The maximum number of messages waiting in the ``comm`` component's message queue. If this number is reached, sending an additional
  message will block until the queue length falls beneath it.

``maxPacketSize`` (property, ``int``, default: ``4096``) <br>
  The maximum size of a single packet the ``comm`` component will transmit. Data-item size (defined by the ``maxItemSize`` property of the ``cache``
  component; see [Configuring the cache](#config-cache-1) must not exceed this value (and there must also be some room left for headers).

``maxRequestOnlyPacketSize`` (property, ``int``, default: ``maxPacketSize / 2``) <br>
  The maximum size of a packet that contains only request messages. Must be less than ``maxPacketSize``.
  The exact semantics of this property is beyond the scope of this document, but if this value is too close to ``maxPacketSize`` a deadlock condition
  may arise (it will be clearly noted in the logs, so you can recognize it if it happens), and if it's too small, performance under heavy load may suffer.

~~~ xml
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
~~~~

##### Configuring Netty Channels {#config-comm-netty}

Except for configuration messages used by the ``cluster`` component, all Galaxy network communication - peer-nodes to peer-nodes, peer-nodes to servers
and slaves to masters - uses the [Netty] library (unless you've decided to use JGroups for peer-to-peer communication - see [Using the Jgroups comm](#config-comm-jgroups).

[Netty]: http://netty.io/

Netty communication channels use various thread-pools.
TCP channels use a single "boss thread" taken from a "boss" thread-pool for making or accepting connections, and possibly multiple "worker" threads
taken from a different pool, responsible for sending and receiving messages. UDP channels (they're connectionless) only use worker threads. 

Components using TCP, therefore have two properties, ``bossExecutor`` and ``workerExecutor`` taking an instance of 
``java.util.concurrent.ThreadPoolExecutor``. If you don't set these properties, each uses a default thread-pool (returned from calling 
``java.util.concurrent.Executors.newCachedThreadPool()``). Components making use of UDP don't have the ``bossThread`` property.

See [Configuring a thread-pool](#config-misc-threadpool-config) for the thread-pool configuration details. 

In addition, all components using Netty can optionally use another thread-pool, passed to the ``receiveExecutor`` property. This thread-pool, which
must be an instance of ``org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor`` (a subclass of ``java.util.concurrent.ThreadPoolExecutor``),
and it is used to actually process the receive messages. If you set this property to ``null`` (which is the default), all message processing will
be done on the channel's worker thread-pool.

``OrderedMemoryAwareThreadPoolExecutor`` doesn't take a ``maximumPoolSize`` argument (as its core-size is also its maximum size), but
it does take two additional arguments:

``maxChannelMemorySize`` (``long``) <br>
  The maximum total size, in bytes, of the queued events per channel (i.e. per cluster node we're communicating with). 
  A value of ``0`` disables this limit.

``maxTotalMemorySize`` (``long``) <br>
  The maximum total size, in bytes, of the queued events for this pool. 
  A value of ``0`` disables this limit.

Unfortunately, named constructor-args don't seem to work with this class, so we must use argument indexes, and create the instance like so:

~~~ xml
<bean class="org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor">
    <constructor-arg index="0" value="8"/> <!-- "corePoolSize" -->
    <constructor-arg index="1" value="0"/> <!-- "maxChannelMemorySize" -->
    <constructor-arg index="2" value="0"/> <!-- "maxTotalMemorySize" -->
    <constructor-arg index="3" value="5000"/> <!-- "keepAliveTime" -->
    <constructor-arg index="4" value="MILLISECONDS"/> <!-- "unit" -->
</bean>
~~~
	
##### Using the JGroups comm {#config-comm-jgroups}

The JGroups ``comm`` implementation is ``co.paralleluniverse.galaxy.jgroups.JGroupsComm``. 
It is only available when JGroups is chosen as the ``cluster`` component implementation (see [Using JGroups](#config-cluster-jgroups)).
Because JGroups is configured in the ``cluster`` bean, this bean has no properties other than the common ``comm`` ones, and can be defined
as simply as:

~~~ xml
<bean id="comm" class="co.paralleluniverse.galaxy.jgroups.JGroupsComm">
	<constructor-arg name="serverComm" ref="serverComm"/>
    <property name="sendToServerInsteadOfMulticast" value="false"/>
    <property name="timeout" value="200"/>
</bean>
~~~

The ``timeout`` and ``sendToServerInsteadOfMulticast`` properties are kept at their default values in this example, so they can be dropped entirely.

##### Monitoring the ``comm`` component {#config-comm-monitoring}

TBD

#### Configuring, Running and Monitoring the Server {#config-server}

The Galaxy server node (or nodes, if more than one is configured in the backup group) provides two features:

* Disk persistence
* High availability in case of node failure.

A Galaxy cluster does not have to be configured to have a server. If disk persistence is not required, you can rely on backup groups
alone for high-availability (see [Backup groups](#api-cluster-organization-backup)). However, in some circumstances, server nodes can provide
other benefits, such as improved performance.

There are two kinds of servers a Galaxy cluster can use. The first, which we'll call "real servers", and the other we'll call "dumb servers".
We will refer to non-server nodes as "peer nodes".

##### Real servers {#config-server-real}

Real servers are server nodes that run the Galaxy server software and (usually) access an embedded database.

###### Configuring real servers

A real server has a similar configuration file to that used in peer nodes, but with some shared and some different components:

``cluster`` <br>
  This is the same as in the peer nodes. See [Configuring the Cluster Component](#config-cluster) for instructions on how to configure this component.

``comm`` <br>
  This is the equivalent of the peer nodes' ``comm`` component (see [Configuring and Monitoring the Comm Component](#config-comm)), but it's (currently) sole implementation
  (``co.paralleluniverse.galaxy.netty.TcpServerServerComm``) - the server side of the peers' TCP ``serverComm`` - takes a ``port`` 
  property (constructor-arg, ``int``), plus, optionally, the ``bossExecutor``, ``workerExecutor`` and ``receiveExecutor`` properties
  explained in [Configuring Netty Channels](#config-comm-netty).

  Here's a configuration example:

~~~ xml
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
~~~

``memory`` <br>
  This is the equivalent of the peers' ``cache`` component, and it's responsible for the server's data-item logic. It has one implementation
  (``co.paralleluniverse.galaxy.core.MainMemory``) that takes just a ``monitoringType`` property (see [The configuration file(s)](#config-monitoring)).
  Here's how it's defined:

~~~ xml
<bean id="memory" class="co.paralleluniverse.galaxy.core.MainMemory">
    <constructor-arg name="monitoringType" value="METRICS"/> <!-- METRICS/JMX -->
</bean>
~~~

``store`` <br>
  This component is responsible for persisting and retrieving data items using a database. 
  Its configuration is explained [below](#config-server-store).

###### Running the server

To run the server, simply run the executable Java class ``co.paralleluniverse.galaxy.Server``, and optionally pass one or two command line
arguments specifying the configuration file and, optionally, the properties file, too (see [The configuration file(s)](#config-file) for an explanation).

Optionally, if for some reason you'd like to embed the Galaxy server in your own Java process, you may start the server by calling
``Server``'s ``start`` method and optionally pas the configuration and properties files.

If no configuration file is specified, the server uses the file called ``galaxy.xml`` if it's found somewhere on the classpath.

You may refer to the Server class [Javadoc](http://puniverse.github.io/galaxy/javadoc/co/paralleluniverse/galaxy/Server.html).

##### Dumb servers {#config-server-dumb}

Dumb servers are machines running some sort of a database server that can be used as Galaxy servers without being Galaxy nodes themselves,
i.e., they do not run any Galaxy code - just the database server. Databases that provide network access can be used as dumb servers.
When using a dumb server, the logic for accessing and communicating with them is hosted on the peer nodes.

To configure a dumb server, you must make some changes and additions to the peers' components.

``serverPipe`` <br>
  This simple additional bean is responsible for piping all messages sent to the server to the local server proxy. It's defined thus:

~~~ xml  
<bean id="serverPipe" class="co.paralleluniverse.galaxy.server.CommPipe"/>
~~~

``serverComm`` <br>
  Instead of a TCP connection to a real server, we will now be directing messages to the server through the ``serverPipe`` so ``serverComm``
  is now defined so:

~~~ xml
<bean id="serverComm" factory-bean="serverPipe" factory-method="getComm1">
    <constructor-arg index="0" value="${grid.nodeId}"/>
</bean>
~~~

``memory`` <br>
  The memory component (responsible for server logic) now sits at the peers, so it has to be added to the peer configuration, and it receives
  messages from the other end of the ``serverPipe``:

~~~ xml  
<bean id="memory" class="co.paralleluniverse.galaxy.core.MainMemory">
    <constructor-arg name="comm">
        <bean factory-bean="serverPipe" factory-method="getComm2">
            <constructor-arg index="0" value="0"/>
        </bean>
    </constructor-arg>
    <constructor-arg name="monitoringType" value="METRICS"/>
</bean>
~~~~

``store`` <br>
  The store is now configured at the peers. See [below](#config-server-store) for instructions on how to configure the store.

##### Configuring the store {#config-server-store}

The store is the component responsible for data-item persistence, and is usually implemented on top of some database.
At the moment there are two store implementations, one that uses **BerkeleyDB Java Edition**, and that uses any RDBMS with a JDBC driver.

###### Using BerkeleyDB {#config-server-store-bdb}

[BerkeleyDB Java Edition] (BDB JE) can be used as Galaxy's store. Because BDB JE is an embedded database and does not have a network interface,
it can only be used as a dumb server - only as part of a real server. 
The ``store`` implementation that uses BDB JE is ``co.paralleluniverse.galaxy.berkeleydb.BerkeleyDB``, and it has two configuration properties:

``envHome`` (constructor-arg, ``String``) <br>
  The path to the directory which will contain the BDB files.

``truncate`` (property, ``boolean``, default: ``false``) <br>
  Whether or not the database will be truncated (i.e., all the data-item data be deleted) when the server starts.

``durability`` (property, ``com.sleepycat.je.Durability.SyncPolicy``, default: ``WRITE_NO_SYNC``) <br>
  Defines the disk synchronization policy to be used when committing a transaction. There are three possible values:
  ``SYNC``, ``WRITE_NO_SYNC``, or ``NO_SYNC``, that are fully explained in the BDB JE Javadocs [here](http://docs.oracle.com/cd/E17277_02/html/java/index.html).

Tuning of BerkeleyDB JE is possible by setting properties in the ``je.properties`` file, placed at the environment home directory.
Details about BDB JE tuning can be found in the JE documentation [here](http://docs.oracle.com/cd/E17277_02/html/GettingStartedGuide/administration.html). 

Here's a configuration example:

~~~ xml
<bean id="store" class="co.paralleluniverse.galaxy.berkeleydb.BerkeleyDB">
    <constructor-arg name="envHome" value="/usr/bdb/galaxy"/>
    <property name="truncate" value="true"/>
</bean>
~~~

[BerkeleyDB Java Edition]: http://www.oracle.com/technetwork/database/berkeleydb/overview/index-093405.html

###### Using SQL {#config-server-store-jdbc}

Any SQL database that supports transactions and has a JDBC driver can be used as the store. Those that have a network interface can also
become dumb servers. The ``store`` implementation that uses JDBC is ``co.paralleluniverse.galaxy.jdbc.SQLDB`` and here are it's configuration
properties:

``dataSource`` (constructor-arg, ``javax.sql.DataSource``) <br>
  The ``DataSource`` instance used to construct DB connections. See the example below on how to set this property.

``maxItemSize`` (property, ``int``, default: ``1024``) <br>
  The maximum size, in bytes, of a data-item. Must be the same as the ``maxItemSize`` set in the ``cache`` component (see [Configuring the cache](#config-cache-1)).

``useUpdateableCursors`` (property, ``boolean``, default: ``false``) <br>
  Whether updateable cursors should be used in some atomic transactions. Might have a positive, or negative performance impact, depending
  on the database and driver implementation.

``schema`` (property, ``String``, default: ``pugalaxy``) <br>
  The schema that will host the Galaxy table.

``tableName`` (property, ``String``, default: ``memory``) <br>
  The name of the table that will store the data-items.

``bigintType`` (property, ``String``, default: queried with ``DatabaseMetaData`` if possible) <br>
  The name of the database's SQL type for JDBC's ``BIGINT``. Should be set if automatic detection does not work.
 
``smallintType`` (property, ``String``, default: queried with ``DatabaseMetaData`` if possible) <br>
  The name of the database's SQL type for JDBC's ``SMALLINT``. Should be set if automatic detection does not work.

``varbinaryType`` (property, ``String``, default: queried with ``DatabaseMetaData`` if possible) <br>
  The name of the database's SQL type for JDBC's ``VARBINARY``. Should be set if automatic detection does not work.

~~~ xml
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
~~~

When using a SQL database, if the entire grid is taken down, you must manually either clear the Galaxy table (which is called 
``pugalaxy.memory`` by default) if you'd like to dispose of the data, or, if you'd like to keep it, you must assign ownership of all
data items to the server by running the following SQL command:

~~~ sql
UPDATE pugalaxy.memory SET owner=0
~~~

{:.alert .alert-warn}
**Note**: **Do not forget** to clear the database table or set the owner in all rows to ``0`` before re-starting the grid. If you don't, havoc will ensue.

#### Miscellaneous Configurations {#config-misc}

##### Thread pools {#config-misc-threadpool}

Galaxy makes extensive use of thread-pools.

###### Configuring a thread-pool {#config-misc-threadpool-config}

Several Galaxy components require you to provide a thread-pool in the form of a ``java.util.concurrent.ThreadPoolExecutor`` 
instance or a subclass of it.

These are ``ThreadPoolExecutor``'s constructor-args:

``corePoolSize``, index:0, ``int`` <br>
  The number of threads to keep in the pool, even if they are idle.

``maximumPoolSize``, index:1, ``int`` <br>
  The maximum number of threads to allow in the pool.

``keepAliveTime``, index:2, ``long`` <br>
  When the number of threads is greater than the core, this is the maximum time that excess idle threads 
  will wait for new tasks before terminating.

``unit``, index:3, ``java.util.concurrent.TimeUnit`` <br>
   The time unit for the ``keepAliveTime`` argument.
   Can be ``NONOSECONDS``, ``MICROSECONDS``, ``MILLISECONDS``, ``SECONDS``, ``MINUTES``, ``HOURS`` or ``DAYS``.

``workQueue``, index:4, ``java.util.concurrent.BlockingQueue`` <br>
  The queue to use for holding tasks before they are executed. 

For the ``workQueue`` argument, is is best to use `an instance of ``co.paralleluniverse.common.concurrent.QueueFactory``
which constructs the queue most appropriate for the given maximum size. It is defined like this:

~~~ xml
<bean class="co.paralleluniverse.common.concurrent.QueueFactory" factory-method="getInstance" c:maxSize="500"/>
~~~

And there are two special values for ``maxSize``. ``-1`` designates an unbounded queue, and ``0`` specifies a handoff "queue"
where each producer must wait for a consumer (i.e. there can be no tasks waiting in the queue).

Here's an example of an inner bean ([Compound values (inner beans)](#config-spring-values-inner)) defining a ``ThreadPoolExecutor``:

~~~ xml
<bean class="java.util.concurrent.ThreadPoolExecutor">
    <constructor-arg index="0" value="2"/>
    <constructor-arg index="1" value="8"/>
    <constructor-arg index="2" value="5000"/>
    <constructor-arg index="3" value="MILLISECONDS"/>
    <constructor-arg index="4">
        <bean class="co.paralleluniverse.common.concurrent.QueueFactory" factory-method="getInstance" c:maxSize="500"/>
    </constructor-arg>
</bean>
~~~

Galaxy provides a convenience class that is a bit simpler to declare, which can be used instead of ``java.util.concurrent.ThreadPoolExecutor``
(**but not when a specific subtype is required!**)

~~~ xml
<bean class="co.paralleluniverse.galaxy.core.ConfigurableThreadPool">
    <constructor-arg name="corePoolSize" value="2"/>
    <constructor-arg name="maximumPoolSize" value="8"/>
    <constructor-arg name="keepAliveMillis" value="5000"/>
    <constructor-arg name="maxQueueSize" value="500"/>
</bean>
~~~

Some Galaxy components may ask for a ``co.paralleluniverse.galaxy.core.NodeOrderedThreadPoolExecutor``, which is 
configured so:

~~~ xml
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
~~~

###### Monitoring thread-pools {#config-misc-threadpool-monitor}

All of Galaxy's thread-pools expose monitoring information using MBeans. All of these MBeans are named 
``co.paralleluniverse:type=ThreadPoolExecutor,name=POOL_NAME``, and can be found in **VisualVM** or **JConsole** in the
MBean tree under the ``co.paralleluniverse/ThreadPoolExecutor`` node.





