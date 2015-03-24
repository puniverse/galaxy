/*
 * Galaxy
 * Copyright (c) 2012-2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *  
 *   or (per the licensee's choosing)
 *  
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package co.paralleluniverse.galaxy.jgroups;

import co.paralleluniverse.common.collection.ConcurrentMultimap;
import static co.paralleluniverse.common.collection.Util.reverse;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.core.AbstractComm;
import co.paralleluniverse.galaxy.core.Comm;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.MessageReceiver;
import co.paralleluniverse.galaxy.core.NodeNotFoundException;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.beans.ConstructorProperties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.jgroups.Address;
import org.jgroups.ReceiverAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
class JGroupsComm extends AbstractComm<Address> {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsComm.class);
    private final Channel channel;
    private final Comm serverComm;
    private final ConcurrentMultimap<Short, Message, Deque<Message>> pendingReply = new ConcurrentMultimap<Short, Message, Deque<Message>>(new ArrayDeque<Message>(0)) {
        @Override
        protected Deque<Message> allocateElement() {
            return new ConcurrentLinkedDeque<Message>();
        }
    };
    private ConcurrentMap<Long, BroadcastEntry> pendingBroadcasts;

    @ConstructorProperties({"name", "cluster", "serverComm"})
    public JGroupsComm(String name, Cluster cluster, Comm serverComm) {
        super(name, cluster, new JGroupsNodeAddressResolver(cluster));
        this.channel = getCluster().getDataChannel();
        this.serverComm = serverComm;
        channel.setReceiver(new ReceiverAdapter() {
            @Override
            public void receive(org.jgroups.Message msg) {
                JGroupsComm.this.receive(msg);
            }
        });
        this.sendToServerInsteadOfMulticast = (serverComm != null); // this is just the default
    }

    @Override
    public void setReceiver(MessageReceiver receiver) {
        super.setReceiver(receiver);
        if (serverComm != null)
            serverComm.setReceiver(receiver);
    }

    @Override
    public void init() throws Exception {
        super.init();
        if (sendToServerInsteadOfMulticast && serverComm == null)
            throw new RuntimeException("sendToServerInsteadOfBroadcast is set to true but no serverComm set");
    }

    @Override
    public void postInit() throws Exception {
        if (!sendToServerInsteadOfMulticast)
            this.pendingBroadcasts = new ConcurrentHashMap<Long, BroadcastEntry>();
        super.postInit();
    }

    @Override
    protected void start(boolean master) {
        final long timeoutNano = TimeUnit.NANOSECONDS.convert(getTimeout(), TimeUnit.MILLISECONDS);
        getScheduler().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                final long now = System.nanoTime();

                if (pendingBroadcasts != null) {
                    for (BroadcastEntry entry : pendingBroadcasts.values()) {
                        final Message message = entry.message;
                        if (message.getType() != Message.Type.INVACK && now - message.getTimestamp() > timeoutNano) {
                            if (pendingBroadcasts.remove(message.getMessageId()) != null) {
                                LOG.debug("Timeout on message {}", message);
                                receive(Message.TIMEOUT((LineMessage) message).setIncoming());
                            }
                        }
                    }
                }
                for (Deque<Message> pending : pendingReply.values()) {
                    for (Message message : reverse(pending)) {
                        if (message.getType() != Message.Type.INVACK && now - message.getTimestamp() > timeoutNano) {
                            if (pending.removeLastOccurrence(message)) {// we're using this instead of iterators to safeguard against the case that a reply just arrives
                                LOG.debug("Timeout on message {}", message);
                                receive(Message.TIMEOUT((LineMessage) message).setIncoming());
                            }
                        } else
                            break; // the rest are younger b/c/ new messages are appended to the head.
                    }
                }
                if (hasPendingBroadcasts()) { // flush broadcasts (necessary because of NACKACK)
                    try {
                        channel.send(new org.jgroups.Message(null, new byte[0]));
                    } catch (Exception ex) {
                        LOG.error("Error while broadcasting flush.", ex);
                    }
                }
            }
        }, 0, getTimeout() / 2, TimeUnit.MILLISECONDS);
        setReady(true);
    }

    @Override
    public final JGroupsCluster getCluster() {
        return (JGroupsCluster) super.getCluster();
    }

    protected boolean hasPendingBroadcasts() {
        return !pendingBroadcasts.isEmpty();
    }

    protected boolean addToPending(Message message, short node) {
        if (!message.getType().isOf(Message.Type.REQUIRES_RESPONSE)) {
            LOG.debug("Message {} does not require a response.", message);
            return true;
        }

        if (node >= 0) {
            LOG.debug("Enqueing message in pending replies {}", message);
            pendingReply.getOrAllocate(node).addFirst(message);
        } else {
            assert message.isBroadcast();
            assert message instanceof LineMessage;
            final Set<Short> nodes = getCluster().getNodes();
            if (message instanceof LineMessage) {
                if (nodes.isEmpty() || (nodes.size() == 1 && nodes.contains(Comm.SERVER))) {
                    LOG.debug("No other nodes in cluster. Responding with NOT_FOUND to message {}", message);
                    receive(Message.NOT_FOUND((LineMessage) message).setIncoming());
                    return false;
                } else {
                    LOG.debug("Enqueing message in pending broadcasts {}", message);
                    pendingBroadcasts.put(message.getMessageId(), new BroadcastEntry((LineMessage) message, nodes));
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    protected void sendToNode(Message message, short node, Address address) {
        assignMessageId(message);
        addToPending(message, node);
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Sending to node {} ({}): {}", new Object[]{node, address, message});
            final byte[] content = message.toByteArray();
            channel.send(new org.jgroups.Message(address, content));
        } catch (Exception ex) {
            LOG.error("Error while sending message " + message + " to node " + node, ex);
        }
    }

    @Override
    protected void sendToServer(Message message) {
        super.sendToServer(message);
        try {
            serverComm.send(message);
        } catch (NodeNotFoundException e) {
            throw new RuntimeException("Server not found!", e);
        }
    }

    @Override
    protected void broadcast(Message message) {
        assignMessageId(message);
        if (addToPending(message, (short) -1)) {
            try {
                LOG.debug("Broadcasting (null): {}", message);
                final byte[] content = message.toByteArray();
                channel.send(new org.jgroups.Message(null, content));
            } catch (Exception ex) {
                LOG.error("Error while broadcasting message " + message, ex);
            }
        }
    }

    private void receive(org.jgroups.Message msg) {
        try {
            LOG.debug("Received {}", msg);
            if (getCluster().getMyAddress() != null && msg.getSrc() != null && getCluster().getMyAddress().equals(msg.getSrc()))
                return; // discard own (cannot set the flag because it screws up th control channel. not much to do about it - annoing up handler in JChannel)
            if (msg.getLength() == 0)
                return; // probably just a flush
            final short sourceNode = getNode(msg.getSrc());
            if (sourceNode < 0)
                throw new RuntimeException("Node not found for source address " + msg.getSrc());

            final Message message = Message.fromByteArray(msg.getRawBuffer(), msg.getOffset(), msg.getLength()).setIncoming().setNode(sourceNode); // Message.fromByteArray(msg.getBuffer());

            if (message.isResponse()) {
                final Deque<Message> pending = pendingReply.get(message.getNode());
                if (pending != null) {
                    boolean res = pending.removeLastOccurrence(message); // relies on Message.equals that matches request/reply
                    if (res)
                        LOG.debug("Message {} is a reply! (removing from pending)", message);
                }

                final BroadcastEntry entry = pendingBroadcasts.get(message.getMessageId());
                if (entry != null) {
                    if (message.getType() != Message.Type.ACK) {// this is a response - no need to wait for further acks
                        LOG.debug("Message {} is a reply to a broadcast! (discarding pending)", message);
                        pendingBroadcasts.remove(message.getMessageId());
                    } else
                        removeFromPendingBroadcasts(message.getMessageId(), message.getNode());
                }
            }

            message.setNode(sourceNode);
            receive(message);
        } catch (Exception ex) {
            LOG.error("Error receiving message", ex);
        }
    }

    @Override
    public void nodeAdded(short id) {
        super.nodeAdded(id);
        try {
            for (Message message : reverse(pendingReply.get(id)))
                sendToNode(message, id);
        } catch (NodeNotFoundException e) {
            throw new AssertionError();
        }
    }

    @Override
    public void nodeSwitched(short id) {
        super.nodeSwitched(id);
        try {
            for (Message message : reverse(pendingReply.get(id)))
                sendToNode(message, id);
            for (BroadcastEntry entry : pendingBroadcasts.values())
                sendToNode(entry.message, id);
        } catch (NodeNotFoundException e) {
            throw new AssertionError();
        }
    }

    @Override
    public void nodeRemoved(short id) {
        super.nodeRemoved(id);
        pendingReply.remove(id);
        for (Long messageId : pendingBroadcasts.keySet())
            removeFromPendingBroadcasts(messageId, id);
    }

    private void removeFromPendingBroadcasts(long messageId, short node) {
        final BroadcastEntry entry = pendingBroadcasts.get(messageId);
        if (LOG.isDebugEnabled())
            LOG.debug("Got ACK from {} to message {}", node, entry.message);
        if (entry.removeNode(node)) {
            LOG.debug("Got all ACKs for message {}, but no response - sending NOT_FOUND to cache!", entry.message);
            receive(Message.NOT_FOUND(entry.message).setIncoming());
            pendingBroadcasts.remove(messageId);
        }
    }

    private static class BroadcastEntry {
        final LineMessage message;
        final ShortSet nodes;

        public BroadcastEntry(LineMessage message, Set<Short> nodes) {
            this.message = message;
            this.nodes = new ShortOpenHashSet(nodes);
            this.nodes.remove(Comm.SERVER); // NOT TO SERVER
            LOG.debug("Awaiting ACKS for message {} from nodes {}", message, this.nodes);
        }

        public synchronized void addNode(short node) {
            nodes.add(node);
        }

        public synchronized boolean removeNode(short node) {
            nodes.remove(node);
            return nodes.isEmpty();
        }
    }
}
