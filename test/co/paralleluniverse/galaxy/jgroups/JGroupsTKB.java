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

import co.paralleluniverse.galaxy.jgroups.ControlChannel;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import co.paralleluniverse.galaxy.Cluster;

import org.jgroups.Address;
import org.jgroups.Global;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.ReceiverAdapter;
import static org.hamcrest.CoreMatchers.*;
import org.jgroups.View;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class JGroupsTKB {

    final static int PORT = 9988;
    final static SocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", PORT);
    private static volatile boolean clustered = false;
    private static final Object lock = new Object();

    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINER);
        System.setProperty(Global.CUSTOM_LOG_FACTORY, "mesi.jgroups.SLF4JLogFactory");
        //System.setProperty(org.jgroups.Global.USE_JDK_LOGGER, "true");

        final Logger log = LoggerFactory.getLogger(JGroupsTKB.class);

        Cluster cluster = mock(Cluster.class);
        //when(cluster.getNodeId(ADDRESS)).thenReturn((short) 1);
        //when(cluster.getSocketAddress((short) 1)).thenReturn(ADDRESS);

        JChannel jchannel = new JChannel();
        jchannel.setDiscardOwnMessages(true);

        ControlChannel control = new ControlChannel(jchannel);
        control.setDiscardOwnMessages(true);

        jchannel.setReceiver(new ReceiverAdapter() {

            @Override
            public void receive(Message msg) {
                log.info("received: {} headers: {} content: {}", new Object[]{msg, msg.getHeaders(), msg.getObject()});
            }
        });

        control.setReceiver(new ReceiverAdapter() {

            @Override
            public void receive(Message msg) {
                log.info("CONTROL received: {} headers: {} content: {}", new Object[]{msg, msg.getHeaders(), msg.getObject()});
            }

            @Override
            public void viewAccepted(View newView) {
                log.info("CONTROL: view: {}", newView);
                synchronized (lock) {
                    clustered = newView.size() > 1;
                    lock.notifyAll();
                }
            }

            @Override
            public void getState(OutputStream output) throws Exception {
                log.info("CONTROL: getState()");
            }

            @Override
            public void setState(InputStream input) throws Exception {
                log.info("CONTROL: setState()");
            }
        });

        log.info("connecting...");
        jchannel.connect("test_cluster");
        log.info("connecetd");

        synchronized (lock) {
            while (!clustered)
                lock.wait();
        }
        log.info("sending data message1");
        jchannel.send(message("message1"));
        log.info("sending control cmessage1");
        control.send(message("cmessage1"));
        log.info("sending data message2");
        jchannel.send(message("message2"));
        log.info("sending control cmessage2");
        control.send(message("cmessage2"));

//        Thread.sleep(10000);
//        jchannel.close();
    }

    private static Message message(Object payload) {
        Message msg = new Message(null, payload);
        msg.setFlag(Message.DONT_BUNDLE);
        return msg;
    }
}
