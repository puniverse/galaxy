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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeAddressResolver;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public abstract class AbstractComm<Address> extends ClusterService implements Comm, NodeChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractComm.class);
    final protected NodeAddressResolver<Address> addressResolver;
    private final AtomicLong nextMessageId = new AtomicLong(1L);
    private final Cluster cluster;
    private MessageReceiver receiver;
    private long timeout = 200;
    protected boolean sendToServerInsteadOfMulticast;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public AbstractComm(String name, Cluster cluster, NodeAddressResolver<Address> addressResolver) {
        super(name, cluster);
        this.addressResolver = addressResolver;
        this.cluster = cluster;
        cluster.addNodeChangeListener(this);
    }

    public void setSendToServerInsteadOfMulticast(boolean value) {
        assertDuringInitialization();
        this.sendToServerInsteadOfMulticast = value;
    }

    public boolean isSendToServerInsteadOfMulticast() {
        return sendToServerInsteadOfMulticast;
    }

    public void setTimeout(long milliseconds) {
        assertDuringInitialization();
        this.timeout = milliseconds;
    }

    protected long getTimeout() {
        return timeout;
    }

    @Override
    public void setReceiver(MessageReceiver receiver) {
        assertDuringInitialization();
        this.receiver = receiver;
    }

    protected short getNode(Address address) {
        return addressResolver.getNodeId(address);
    }

    protected Address getNodeAddress(short node) {
        return addressResolver.getNodeAddress(node);
    }

    protected long nextMessageId() {
        return nextMessageId.getAndIncrement();
    }

    protected ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    /**
     * Blocks
     */
    @Override
    public void send(final Message message) throws NodeNotFoundException {
        assert message.getMessageId() <= 0 ^ message.isResponse();
        message.setTimestamp(System.nanoTime());

        assert message.isBroadcast() ^ message.getNode() >= 0;

        if (message.getNode() == cluster.getMyNodeId()) {
            scheduler.execute(new Runnable() {
                @Override
                public void run() {
                    receive(message);
                }
            });
            return;
        }

        if (getCluster().hasServer()) {
            if (message.isBroadcast()
                    && (sendToServerInsteadOfMulticast || (message instanceof LineMessage && Cache.isReserved(((LineMessage) message).getLine()))))
                message.setNode(SERVER);
        }

        if (message.getNode() == SERVER)
            sendToServer(message);
        else if (message.getNode() >= 0)
            sendToNode(message, message.getNode());
        else
            broadcast(message);
    }

    protected void assignMessageId(Message message) {
        if (message.getMessageId() < 0)
            message.setMessageId(nextMessageId()); // TODO: possible pitfall: b/c this method is not synchronized, two threads may run it concurrently, one would get a smaller id bu the other would put the message in a queue first - broken invariant!
    }

    /**
     * Doesn't add to pending. Assumes a different comm for server, but if not, the derived can add to pending.
     *
     * @param message
     */
    protected void sendToServer(Message message) {
        assignMessageId(message);
        LOG.debug("Sending to server: {}", message);
    }

    protected void sendToNode(Message message, short node) throws NodeNotFoundException {
        if (LOG.isDebugEnabled())
            LOG.debug("Sending to node {}: {}", node, message);
        final Address address = getNodeAddress(node);
        if (address == null) {
            LOG.warn("Address not found for node {} while sending {}!", node, message);
            throw new NodeNotFoundException(node);
        } else
            sendToNode(message, node, address);
    }

    protected abstract void sendToNode(Message message, short node, Address address);

    protected abstract void broadcast(Message message);

    protected final void receive(Message message) {
        if (getCluster().isMaster())
            receiver.receive(message);
    }

    @Override
    public void nodeAdded(short id) {
    }

    @Override
    public void nodeSwitched(short id) {
    }

    @Override
    public void nodeRemoved(short id) {
    }

    @Override
    protected void shutdown() {
        super.shutdown();
        scheduler.shutdownNow();
    }
}
