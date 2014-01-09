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

import co.paralleluniverse.galaxy.cluster.ReaderWriter;
import com.google.common.base.Throwables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.jgroups.Address;
import org.jgroups.util.Util;

/**
 *
 * @author pron
 */
final class JGroupsConstants {
    public final static String JGROUPS_ADDRESS = "jg_addr";
    
    public final static ReaderWriter<Address> JGROUPS_ADDRESS_READER_WRITER = new ReaderWriter<Address>() {
            @Override
            public Address read(byte[] data) {
                try {
                    return Util.readAddress(new DataInputStream(new ByteArrayInputStream(data)));
                } catch (Exception ex) {
                    throw Throwables.propagate(ex);
                }
            }

            @Override
            public byte[] write(Address value) {
                try {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final DataOutputStream dos = new DataOutputStream(baos);
                    Util.writeAddress((Address) value, dos);
                    dos.flush();
                    return baos.toByteArray();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        };
    
    private JGroupsConstants() {
    }
}
