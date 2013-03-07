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
