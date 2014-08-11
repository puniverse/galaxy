# *Galaxy*<br/>A cache-coherent in-memory data grid
[![Build Status](http://img.shields.io/travis/puniverse/galaxy.svg?style=flat)](https://travis-ci.org/puniverse/galaxy) [![Dependency Status](https://www.versioneye.com/user/projects/52b019d7ec1375ace70000fa/badge.svg?style=flat)](https://www.versioneye.com/user/projects/52b019d7ec1375ace70000fa) [![Version](http://img.shields.io/badge/version-1.4-blue.svg?style=flat)](https://github.com/puniverse/galaxy/releases) [![License](http://img.shields.io/badge/license-EPL-blue.svg?style=flat)](https://www.eclipse.org/legal/epl-v10.html) [![License](http://img.shields.io/badge/license-LGPL-blue.svg?style=flat)](https://www.gnu.org/licenses/lgpl.html)

Galaxy is an in-memory data-grid. It's main function is to distribute data objects (stored as simple byte arrays) among cluster nodes for distributed processing. It also provides point-to-point messaging with guaranteed delivery and guaranteed ordering, as well as a cluster configuration management service.

Galaxy Features and Architecture
---------------------------------

* __RAM storage and code/data co-location__
Application code runs on the same cluster nodes (called **peer nodes**), and processes the data    objects which are kept in RAM.
  
  Unlike other IMDGs that partition data items and distribute them in such a way that each object is the responsibility of a  single node, Galaxy nodes exchange messages to transfer ownership of objects from one node to another. This means that if the  application code in one of the nodes regularly updates some data items, those items will be owned by the node, and will be available for processing and update without any network I/O. As the application runs, items will migrate from one node  to another (though items can reside on several nodes at once for read-only access). This gives the application precise control over the location of the data in the cluster for maximum performance. 

  This flexibility, however, is suitable when data access patterns can be predicted and so the data can be optimally placed. If data access is random, other IMDGs may be a better choice as they can retrieve or update any item at a cost of one network hop, while Galaxy might need to search the cluster for an unexpected item access.
  
  Because the application code is co-located with the data, and because all of the data is kept in RAM, Galaxy is suitable for low-latency applications.
  
* __Consistency__
  Galaxy stores data in a fully consistent manner, meaning that if data item B has been modified after a change to data item A, no node in the cluster will see A's new value but B's old one.
  
  Galaxy achieves consistency by using a [cache-coherence protocol](http://en.wikipedia.org/wiki/Cache_coherence) similar to  the protocols used to coordinate memory access among CPU cores. However, as Galaxy can guarantee the ordering of coordination   messages between nodes no memory-fence operations are requires (as they are in CPUs) to achieve consistency.
  
* __Disk persistence and server nodes__
  The data items can optionally be persisted to disk on one or more special *server nodes*.
  
  However, in order to keep latency low, Galaxy (even when configured to use a persistent server node), is not durable. This means that a failure in one or more of the nodes may result in a permanent loss of some *recent* updates. However, even in cases of such failures the data remains consistent, meaning the lost updates will be lost to all of the nodes (or to none).
  
* __High Availability__
  Galaxy can withstand a failure in one or more of the nodes, providing high-availability. This is achieved by either running Galaxy with a server node (which persists all of the grid data to disk) or by running a slave node (or more) for each of the peers, or both.
  
  If only a server node is used, data is not lost when a peer node fails (except for possibly some recent updates as explained above), and all the data items owned by the failed node are still accessible. However, as the server reads those items from the disk, latency might suffer until all items have been accessed by the peers and kept in RAM.
  
  Alternately, or in combination with a server, you can run one or more slave-nodes for each of the peers, that mirror the data stored in them, so that upon failure of a peer, one of its slaves will take over, already having all of the necessary data items in RAM.
  
  A server node may also have slaves that will take over when it fails.
  
* __Messaging__
  Galaxy provides a point-to-point messaging service that guarantees message delivery and ordering. A message can be sent to a known node or to the unknown (to the application) owner of a given data item. So if **Galaxy's data-item migration makes moving data to code simple, Galaxy's messages make moving an operation (code) to data just as simple**.
  
  The application messages are delivered by the same communication channel that delivers Galaxy's internal coherence protocol messages, so messages are also  guaranteed to be ordered with data operations. Say that node A updates data item X, and then sends a message to the owner of data item Y (which happens to be node B), as a result of which node B reads the value of X. In this case node B is guaranteed to read the value of X after the update done by A before sending the message.
  
* __Monitoring__
  All of Galaxy's components are monitored to enable full diagnoses of failure or performance problems.
  

Galaxy's aim is to give the application full control over data location and processing in the grid, and in-order to provide maximal flexibility with a simple API, it is relatively low-level. It provides no query mechanism whatsoever, imposes no structure on the data (which is kept in memory and on disk as byte arrays), and provides no locking of elements to coordinate complex transactions among different threads on a single node (although each operation is atomic by itself). All of that must be provided by the application.

Documentation
-------------

The Galaxy documentation is found [here](http://puniverse.github.com/galaxy/).

Galaxy Internals
----------------

A series of blog posts detailing Galaxy's inner workings can be found here: [part 1](http://blog.paralleluniverse.co/post/28062434301/galaxy-internals-part-1), [part 2](http://blog.paralleluniverse.co/post/28635713418/how-galaxy-handles-failures), [part 3](http://blog.paralleluniverse.co/post/29085615915/galaxy-internals-part-3).
This [blog post](http://highscalability.com/blog/2012/8/20/the-performance-of-distributed-data-structures-running-on-a.html) contains an amortized cost analysis, in terms of network roundtrips, of a B+ tree distributed with Galaxy.

Getting Started
---------------

Galaxy releases can be downloaded [here](http://puniverse.github.com/galaxy/start/getting-started.html).


Mailing List / Forum
--------------------

Galaxy's mailing list/forum is found on Goolge Groups [here](https://groups.google.com/forum/#!forum/galaxy-user).

License
---------

Galaxy is free software published under the following license:

```
Copyright (c) 2013-2014, Parallel Universe Software Co. All rights reserved.

This program and the accompanying materials are dual-licensed under
either the terms of the Eclipse Public License v1.0 as published by
the Eclipse Foundation
 
  or (per the licensee's choosing)
 
under the terms of the GNU Lesser General Public License version 3.0
as published by the Free Software Foundation.
```

---
