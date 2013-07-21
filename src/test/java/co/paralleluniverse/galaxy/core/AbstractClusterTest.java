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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.galaxy.cluster.SlaveConfigurationListener;
import co.paralleluniverse.galaxy.cluster.DistributedTree;
import co.paralleluniverse.galaxy.cluster.NodeInfo;
import co.paralleluniverse.galaxy.cluster.LifecycleListener;
import co.paralleluniverse.galaxy.cluster.ReaderWriters;
import co.paralleluniverse.galaxy.cluster.NodeChangeListener;
import com.google.common.base.Charsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import co.paralleluniverse.galaxy.core.AbstractCluster.NodeInfoImpl;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static org.mockito.Matchers.any;
import static co.paralleluniverse.galaxy.test.LogMock.startLogging;
import static co.paralleluniverse.galaxy.test.LogMock.stopLogging;
import static co.paralleluniverse.galaxy.test.LogMock.when;
import static co.paralleluniverse.galaxy.test.LogMock.doAnswer;
import static co.paralleluniverse.galaxy.test.LogMock.doNothing;
import static co.paralleluniverse.galaxy.test.LogMock.doReturn;
import static co.paralleluniverse.galaxy.test.LogMock.doThrow;
import static co.paralleluniverse.galaxy.test.LogMock.mock;
import static co.paralleluniverse.galaxy.test.LogMock.spy;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;

import static co.paralleluniverse.galaxy.core.NodeInfoMatchers.*;
import co.paralleluniverse.galaxy.cluster.NodePropertyListener;

/**
 *
 * @author pron
 */
public class AbstractClusterTest {
    static final String ROOT = "/co.paralleluniverse.galaxy";
    static final String MY_NAME = "mememe";
    static final short MY_ID = (short) 100;
    AbstractCluster cluster;
    DistributedTree tree;
    LifecycleListener lifecycleListener;
    NodeChangeListener nodeListener;
    SlaveConfigurationListener slaveListener;

    @Before
    public void setUp() {
        tree = new LocalTree(); // new MockDistributedTree();
        cluster = new AbstractCluster("cluster", MY_ID) {
            @Override
            protected boolean isMe(NodeInfoImpl node) {
                return node.getName().equals(MY_NAME);
            }

            @Override
            public boolean hasServer() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public Object getUnderlyingResource() {
                return null;
            }
        };
        cluster.setName(MY_NAME);
        cluster.setControlTree(tree);

        lifecycleListener = mock(LifecycleListener.class);
        cluster.addLifecycleListener(lifecycleListener);

        nodeListener = mock(NodeChangeListener.class);
        cluster.addNodeChangeListener(nodeListener);

        slaveListener = mock(SlaveConfigurationListener.class);
        cluster.addSlaveConfigurationListener(slaveListener);
    }

    @After
    public void tearDown() {
    }

    ///////////////////////////////////////////////////////////////////////
    @Test
    public void testSetRequiredPropery() throws Exception {
        tree = spy(tree);
        cluster.setControlTree(tree);

        cluster.addNodeProperty("foo", true, true, ReaderWriters.STRING);
        cluster.addNodeProperty("bar", true, true, ReaderWriters.INTEGER);

        cluster.setNodeProperty("foo", "1.2.3.4");
        cluster.setNodeProperty("bar", 500);

        cluster.postInit();

        assertThat((String) cluster.getMyNodeInfo().get("foo"), is("1.2.3.4"));
        assertThat((Integer) cluster.getMyNodeInfo().get("bar"), is(500));

        verify(tree).create(ROOT + "/nodes", false);
        verify(tree).create(ROOT + "/nodes/" + MY_NAME, true);
        verify(tree).create(ROOT + "/nodes/" + MY_NAME + "/id", true);
        verify(tree).set(ROOT + "/nodes/" + MY_NAME + "/id", Short.toString(MY_ID).getBytes(Charsets.UTF_8));
        verify(tree).create(ROOT + "/nodes/" + MY_NAME + "/foo", true);
        verify(tree).set(ROOT + "/nodes/" + MY_NAME + "/foo", "1.2.3.4".getBytes(Charsets.UTF_8));
        verify(tree).create(ROOT + "/nodes/" + MY_NAME + "/bar", true);
        verify(tree).set(ROOT + "/nodes/" + MY_NAME + "/bar", "500".getBytes(Charsets.UTF_8));
        verify(tree).flush();
    }

    @Test
    public void whenMissingRequiredPropertiesThenFailInit() throws Exception {
        cluster.addNodeProperty("foo", true, true, ReaderWriters.STRING);
        cluster.addNodeProperty("bar", true, true, ReaderWriters.INTEGER);

        cluster.setNodeProperty("foo", "1.2.3.4");
        //cluster.setProperty("bar", 500);

        try {
            cluster.postInit();
            fail("Exception not thrown");
        } catch (RuntimeException e) {
        }
        verify(lifecycleListener, never()).online(anyBoolean());
    }

    @Test
    public void whenDuplicateNameThenFailInit() {
        addNode("mememe", MY_ID);

        try {
            init();
            fail("Exception not thrown");
        } catch (RuntimeException e) {
        }
        verify(lifecycleListener, never()).online(anyBoolean());
    }

    @Test
    public void whenFirstOnlineThenMaster() {
        init();
        cluster.goOnline();
        
        verify(lifecycleListener).online(true);
        assertThat(cluster.getMyMaster(), is(nullValue()));
    }

    @Test
    public void whenNotFirstOnlineThenSlave() {
        addNode("master1", MY_ID);
        init();
        cluster.goOnline();

        verify(lifecycleListener).online(false);
        assertThat(cluster.getMyMaster().getName(), is("master1"));
    }

    @Test
    public void testNodes() {
        addNode("node1", 10);

        init();
        cluster.goOnline();
        
        addNode("node2", 20);
        addNode("node3", 30);

        verify(nodeListener).nodeAdded(sh(10));
        verify(nodeListener).nodeAdded(sh(20));
        verify(nodeListener).nodeAdded(sh(30));
        verifyNoMoreInteractions(nodeListener);

        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node2", "node3")));

        removeNode("node2");

        verify(nodeListener).nodeRemoved(sh(20));
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node3")));
    }

    // @Test - removed from tests when started using the LEADERS branch
    public void testRecoverFromNodeThatFailsToInit1() {
        addBadNode("node1", 10); // now following node declarations will be stuck in DistributedBranchHelper until this is fixed or deleted

        init();

        addNode("node2", 20);
        addNode("node3", 30);

        verify(lifecycleListener, never()).online(anyBoolean());
        verifyNoMoreInteractions(nodeListener);

        removeNode("node1");

        verify(lifecycleListener).online(true);

        verify(nodeListener).nodeAdded(sh(20));
        verify(nodeListener).nodeAdded(sh(30));
        verifyNoMoreInteractions(nodeListener);

        assertThat(set(names(cluster.getMasters())), equalTo(set("node2", "node3")));
    }

    // @Test - removed from tests when started using the LEADERS branch
    public void testRecoverFromNodeThatFailsToInit2() {
        addNode("node1", 10);
        addBadNode("node2", 20); // now following node declarations will be stuck in DistributedBranchHelper until this is fixed or deleted

        init();

        addNode("node3", 30);

        verify(lifecycleListener, never()).online(anyBoolean());
        verifyNoMoreInteractions(nodeListener);

        removeNode("node2");

        verify(lifecycleListener).online(true);

        verify(nodeListener).nodeAdded(sh(10));
        verify(nodeListener).nodeAdded(sh(30));
        verifyNoMoreInteractions(nodeListener);

        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node3")));
    }

    // @Test - removed from tests when started using the LEADERS branch
    public void testRecoverFromNodeThatFailsToInit3() {
        addNode("node1", 10);

        init();

        addBadNode("node2", 20); // now following node declarations will be stuck in DistributedBranchHelper until this is fixed or deleted
        addNode("node3", 30);

        verify(lifecycleListener).online(true);

        verify(nodeListener).nodeAdded(sh(10));
        verifyNoMoreInteractions(nodeListener);

        removeNode("node2");

        verify(lifecycleListener).online(true);

        verify(nodeListener).nodeAdded(sh(30));
        verifyNoMoreInteractions(nodeListener);

        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node3")));
    }

    @Test
    public void testMySlaves() {
        init();
        cluster.goOnline();
        
        addNode("slave1", MY_ID);
        addNode("slave2", MY_ID);
        addNode("slave3", MY_ID);
        addNode("nonslave1", 11);
        addNode("nonslave2", 12);

        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "slave1")));
        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "slave2")));
        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "slave3")));
        verifyNoMoreInteractions(slaveListener);

        assertThat(names(cluster.getMySlaves()), equalTo(list("slave1", "slave2", "slave3")));

        removeNode("slave2");

        verify(slaveListener).slaveRemoved(argThat(withPropertyEqualTo("name", "slave2")));
        assertThat(names(cluster.getMySlaves()), equalTo(list("slave1", "slave3")));
    }

    @Test
    public void testOtherNodeSwitchover() {
        addNode("node1", 10);
        addNode("node1_slave1", 10);
        addNode("node1_slave2", 10);

        init();
        cluster.goOnline();
        
        addNode("node2", 20);
        addNode("node2_slave1", 20);
        addNode("node2_slave2", 20);
        addNode("node3", 30);
        addNode("node3_slave1", 30);
        addNode("node3_slave2", 30);

        verify(nodeListener).nodeAdded(sh(10));
        verify(nodeListener).nodeAdded(sh(20));
        verify(nodeListener).nodeAdded(sh(30));
        verifyNoMoreInteractions(nodeListener);

        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node2", "node3")));

        removeNode("node2_slave1");

        verifyNoMoreInteractions(nodeListener);

        removeNode("node2_slave2");

        verifyNoMoreInteractions(nodeListener);

        removeNode("node2");

        verify(nodeListener).nodeRemoved(sh(20));
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node3")));

        removeNode("node3");

        verify(nodeListener).nodeSwitched(sh(30));
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node3_slave1")));

        removeNode("node3_slave1");

        verify(nodeListener, times(2)).nodeSwitched(sh(30));
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node3_slave2")));

        removeNode("node3_slave2");

        verify(nodeListener).nodeRemoved(sh(30));
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1")));

        removeNode("node1_slave1");

        verifyNoMoreInteractions(nodeListener);
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1")));

        removeNode("node1");

        verify(nodeListener).nodeSwitched(sh(10));
        assertThat(set(names(cluster.getMasters())), equalTo(set("node1_slave2")));

        removeNode("node1_slave2");

        verify(nodeListener).nodeRemoved(sh(10));
        assertThat(set(names(cluster.getMasters())), equalTo(Collections.EMPTY_SET));
    }

//    @Test Second slave is not supported yet
    public void testSecondSlaveSwitchover() {
        addNode("n1", MY_ID);
        addNode("n2", MY_ID);
        addNode("n3", MY_ID);

        init();
        cluster.goOnline();
        
        addNode("n4", MY_ID);
        addNode("n5", MY_ID);
        addNode("n6", MY_ID);

        verify(lifecycleListener).online(false);
        assertThat(cluster.getMyMaster().getName(), is("n1"));
        assertThat(names(cluster.getMySlaves()), equalTo(Collections.EMPTY_LIST));

        removeNode("n2");

        verifyNoMoreInteractions(slaveListener);

        removeNode("n1");
        verify(slaveListener).newMaster(argThat(withPropertyEqualTo("name", "n3")));
        assertThat(cluster.getMyMaster().getName(), is("n3"));
        assertThat(names(cluster.getMySlaves()), equalTo(Collections.EMPTY_LIST));

        removeNode("n3");

        verify(lifecycleListener).switchToMaster();
        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "n4")));
        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "n5")));
        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "n6")));
        assertThat(names(cluster.getMySlaves()), equalTo(list("n4", "n5", "n6")));
    }

    @Test
    public void testSwitchover() {
        addNode("n1", MY_ID);
        init();
        cluster.goOnline();
        
        verify(lifecycleListener).online(false);
        assertThat(cluster.getMyMaster().getName(), is("n1"));
        assertThat(names(cluster.getMySlaves()), equalTo(Collections.EMPTY_LIST));

        removeNode("n1");
        addNode("n4", MY_ID);
        verify(lifecycleListener).switchToMaster();
        verify(slaveListener).slaveAdded(argThat(withPropertyEqualTo("name", "n4")));
        assertThat(names(cluster.getMySlaves()), equalTo(list("n4")));
    }

    @Test
    public void testSetNodeProperty() {
        tree = spy(tree);
        cluster.setControlTree(tree);

        init();

        cluster.addNodeProperty("why?", false, false, ReaderWriters.STRING);
        cluster.addNodeProperty("howmuch?", false, false, ReaderWriters.INTEGER);

        cluster.setNodeProperty("why?", "because!");
        cluster.setNodeProperty("howmuch?", 20);

        assertThat((String) cluster.getMyNodeInfo().get("why?"), is("because!"));
        assertThat((Integer) cluster.getMyNodeInfo().get("howmuch?"), is(20));

        verify(tree).create(ROOT + "/nodes/" + MY_NAME + "/why?", true);
        verify(tree).set(ROOT + "/nodes/" + MY_NAME + "/why?", "because!".getBytes(Charsets.UTF_8));
        verify(tree).create(ROOT + "/nodes/" + MY_NAME + "/howmuch?", true);
        verify(tree).set(ROOT + "/nodes/" + MY_NAME + "/howmuch?", "20".getBytes(Charsets.UTF_8));
    }

    @Test
    public void testNodePropertyListeners() {
        init();
        cluster.goOnline();
        
        addNode("node1", 10);
        addNode("node2", 20);

        addNode("slave1", MY_ID);
        addNode("slave2", MY_ID);

        assertThat(set(names(cluster.getMasters())), equalTo(set("node1", "node2")));
        assertThat(names(cluster.getMySlaves()), equalTo(list("slave1", "slave2")));

        cluster.addNodeProperty("why?", false, false, ReaderWriters.STRING);
        cluster.addNodeProperty("howmuch?", false, false, ReaderWriters.INTEGER);

        NodePropertyListener listener1 = mock(NodePropertyListener.class);
        NodePropertyListener listener2 = mock(NodePropertyListener.class);

        tree.create(ROOT + "/nodes/node1/why?", true);
        tree.create(ROOT + "/nodes/node1/howmuch?", true);

        tree.create(ROOT + "/nodes/slave1/why?", true);
        tree.create(ROOT + "/nodes/slave1/howmuch?", true);

        cluster.addMasterNodePropertyListener("why?", listener1);
        cluster.addSlaveNodePropertyListener("howmuch?", listener2);

        tree.set(ROOT + "/nodes/node1/why?", "because node1!".getBytes(Charsets.UTF_8));
        tree.set(ROOT + "/nodes/node1/howmuch?", "2001".getBytes(Charsets.UTF_8));

        tree.set(ROOT + "/nodes/slave1/why?", "because slave1!".getBytes(Charsets.UTF_8));
        tree.set(ROOT + "/nodes/slave1/howmuch?", "2011".getBytes(Charsets.UTF_8));

        verify(listener1).propertyChanged(argThat(withPropertyEqualTo("name", "node1")), eq("why?"), eq("because node1!"));
        verifyNoMoreInteractions(listener1);
        verify(listener2).propertyChanged(argThat(withPropertyEqualTo("name", "slave1")), eq("howmuch?"), eq(2011));
        verifyNoMoreInteractions(listener2);

        tree.create(ROOT + "/nodes/node2/why?", true);
        tree.set(ROOT + "/nodes/node2/why?", "because node2!".getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/nodes/node2/howmuch?", true);
        tree.set(ROOT + "/nodes/node2/howmuch?", "2002".getBytes(Charsets.UTF_8));

        tree.create(ROOT + "/nodes/slave2/why?", true);
        tree.set(ROOT + "/nodes/slave2/why?", "because slave2!".getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/nodes/slave2/howmuch?", true);
        tree.set(ROOT + "/nodes/slave2/howmuch?", "2012".getBytes(Charsets.UTF_8));

        verify(listener1).propertyChanged(argThat(withPropertyEqualTo("name", "node2")), eq("why?"), argThat(is(nullValue())));
        verify(listener1).propertyChanged(argThat(withPropertyEqualTo("name", "node2")), eq("why?"), eq("because node2!"));
        verifyNoMoreInteractions(listener1);
        verify(listener2).propertyChanged(argThat(withPropertyEqualTo("name", "slave2")), eq("howmuch?"), argThat(is(nullValue())));
        verify(listener2).propertyChanged(argThat(withPropertyEqualTo("name", "slave2")), eq("howmuch?"), eq(2012));
        verifyNoMoreInteractions(listener2);

        tree.delete(ROOT + "/nodes/node1/why?");
        tree.delete(ROOT + "/nodes/node1/howmuch?");
        tree.delete(ROOT + "/nodes/slave1/why?");
        tree.delete(ROOT + "/nodes/slave1/howmuch?");

        verify(listener1).propertyChanged(argThat(withPropertyEqualTo("name", "node1")), eq("why?"), argThat(is(nullValue())));
        verifyNoMoreInteractions(listener1);
        verify(listener2).propertyChanged(argThat(withPropertyEqualTo("name", "slave1")), eq("howmuch?"), argThat(is(nullValue())));
        verifyNoMoreInteractions(listener2);
    }

    /////////////////////////////////////////////////////////////////////////////////
    void init() {
        try {
            cluster.addNodeProperty("foo", true, true, ReaderWriters.STRING);
            cluster.addNodeProperty("bar", true, true, ReaderWriters.INTEGER);

            cluster.setNodeProperty("foo", "1.2.3.4");
            cluster.setNodeProperty("bar", 500);

            cluster.postInit();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void addNode(String name, int id) {
        addNode(name, id, "-" + name + "-", name.hashCode());
    }

    void addNode(String name, int id, String foo, int bar) {
        tree.create(ROOT + "/nodes/" + name, true);
        tree.create(ROOT + "/nodes/" + name + "/id", true);
        tree.set(ROOT + "/nodes/" + name + "/id", Short.toString((short) id).getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/nodes/" + name + "/foo", true);
        tree.set(ROOT + "/nodes/" + name + "/foo", foo.getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/nodes/" + name + "/bar", true);
        tree.set(ROOT + "/nodes/" + name + "/bar", Integer.toString(bar).getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/leaders/" + name + "/bar", true);
    }

    void addBadNode(String name, int id) {
        int bar = 666;
        tree.create(ROOT + "/nodes/" + name, true);
        tree.create(ROOT + "/nodes/" + name + "/id", true);
        tree.set(ROOT + "/nodes/" + name + "/id", Short.toString((short) id).getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/nodes/" + name + "/bar", true);
        tree.set(ROOT + "/nodes/" + name + "/bar", Integer.toString(bar).getBytes(Charsets.UTF_8));
        tree.create(ROOT + "/leaders/" + name, true);
    }

    void removeNode(String name) {
        tree.delete(ROOT + "/leaders/" + name);
        tree.delete(ROOT + "/nodes/" + name);
    }

    static short sh(int x) {
        return (short) x;
    }

    static short[] sh(int... args) {
        final short[] array = new short[args.length];
        for (int i = 0; i < args.length; i++)
            array[i] = (short) args[i];
        return array;
    }

    static List<String> names(Collection<NodeInfo> nis) {
        List<String> names = new ArrayList<String>(nis.size());
        for (NodeInfo ni : nis)
            names.add(ni.getName());
        return names;
    }

    static List<Short> ids(Collection<NodeInfo> nis) {
        List<Short> ids = new ArrayList<Short>(nis.size());
        for (NodeInfo ni : nis)
            ids.add(ni.getNodeId());
        return ids;
    }

    static <T> Set<T> set(T... es) {
        return new HashSet<T>(Arrays.asList(es));
    }

    static <T> Set<T> set(Collection<T> es) {
        return new HashSet<T>(es);
    }

    static <T> List<T> list(T... es) {
        return Arrays.asList(es);
    }

    static void pending() {
        fail("Test pending");
    }
}
