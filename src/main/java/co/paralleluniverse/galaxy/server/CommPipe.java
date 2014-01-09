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
package co.paralleluniverse.galaxy.server;

import co.paralleluniverse.galaxy.core.Comm;
import co.paralleluniverse.galaxy.core.Message;
import co.paralleluniverse.galaxy.core.MessageReceiver;
import co.paralleluniverse.galaxy.core.ServerComm;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipes messages between two comms.
 * @author pron
 */
public class CommPipe {
    private static final Logger LOG = LoggerFactory.getLogger(CommPipe.class);
    private final Executor queue = Executors.newSingleThreadExecutor();
    private MessageReceiver receiver1;
    private short node1;
    private MessageReceiver receiver2;
    private short node2 = 0;
    private final Comm comm1 = new ServerComm() {
        @Override
        public void setReceiver(MessageReceiver receiver) {
            receiver1 = receiver;
        }

        @Override
        public void send(Message message) {
            final Message m = message.clone();
            m.setNode(node1);
            m.setIncoming();
            queue.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (LOG.isDebugEnabled())
                            LOG.debug("\"{}\" Received {}", node2, m);
                        receiver2.receive(m);
                    } catch (Exception e) {
                        LOG.error("Exception while processing message.", e);
                    }
                }
            });
        }
    };
    private final Comm comm2 = new Comm() {
        @Override
        public void setReceiver(MessageReceiver receiver) {
            receiver2 = receiver;
        }

        @Override
        public void send(Message message) {
            final Message m = message.clone();
            m.setNode(node2);
            m.setIncoming();
            try {
                LOG.debug("Received {}", m);
                receiver1.receive(m);
            } catch (Exception e) {
                LOG.error("Exception while processing message.", e);
            }
        }
    };

    /**
     * Messages sent from comm1 are queued before transfered to comm2, and are received on a pooled thread.
     * @return
     */
    public synchronized Comm getComm1(short node1) {
        this.node1 = node1;
        return comm1;
    }

    /**
     * Messages from comm2 are processed by comm1 on the current thread (the one that called {@link Comm#send(co.paralleluniverse.galaxy.Message) Comm.send()})
     * @return
     */
    public synchronized Comm getComm2(short node2) {
        this.node2 = node2;
        return comm2;
    }
}
