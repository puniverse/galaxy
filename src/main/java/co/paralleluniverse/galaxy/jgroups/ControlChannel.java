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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.InputStream;
import org.jgroups.Address;
import org.jgroups.Event;
import org.jgroups.Header;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.UpHandler;
import org.jgroups.View;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.stack.StateTransferInfo;
import org.jgroups.util.StateTransferResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class ControlChannel extends JChannelAdapter implements ExtendedChannel, UpHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ControlChannel.class);
    private static final short MAGIC_HEADER = (short) 1199;

    static {
        ClassConfigurator.add(MAGIC_HEADER, ControlHeader.class);
    }
    private static final ControlHeader CONTROL_MARKER = new ControlHeader();
    private Receiver receiver;
    private final boolean dataDiscardOwnMessages;
    private boolean discardOwnMessages;

    /**
     * The JChannel's setDiscardOwnMessages must be called before constructing the control channel (and the value must never change)
     *
     * @param channel
     */
    public ControlChannel(JChannel channel) {
        super(channel);
        this.dataDiscardOwnMessages = channel.getDiscardOwnMessages();
        channel.setDiscardOwnMessages(false);
        channel.setUpHandler(this);
    }

    @Override
    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public Receiver getReceiver() {
        return receiver;
    }

    @Override
    public void setDiscardOwnMessages(boolean flag) {
        this.discardOwnMessages = flag;
    }

    @Override
    public boolean getDiscardOwnMessages() {
        return discardOwnMessages;
    }

    @Override
    public void send(Message msg) throws Exception {
        msg.putHeader(MAGIC_HEADER, CONTROL_MARKER);
        super.send(msg);
    }

    @Override
    public Object up(Event evt) {
        final Receiver dataReceiver = jchannel.getReceiver();
        if (receiver != null) {
            switch (evt.getType()) {
                case Event.MSG:
                    final Message msg = (Message) evt.getArg();
                    LOG.debug("up: " + msg);
                    if (msg.getHeader(MAGIC_HEADER) != null) {
                        LOG.debug("up: to control channel " + msg);
                        if (discardOwnMessages && isOwnMessage(msg))
                            LOG.debug("up: discarded");
                        else
                            receiver.receive(msg);
                    } else if (dataReceiver != null) {
                        LOG.debug("up: to data channel " + msg);
                        if (dataDiscardOwnMessages && isOwnMessage(msg))
                            LOG.debug("up: discarded");
                        else
                            dataReceiver.receive(msg);
                    }
                    break;

                case Event.VIEW_CHANGE:
                    receiver.viewAccepted((View) evt.getArg());
                    break;

                case Event.SUSPECT:
                    receiver.suspect((Address) evt.getArg());
                    break;

                case Event.GET_STATE_OK:
                    final StateTransferResult result = (StateTransferResult) evt.getArg();
                    final byte[] state = result.getBuffer();
                    if (state != null) {
                        final ByteArrayInputStream input = new ByteArrayInputStream(state);
                        try {
                            receiver.setState(input);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                    break;

                case Event.STATE_TRANSFER_INPUTSTREAM:
                    final InputStream is = (InputStream) evt.getArg();
                    if (is != null) {
                        try {
                            receiver.setState(is);
                        } catch (Throwable t) {
                            LOG.error("Error while setting state", t);
                            throw new RuntimeException("failed calling setState() in state requester", t);
                        }
                    }
                    break;

                case Event.GET_APPLSTATE:
                    byte[] tmpState = null;
                    if (receiver != null) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                        try {
                            receiver.getState(output);
                            tmpState = output.toByteArray();
                        } catch (Exception e) {
                            LOG.error("Error while getting state", e);
                            throw new RuntimeException(getAddress() + ": failed getting state from application", e);
                        }
                    }
                    return new StateTransferInfo(null, 0L, tmpState);

                case Event.BLOCK:
                    receiver.block();
                    if (dataReceiver != null)
                        dataReceiver.block();
                    return true;

                case Event.UNBLOCK:
                    receiver.unblock();
                    if (dataReceiver != null)
                        dataReceiver.unblock();
                    break;
            }
        }

        return null;
    }

    private boolean isOwnMessage(Message msg) {
        return getAddress() != null && msg.getSrc() != null && getAddress().equals(msg.getSrc());
    }

    public static class ControlHeader extends Header {
        public ControlHeader() {
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public void writeTo(DataOutput out) throws Exception {
        }

        @Override
        public void readFrom(DataInput in) throws Exception {
        }
    }
}
