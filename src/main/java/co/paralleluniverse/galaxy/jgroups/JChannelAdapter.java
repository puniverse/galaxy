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
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.stack.Protocol;
import org.jgroups.util.UUID;

/**
 *
 * @author pron
 */
class JChannelAdapter implements ExtendedChannel {

    public static Channel asChannel(JChannel jchannel) {
        return new JChannelAdapter(jchannel);
    }

    public static ChannelExtender asExtendedChannel(JChannel jchannel) {
        return new ChannelExtender(new JChannelAdapter(jchannel));
    }
    protected final JChannel jchannel;

    public JChannelAdapter(JChannel jchannel) {
        this.jchannel = jchannel;
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
        jchannel.setDiscardOwnMessages(flag);
    }

    @Override
    public boolean getDiscardOwnMessages() {
        return jchannel.getDiscardOwnMessages();
    }

    @Override
    public void removeChannelListener(ChannelListener listener) {
        jchannel.removeChannelListener(listener);
    }

    @Override
    public void addChannelListener(ChannelListener listener) {
        jchannel.addChannelListener(listener);
    }

    @Override
    public void setReceiver(Receiver r) {
        jchannel.setReceiver(r);
    }

    @Override
    public Receiver getReceiver() {
        return jchannel.getReceiver();
    }

    @Override
    public void send(Message msg) throws Exception {
        jchannel.send(msg);
    }

    @Override
    public boolean isOpen() {
        return jchannel.isOpen();
    }

    @Override
    public boolean isConnected() {
        return jchannel.isConnected();
    }

    @Override
    public View getView() {
        return jchannel.getView();
    }

    @Override
    public void getState(Address target, long timeout, boolean useFlushIfPresent) throws Exception {
        jchannel.getState(target, timeout, useFlushIfPresent);
    }

    @Override
    public String getName(Address member) {
        return jchannel.getName(member);
    }

    @Override
    public String getName() {
        return jchannel.getName();
    }

    @Override
    public String getClusterName() {
        return jchannel.getClusterName();
    }

    @Override
    public Address getAddress() {
        return jchannel.getAddress();
    }

    @Override
    public <T extends Protocol> boolean hasProtocol(Class<T> protocolType) {
        return jchannel.getProtocolStack().findProtocol(protocolType) != null;
    }
    
    
}
