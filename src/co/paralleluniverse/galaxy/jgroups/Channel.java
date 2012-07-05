/*
 * Galaxy
 * Copyright (C) 2012 Parallel Universe Software Co.
 * 
 * This file is part of Galaxy.
 *
 * Galaxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * Galaxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with Galaxy. If not, see <http://www.gnu.org/licenses/>.
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
