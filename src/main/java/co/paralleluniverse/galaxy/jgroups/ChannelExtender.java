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

import org.jgroups.Address;
import org.jgroups.ChannelListener;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.stack.Protocol;
import org.jgroups.util.UUID;

/**
 *
 * @author pron
 */
public class ChannelExtender implements ExtendedChannel {

    private final Channel channel;

    public ChannelExtender(Channel jchannel) {
        this.channel = jchannel;
    }

    @Override
    public void send(Address dst, byte[] buf, int offset, int length) throws Exception {
        send(new Message(dst, null, buf, offset, length));
    }

    @Override
    public void send(Address dst, byte[] buf) throws Exception {
        send(new Message(dst, null, buf));
    }

    @Override
    public void send(Address dst, Object obj) throws Exception {
        send(new Message(dst, null, obj));
    }

    @Override
    public String getViewAsString() {
        final View v = getView();
        return v != null ? v.toString() : "n/a";
    }

    @Override
    public String getAddressAsUUID() {
        return getAddress() instanceof UUID ? ((UUID) getAddress()).toStringLong() : null;
    }

    @Override
    public String getAddressAsString() {
        return getAddress() != null ? getAddress().toString() : "n/a";
    }

    @Override
    public void setDiscardOwnMessages(boolean flag) {
        channel.setDiscardOwnMessages(flag);
    }

    @Override
    public boolean getDiscardOwnMessages() {
        return channel.getDiscardOwnMessages();
    }

    @Override
    public void removeChannelListener(ChannelListener listener) {
        channel.removeChannelListener(listener);
    }

    @Override
    public void addChannelListener(ChannelListener listener) {
        channel.addChannelListener(listener);
    }

    @Override
    public void setReceiver(Receiver r) {
        channel.setReceiver(r);
    }

    @Override
    public Receiver getReceiver() {
        return channel.getReceiver();
    }

    @Override
    public void send(Message msg) throws Exception {
        channel.send(msg);
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public View getView() {
        return channel.getView();
    }

    @Override
    public void getState(Address target, long timeout, boolean useFlushIfPresent) throws Exception {
        channel.getState(target, timeout, useFlushIfPresent);
    }

    public void getState(Address target, long timeout) throws Exception {
        getState(target, timeout, true);
    }

    @Override
    public String getName(Address member) {
        return channel.getName(member);
    }

    @Override
    public String getName() {
        return channel.getName();
    }

    @Override
    public String getClusterName() {
        return channel.getClusterName();
    }

    @Override
    public Address getAddress() {
        return channel.getAddress();
    }

    @Override
    public <T extends Protocol> boolean hasProtocol(Class<T> protocolType) {
        return channel.hasProtocol(protocolType);
    }
}
