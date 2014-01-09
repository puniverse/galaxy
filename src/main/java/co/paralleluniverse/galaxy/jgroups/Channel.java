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

/**
 *
 * @author pron
 */
public interface Channel {

    Address getAddress();

    String getClusterName();

    String getName(Address member);

    String getName();

    void getState(Address target, long timeout, boolean useFlushIfPresent) throws Exception;

    View getView();

    boolean isConnected();

    boolean isOpen();

    void send(Message msg) throws Exception;

    void setReceiver(Receiver r);

    Receiver getReceiver();

    void removeChannelListener(ChannelListener listener);

    void addChannelListener(ChannelListener listener);

    void setDiscardOwnMessages(boolean flag);

    boolean getDiscardOwnMessages();
    
    <T extends Protocol> boolean hasProtocol(Class<T> protocolType);
}
