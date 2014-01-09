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
package co.paralleluniverse.galaxy.netty;

import co.paralleluniverse.common.collection.Util;
import co.paralleluniverse.galaxy.core.Message;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class MessagePacket implements Iterable<Message>, Cloneable {
    private int size;
    private int numBuffers;
    private transient boolean multicast;
    private transient long timestamp;
    private ArrayList<Message> messages = new ArrayList<Message>();
    private static final Logger LOG = LoggerFactory.getLogger(MessagePacket.class);

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void addMessage(Message message) {
        assert messages.size() < 256;
        messages.add(message);
        size += message.size();
        numBuffers += 1 + message.getNumDataBuffers();
    }

    public boolean removeMessage(Message m) {
        int index = messages.indexOf(m);
        Message message = messages.get(index); // m != message. often m will be the response to message
        if (index >= 0) {
            messages.remove(index);
            messageRemoved(message);
            return true;
        } else
            return false;
    }

    public boolean removeMessage(long id) {
        for (Iterator<Message> it = messages.iterator(); it.hasNext();) {
            final Message message = it.next();
            if (message.getMessageId() != id)
                continue;

            it.remove();
            messageRemoved(message);
            return true;
        }
        return false;
    }

    private void messageRemoved(Message message) {
        size -= message.size();
        numBuffers -= 1 + message.getNumDataBuffers();
    }

    public Message getMessage(Message m) {
        int index = messages.indexOf(m);
        if (index < 0)
            return null;
        Message message = messages.get(index); // m != message. often m will be the response to message
        return message;
    }

    public boolean contains(Message m) {
        return messages.contains(m);
    }

    public boolean contains(long id) {
        for (Iterator<Message> it = messages.iterator(); it.hasNext();) {
            final Message message = it.next();
            if (message.getMessageId() != id)
                continue;

            return true;
        }
        return false;
    }

    public int numMessages() {
        return messages.size();
    }

    public ArrayList<Message> getMessages() {
        return messages;
    }

    public boolean isMulticast() {
        return multicast;
    }

    public void setMulticast() {
        this.multicast = true;
    }

    @Override
    public Iterator<Message> iterator() {
        return wrapIterator(messages.iterator());
    }

    public Iterator<Message> reverseIterator() {
        return wrapIterator(Util.reverse(messages).iterator());
    }

    private Iterator<Message> wrapIterator(final Iterator<Message> it) {
        return new Iterator<Message>() {
            private Message message;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Message next() {
                message = it.next();
                return message;
            }

            @Override
            public void remove() {
                it.remove();
                messageRemoved(message);
            }
        };
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int sizeInBytes() {
        return size;
    }

    public int getNumBuffers() {
        return numBuffers;
    }

    public ByteBuffer[] toByteBuffers() {
        ByteBuffer[] buffers = new ByteBuffer[numBuffers];
        int i = 0;
        for (Message message : messages) {
            ByteBuffer[] bs = message.toByteBuffers();
            for (ByteBuffer b : bs) {
                if (b != null)
                    b.rewind();
                buffers[i++] = b;
            }
        }
        return buffers;
    }

    public void fromByteBuffer(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            if (LOG.isDebugEnabled())
                LOG.debug("decoding. remaining " + buffer.remaining());
            final Message fromByteBuffer = Message.fromByteBuffer(buffer);
            if (LOG.isDebugEnabled())
                LOG.debug("decoded " + fromByteBuffer);
            addMessage(fromByteBuffer);
        }
    }

    public short getNode() {
        return messages.iterator().next().getNode();
    }

    public void setNode(short node) {
        for (Message m : messages)
            m.setNode(node);
    }

    @Override
    public String toString() {
        return "MessagePacket[" + messages + ']';
    }

    @Override
    public MessagePacket clone() {
        try {
            final MessagePacket clone = (MessagePacket) super.clone();
            clone.messages = (ArrayList<Message>) messages.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}
