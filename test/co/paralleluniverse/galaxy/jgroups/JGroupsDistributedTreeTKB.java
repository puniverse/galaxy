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

import co.paralleluniverse.galaxy.cluster.DistributedTreeTKB;
import com.google.common.base.Charsets;
import java.io.File;
import org.jgroups.Address;
import org.jgroups.Global;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

/**
 *
 * @author pron
 */
public class JGroupsDistributedTreeTKB {
    public static void main(String[] args) throws Exception {
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.FINER);
        System.setProperty(Global.CUSTOM_LOG_FACTORY, "mesi.jgroups.SLF4JLogFactory");


        final JChannel jchannel = new JChannel(new File("src/udp.xml"));
        jchannel.setReceiver(new ReceiverAdapter() {

            @Override
            public void receive(Message msg) {
                //System.err.println("XXXX " + msg);
            }
            
        });
        ControlChannel control = new ControlChannel(jchannel);

        ReplicatedTree.ConflictResolver conflictResolver = new ReplicatedTree.ConflictResolver() {
            @Override
            public byte[] resolve(String node, byte[] current, byte[] other, Address otherAddress) {
                final String currentString = current != null ? deserialize(current) : null;
                final String otherString = other != null ? deserialize(other) : null;
                System.out.println("CONFLICT IN " + node + ": " + currentString + " <-> " + otherString + " from: " + otherAddress);
                final boolean currentWins = jchannel.getAddress().compareTo(otherAddress) <= 0;
                System.out.println("CONFLICT RESOLVED: " + (currentWins ? "CURRENT " + jchannel.getAddress() + " " + currentString : "OTHER " + otherAddress + " " + otherString));
                return currentWins ? current : other;
            }
        };

        ReplicatedTree tree = new ReplicatedTree(control, conflictResolver, 5000);

        jchannel.connect("test_tree_cluster", null, 10000);
        final Address myAddress = jchannel.getAddress();


        DistributedTreeTKB tkb = new DistributedTreeTKB(new DistributedTreeAdapter(tree), myAddress.toString());
        tkb.run();
    }

    static byte[] serialize(String object) {
        return object.getBytes(Charsets.UTF_8);
    }

    static String deserialize(byte[] array) {
        return new String(array, Charsets.UTF_8);
    }
}
