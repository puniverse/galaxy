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

import co.paralleluniverse.galaxy.cluster.ReaderWriter;
import co.paralleluniverse.galaxy.cluster.ReaderWriters;
import com.google.common.base.Throwables;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 *
 * @author pron
 */
public final class IpConstants {
    public final static String IP_ADDRESS = "ip_addr";
    public final static String IP_COMM_PORT = "ip_port";
    public final static String IP_SERVER_PORT = "ip_server_port";
    public final static String IP_SLAVE_PORT = "ip_slave_port";
    public final static ReaderWriter<InetAddress> INET_ADDRESS_READER_WRITER = new ReaderWriter<InetAddress>() {
        @Override
        public InetAddress read(byte[] data) {
            try {
                final String strAddress = ReaderWriters.STRING.read(data);
                return InetAddress.getByName(strAddress);
            } catch (UnknownHostException ex) {
                throw Throwables.propagate(ex);
            }
        }

        @Override
        public byte[] write(InetAddress value) {
            return ReaderWriters.STRING.write(value.getHostAddress());
        }
    };
    public final static ReaderWriter<InetSocketAddress> INET_SOCKET_ADDRESS_READER_WRITER = new ReaderWriter<InetSocketAddress>() {
        @Override
        public InetSocketAddress read(byte[] data) {
            final String strAddress = ReaderWriters.STRING.read(data);
            final int index = strAddress.lastIndexOf(':');
            final String host = strAddress.substring(0, index);
            final int port = Integer.parseInt(strAddress.substring(index + 1));
            return new InetSocketAddress(host, port);
        }

        @Override
        public byte[] write(InetSocketAddress value) {
            return ReaderWriters.STRING.write(value.toString());
        }
    };

    private IpConstants() {
    }
}
