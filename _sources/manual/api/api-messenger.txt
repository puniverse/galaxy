.. _man-api-messenger:

#########
Messenger
#########

The ``Messenger`` (:javadoc:`Javadoc <co/paralleluniverse/galaxy/Messenger.html>`__)
lets you send point-to-point messages to other nodes in the cluster, and is used alongside the
:ref:`data-store <man-api-store>` to distribute your application in the grid. While Galaxy's data-store moves data around
the grid to be processed at the appropriate node (moving data to code), messages are used to request nodes to carry out operations
without migrating data (moving code to data).

There are two ways of routing your messages. You can send a message to a known node, or you can send it to the (unknown) owner
of a specific data item.

.. _man-api-messenger-topics:

Message topics
==============

Messages are sent and received by **topics**. Topics are the mechanism by which messages are delivered to the appropriate 
recipient within the node. There are two kinds of topics you can use. ``long`` topics and ``String`` topics.

To receive messages, you register a listener that will receive all incoming messages sent to a specific topic in the local node.
You register receivers like so:

.. code-block:: java

    messenger.addMessageListener(topic, myListener);
    
With ``topic`` being either a ``String`` or a ``long``, and ``myListener`` is an object implementing ``MessageListener``
(:javadoc:`Javadoc <co/paralleluniverse/galaxy/MessageListener.html>`__).

When a message sent to ``myTopic`` on the local node is received, ``myListener``'s ``messageReceived`` method
(:javadoc:`Javadoc <co/paralleluniverse/galaxy/MessageListener.html#messageReceived(short, byte[])>`)
will be called, and passed the message payload and the sending node.

You remove a listener by calling ``removeMessageListener``.

.. _man-api-messenger-routing:

Routing messages
================

One way of addressing messages is sending them to a well known node. You would usually use this option when replying to received
messages, in which case the original sender is passed to the ``MessageListener``'s ``messageReceived`` method
(:javadoc:`Javadoc <co/paralleluniverse/galaxy/MessageListener.html#messageReceived(short, byte[])>`). 

To send a message to a well known node use one of the ``send`` methods, and pass it the node id (a ``short``), the topic (either
a ``long`` or a ``String``), and the message payload, like so:

.. code-block:: java

    messenger.send(node, topic, message);

The other way of addressing messages is sending them to the owner of a known data item. To do that, use one of the ``sendToOwnerOf``
methods, and pass it the item id, the topic and the message payload, like so:

.. code-block:: java

    messenger.sendToOwnerOf(itemId, topic, message);

.. _man-api-messenger-delivery:

Delivery guarantees
===================

Messages are guaranteed to be delivered, and to arrive in the order they were sent (i.e. two messages M and N that are sent in this 
order from node A to B, will be received by B in the same order, regardless of the messages' topics).

Messages are delivered by the same communication channel that delivers Galaxy's internal coherence protocol messages used to move
data items around, so messages are also guaranteed to be ordered with data operations. 

Say that node A updates data item X, and then sends a message M to the owner of data item Y (which happens to be node B), 
and as a result of receiving M, node B reads the value of X. 
In this case node B is guaranteed to read the value of X after the update done by A before sending the message.

These guarantees make it simple to distribute data processing in the grid.