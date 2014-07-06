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

import co.paralleluniverse.common.collection.ConcurrentMultimap;
import co.paralleluniverse.common.concurrent.WithExecutor;
import co.paralleluniverse.common.io.Streamable;
import co.paralleluniverse.common.io.Streamables;
import co.paralleluniverse.common.spring.Component;
import co.paralleluniverse.galaxy.MessageListener;
import co.paralleluniverse.galaxy.Messenger;
import co.paralleluniverse.galaxy.TimeoutException;
import co.paralleluniverse.galaxy.core.Message.LineMessage;
import co.paralleluniverse.galaxy.core.Message.MSG;
import co.paralleluniverse.galaxy.core.Op.Type;
import com.google.common.util.concurrent.ListenableFuture;
import java.beans.ConstructorProperties;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class MessengerImpl extends Component implements Messenger {
    private static final Logger LOG = LoggerFactory.getLogger(MessengerImpl.class);
    private final AtomicLong topicGenerator = new AtomicLong();
    private final Cache cache;
    private final ConcurrentMultimap<Long, MessageListener, List<MessageListener>> longTopicListeners = new ConcurrentMultimap<Long, MessageListener, List<MessageListener>>(new NonBlockingHashMapLong<List<MessageListener>>(), (List<MessageListener>) Collections.EMPTY_LIST) {
        @Override
        protected List<MessageListener> allocateElement() {
            return new CopyOnWriteArrayList<MessageListener>();
        }
    };
    private final ConcurrentMultimap<String, MessageListener, List<MessageListener>> stringTopicListeners = new ConcurrentMultimap<String, MessageListener, List<MessageListener>>(new NonBlockingHashMap<String, List<MessageListener>>(), (List<MessageListener>) Collections.EMPTY_LIST) {
        @Override
        protected List<MessageListener> allocateElement() {
            return new CopyOnWriteArrayList<MessageListener>();
        }
    };
    private final NodeOrderedThreadPoolExecutor executor;

    @ConstructorProperties({"name", "cache", "threadPool"})
    MessengerImpl(String name, Cache cache, NodeOrderedThreadPoolExecutor threadPool) {
        super(name);
        this.executor = threadPool;
        if (executor == null)
            throw new RuntimeException("The executor must be set!");
        this.cache = cache;
        cache.setReceiver(new MessageReceiver() {
            @Override
            public void receive(Message message) {
                if (message.getType() == Message.Type.MSGACK) {
                    // do nothing - at least in this version
                } else
                    MessengerImpl.this.receive((MSG) message);
                }
        });
    }

    @Override
    public long createTopic() {
        return topicGenerator.incrementAndGet();
    }
    
    @Override
    public void addMessageListener(long topic, MessageListener listener) {
        longTopicListeners.put(topic, listener);
    }

    @Override
    public void removeMessageListener(long topic, MessageListener listener) {
        longTopicListeners.remove(topic, listener);
    }

    @Override
    public void addMessageListener(String topic, MessageListener listener) {
        stringTopicListeners.put(topic, listener);
    }

    @Override
    public void removeMessageListener(String topic, MessageListener listener) {
        stringTopicListeners.remove(topic, listener);
    }

    @Override
    public void send(short node, long topic, byte[] data) {
        sendToNode(node, new Msg(topic, null, data));
    }

    @Override
    public void send(short node, String topic, byte[] data) {
        if (topic == null)
            throw new IllegalArgumentException("Topic must not be null");
        sendToNode(node, new Msg(-1, topic, data));
    }

    @Override
    public void send(short node, long topic, Streamable data) {
        sendToNode(node, new Msg(topic, null, data));
    }

    @Override
    public void send(short node, String topic, Streamable data) {
        if (topic == null)
            throw new IllegalArgumentException("Topic must not be null");
        sendToNode(node, new Msg(-1, topic, data));
    }

    @Override
    public void sendToOwnerOf(long ref, long topic, byte[] data) throws TimeoutException {
        sendToOwnerOf(ref, new Msg(topic, null, data));
    }

    @Override
    public void sendToOwnerOf(long ref, String topic, byte[] data) throws TimeoutException {
        if (topic == null)
            throw new IllegalArgumentException("Topic must not be null");
        sendToOwnerOf(ref, new Msg(-1, topic, data));
    }

    @Override
    public void sendToOwnerOf(long ref, long topic, Streamable data) throws TimeoutException {
        sendToOwnerOf(ref, new Msg(topic, null, data));
    }

    @Override
    public void sendToOwnerOf(long ref, String topic, Streamable data) throws TimeoutException {
        if (topic == null)
            throw new IllegalArgumentException("Topic must not be null");
        sendToOwnerOf(ref, new Msg(-1, topic, data));
    }

    @Override
    public ListenableFuture<Void> sendToOwnerOfAsync(long ref, long topic, byte[] data) {
        return sendToOwnerOfAsync(ref, new Msg(topic, null, data));
    }

    @Override
    public ListenableFuture<Void> sendToOwnerOfAsync(long ref, String topic, byte[] data) {
        if (topic == null)
            throw new IllegalArgumentException("Topic must not be null");
        return sendToOwnerOfAsync(ref, new Msg(-1, topic, data));
    }

    @Override
    public ListenableFuture<Void> sendToOwnerOfAsync(long ref, long topic, Streamable data) {
        return sendToOwnerOfAsync(ref, new Msg(topic, null, data));
    }

    @Override
    public ListenableFuture<Void> sendToOwnerOfAsync(long ref, String topic, Streamable data) {
        if (topic == null)
            throw new IllegalArgumentException("Topic must not be null");
        return sendToOwnerOfAsync(ref, new Msg(-1, topic, data));
    }

    private void sendToNode(short node, Msg msg) {
        if (LOG.isDebugEnabled())
            LOG.debug("Sending to node {}: {}", node, msg);
        cache.send(Message.MSG(node, -1, true, Streamables.toByteArray(msg)));
    }

    private void sendToOwnerOf(long line, Msg msg) throws TimeoutException {
        if (LOG.isDebugEnabled())
            LOG.debug("Sending to owner of {}: {}", Long.toHexString(line), msg);
        final LineMessage message = Message.MSG((short) -1, line, true, Streamables.toByteArray(msg));
        cache.doOp(Type.SEND, line, null, message, null);
    }

    private ListenableFuture<Void> sendToOwnerOfAsync(long line, Msg msg) {
        if (LOG.isDebugEnabled())
            LOG.debug("Sending to owner of {}: {}", Long.toHexString(line), msg);
        final LineMessage message = Message.MSG((short) -1, line, true, Streamables.toByteArray(msg));
        return (ListenableFuture<Void>) (Object) cache.doOpAsync(Type.SEND, line, null, message, null);
    }

    private void receive(MSG message) {
        final Msg msg = new Msg();
        Streamables.fromByteArray(msg, message.getData());
        LOG.debug("Received: {}", msg);
        final Collection<MessageListener> ls = msg.hasSTopic() ? stringTopicListeners.get(msg.getsTopic()) : longTopicListeners.get(msg.getlTopic());
        if (ls != null)
            notifyListeners(ls, message.getNode(), msg);
    }

    private void notifyListeners(final Collection<MessageListener> listeners, final short node, final Msg msg) {
        executor.execute(new NodeTask() {
            @Override
            public short getNode() {
                return node;
            }

            @Override
            public void run() {
                synchronized (listeners) { // make topic messages serial
                    for (final MessageListener listener : listeners) {
                        if (!(listener instanceof WithExecutor)) {
                            try {
                                listener.messageReceived(node, msg.getData());
                            } catch (Exception e) {
                                LOG.error("Listener threw an exception.", e);
                            }
                        } else {
                            ((WithExecutor) listener).getExecutor().execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        listener.messageReceived(node, msg.getData());
                                    } catch (Exception e) {
                                        LOG.error("Listener threw an exception.", e);
                                    }
                                }
                            });
                        }
                    }
                }
            }
        });
    }

    private static class Msg implements Streamable {
        private long lTopic = -1;
        private String sTopic = null;
        private byte[] data;

        public Msg() {
        }

        public Msg(long topic, byte[] data) {
            this(topic, null, data);
        }

        public Msg(String topic, byte[] data) {
            this(-1, topic, data);
            assert topic != null;
        }

        public Msg(long topic, Streamable data) {
            this(topic, null, data);
        }

        public Msg(String topic, Streamable data) {
            this(-1, topic, data);
            assert topic != null;
        }

        private Msg(long lTopic, String sTopic, byte[] data) {
            this.lTopic = lTopic;
            this.sTopic = sTopic;
            this.data = data;
        }

        private Msg(long lTopic, String sTopic, Streamable data) {
            this(lTopic, sTopic, Streamables.toByteArray(data));
        }

        public boolean hasSTopic() {
            return sTopic != null;
        }

        public long getlTopic() {
            return lTopic;
        }

        public String getsTopic() {
            return sTopic;
        }

        public byte[] getData() {
            return data;
        }

        @Override
        public int size() {
            return 1 + (lTopic != -1 ? 8 : Streamables.calcUtfLength(sTopic) + 2 + data.length);
        }

        @Override
        public void write(DataOutput out) throws IOException {
            final boolean hasSTopic = hasSTopic();
            out.writeBoolean(hasSTopic);
            if (hasSTopic)
                out.writeUTF(sTopic);
            else
                out.writeLong(lTopic);
            out.writeShort((short) data.length);
            out.write(data);
        }

        @Override
        public void read(DataInput in) throws IOException {
            final boolean hasSTopic = in.readBoolean();
            if (hasSTopic) {
                lTopic = -1;
                sTopic = in.readUTF();
            } else {
                lTopic = in.readLong();
                sTopic = null;
            }
            final int dataLength = in.readUnsignedShort();
            data = new byte[dataLength];
            in.readFully(data);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Msg[");
            sb.append("Topic: ");
            if (sTopic != null)
                sb.append('"').append(sTopic).append('"');
            else
                sb.append(lTopic);
            sb.append(" data: ");
            if (data == null)
                sb.append("null");
            else
                sb.append(("(")).append(data.length).append(" bytes)");
            sb.append("]");
            return sb.toString();
        }
    }
}
