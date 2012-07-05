.. _about:

###############
About Galaxy
###############

Galaxy is a high-performance in-memory data-grid (IMDG) that can serve as a basis for building distributed applications
that require fine-tuned control over data placement and/or custom distributed data-structures.

Galaxy is developed by `Parallel Universe`__ and released as free software under the GNU Lesser General Public License.

.. __: http://paralleluniverse.co

.. _about-license:

License
=======

.. code::

  Galaxy
  Copyright (C) 2012 Parallel Universe Software Co.
  
  Galaxy is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as 
  published by the Free Software Foundation, either version 3 of 
  the License, or (at your option) any later version.
  
  Galaxy is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.
  
  You should have received a copy of the GNU Lesser General Public 
  License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.


.. _about-dependencies:

Dependencies
============

Galaxy uses many other open-source libraries.

Required Dependencies
---------------------

* Netty_ an asynchronous event-driven network application framework
* Metrics_, capturing JVM- and application-level metrics.
* `Spring Framework`_ 
* SLF4J_, a simple logging facade for Java
* `Google Guava Libraries`_
* Trove_, high performance collections for Java
* `Highly Scalable Java (high-scale-lib)`_, a collection of concurrent and highly scalable utilities.
* ConcurrentLinkedHashMap_, a high performance version of java.util.LinkedHashMap for use as a software cache.

.. _Netty: http://netty.io/
.. _Metrics: http://metrics.codahale.com/
.. _`Spring Framework`: http://www.springsource.org/spring-framework
.. _SLF4J: http://www.slf4j.org/
.. _`Google Guava Libraries`: http://code.google.com/p/guava-libraries/
.. _Trove: http://trove.starlight-systems.com/
.. _`Highly Scalable Java (high-scale-lib)`: http://sourceforge.net/projects/high-scale-lib/
.. _ConcurrentLinkedHashMap: http://code.google.com/p/concurrentlinkedhashmap/

Optional Dependencies
---------------------

* `Apache ZooKeeper`_ + Curator_, a ZooKeeper client wrapper and rich ZooKeeper framework
* JGroups_, a toolkit for reliable multicast communication
* `BerkeleyDB Java Edition`_

.. _`Apache ZooKeeper`: http://zookeeper.apache.org/
.. _Curator: https://github.com/Netflix/curator
.. _JGroups: http://www.jgroups.org/
.. _`BerkeleyDB Java Edition`: http://www.oracle.com/technetwork/products/berkeleydb/overview/index.html
