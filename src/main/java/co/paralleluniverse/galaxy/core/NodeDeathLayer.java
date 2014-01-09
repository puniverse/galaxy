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

import co.paralleluniverse.common.collection.ConcurrentMapComplex;
import co.paralleluniverse.galaxy.Cluster;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.util.Iterator;
import java.util.Set;

/**
 * This class is unused as it's insufficient to invalidate S lines whose owner has died.
 * @author pron
 */
class NodeDeathLayer implements Comm, NodeChangeListener, MessageReceiver {
    private final Comm comm;
    private final Cluster cluster;
    private MessageReceiver cache;
    private final ConcurrentMapComplex<Short, SetMultimap<Long, LineMessage>> pending = new ConcurrentMapComplex<Short, SetMultimap<Long, LineMessage>>() {

        @Override
        protected SetMultimap<Long, LineMessage> allocateElement() {
            SetMultimap<Long, LineMessage> mm = HashMultimap.create();
            return Multimaps.synchronizedSetMultimap(mm);
        }

        @Override
        protected SetMultimap<Long, LineMessage> emptyElement() {
            return ImmutableSetMultimap.of();
        }
    };

    public NodeDeathLayer(Comm comm, Cluster cluster) {
        this.comm = comm;
        this.cluster = cluster;
        
        comm.setReceiver(this);
        cluster.addNodeChangeListener(this);
    }

    @Override
    public void setReceiver(MessageReceiver receiver) {
        cache = receiver;
    }

    public void addPending(LineMessage message) {
        short node = message.getNode();
        if (node != -1)
            pending.getOrAllocate(node).put(message.getLine(), message);
    }

    public void removePending(LineMessage message) {
        Set<LineMessage> msgs = pending.get(message.getNode()).get(message.getLine());
        for (Iterator<LineMessage> it = msgs.iterator(); it.hasNext();) {
            Message msg = it.next();
            if (message.getType() == Message.Type.INVACK && msg.getType() == Message.Type.INV)
                it.remove();
            else if (message.getType() == Message.Type.PUTX && (msg.getType() == Message.Type.GETX || msg.getType() == Message.Type.GET))
                it.remove();
            else if (message.getType() == Message.Type.PUT && msg.getType() == Message.Type.GET)
                it.remove();
        }
    }

    @Override
    public void receive(Message message) {
        removePending((LineMessage)message);
        cache.receive(message);
    }

    @Override
    public void send(Message message) {
        try {
            comm.send(message);
        } catch (NodeNotFoundException e) {
            final Message response = genResponse((LineMessage)message);
            if (response != null)
                cache.receive(shortCircuitMessage(message.getNode(), response));
        }
    }

    @Override
    public void nodeRemoved(short node) {
        final Multimap<Long, LineMessage> mm = pending.get(node);
        for (LineMessage m : mm.values()) {
            final Message response = genResponse(m);
            if (response != null)
                cache.receive(shortCircuitMessage(m.getNode(), response));
        }
        pending.remove(node);
    }

    private Message genResponse(Message message) {
        switch (message.getType()) {
            case INV:
                return Message.INVACK((Message.INV)message);
            case GET:
            case GETX:
                return Message.CHNGD_OWNR((LineMessage)message, ((LineMessage)message).getLine(), (short) -1, false);
            default:
                return null;
            // don't send message
            }
    }

    @Override
    public void nodeAdded(short node) {
    }

    private Message shortCircuitMessage(short node, Message message) {
        message.setIncoming();
        message.setNode(node);
        return message;
    }

    @Override
    public void nodeSwitched(short id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
