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

import co.paralleluniverse.galaxy.cluster.DistributedBranchHelper;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import co.paralleluniverse.galaxy.test.MockitoUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.*;
import org.hamcrest.Matcher;
import co.paralleluniverse.galaxy.cluster.DistributedTree.Listener;

import static co.paralleluniverse.galaxy.test.CollectionMatchers.*;
import static org.junit.matchers.JUnitMatchers.*;
import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;
import java.util.Set;
import org.mockito.InOrder;

/**
 *
 * @author pron
 */
public class DistributedBranchHelperTest {
    static final String ROOT = "/r";
    MyLocalTree mdt;
    DistributedBranchHelper branch;
    Listener listener;

    @Before
    public void setUp() {
        reset();
    }

    private void reset() {
        mdt = new MyLocalTree();
        listener = mock(Listener.class);
        branch = null;
    }

    private void createBranch(boolean ordered) {
        final Set<String> requiredProperties = ImmutableSet.of("a", "b");
        branch = new DistributedBranchHelper(mdt.tree, ROOT, ordered) {
            @Override
            protected boolean isNodeComplete(String node, Set<String> properties) {
                return properties.containsAll(requiredProperties);
            }
        };
        branch.addListener(listener); // listener
        branch.init();
    }

    //////////////////////////////////////////////////////////////////////////////
    @Test
    public void test1() {
        createBranch(true);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "a");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "a", "a1");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).nodeChildAdded(ROOT, "child1");
        inOrder.verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child1", "child2")));
    }

    @Test
    public void test2() {
        for (boolean ordered : new boolean[]{false, true}) {
            createBranch(ordered);

            mdt.add(ROOT, "child1");
            mdt.add(ROOT, "child1", "a");
            mdt.add(ROOT, "child1", "b");
            mdt.set(ROOT, "child1", "b", "b1");

            mdt.add(ROOT, "child2");
            mdt.add(ROOT, "child2", "a");
            mdt.add(ROOT, "child2", "b");
            mdt.set(ROOT, "child2", "a", "a2");

            verify(listener, never()).nodeChildAdded(ROOT, "child1");
            verify(listener, never()).nodeChildAdded(ROOT, "child2");
            assertThat(branch.getChildren(), (Matcher) empty());

            reset();
        }
    }

    @Test
    public void test3() {
        createBranch(true);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "a");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), (Matcher) empty());
    }

    @Test
    public void test3_1() {
        createBranch(false);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "a");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), (Matcher) empty());
    }

    @Test
    public void test4() {
        for (boolean ordered : new boolean[]{false, true}) {
            createBranch(ordered);

            mdt.add(ROOT, "child1");
            mdt.add(ROOT, "child1", "a");
            mdt.add(ROOT, "child1", "b");
            mdt.set(ROOT, "child1", "a", "a1");
            mdt.set(ROOT, "child1", "b", "b1");

            mdt.add(ROOT, "child2");
            mdt.add(ROOT, "child2", "a");
            mdt.add(ROOT, "child2", "b");
            mdt.set(ROOT, "child2", "b", "b2");

            verify(listener).nodeChildAdded(ROOT, "child1");
            verify(listener, never()).nodeChildAdded(ROOT, "child2");
            assertThat(branch.getChildren(), equalTo(Arrays.asList("child1")));

            reset();
        }
    }

    @Test
    public void test5() {
        createBranch(true);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "a");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), (Matcher) empty());

        mdt.set(ROOT, "child1", "a", "a1");

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).nodeChildAdded(ROOT, "child1");
        inOrder.verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child1", "child2")));
    }

    @Test
    public void test5_1() {
        createBranch(false);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "a");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");

        mdt.set(ROOT, "child1", "a", "a1");

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).nodeChildAdded(ROOT, "child2");
        inOrder.verify(listener).nodeChildAdded(ROOT, "child1");
    }

    @Test
    public void test6() {
        for (boolean ordered : new boolean[]{false, true}) {

            mdt.add(ROOT, "child1");
            mdt.add(ROOT, "child1", "a");
            mdt.add(ROOT, "child1", "b");
            mdt.set(ROOT, "child1", "a", "a1");
            mdt.set(ROOT, "child1", "b", "b1");

            mdt.add(ROOT, "child2");
            mdt.add(ROOT, "child2", "a");
            mdt.add(ROOT, "child2", "b");
            mdt.set(ROOT, "child2", "a", "a2");
            mdt.set(ROOT, "child2", "b", "b2");

            createBranch(ordered);

            InOrder inOrder = inOrder(listener);
            inOrder.verify(listener).nodeChildAdded(ROOT, "child1");
            inOrder.verify(listener).nodeChildAdded(ROOT, "child2");
            assertThat(branch.getChildren(), equalTo(Arrays.asList("child1", "child2")));

            reset();
        }
    }

    @Test
    public void test7() {
        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        assertThat(mdt.tree.getChildren(ROOT), equalTo(Arrays.asList("child1")));

        createBranch(true);

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), (Matcher) empty());

        mdt.add(ROOT, "child1", "a");
        mdt.set(ROOT, "child1", "a", "a1");

        InOrder inOrder = inOrder(listener);
        inOrder.verify(listener).nodeChildAdded(ROOT, "child1");
        inOrder.verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child1", "child2")));
    }

    @Test
    public void test8() {
        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        assertThat(mdt.tree.getChildren(ROOT), equalTo(Arrays.asList("child1")));

        createBranch(true);

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), (Matcher) empty());

        mdt.remove(ROOT, "child1");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child2")));
    }

    @Test
    public void test9() {
        createBranch(true);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), (Matcher) empty());

        mdt.remove(ROOT, "child1");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child2")));
    }

    @Test
    public void test10() {
        createBranch(true);

        mdt.add(ROOT, "child0");
        mdt.add(ROOT, "child0", "a");
        mdt.add(ROOT, "child0", "b");
        mdt.set(ROOT, "child0", "a", "a0");
        mdt.set(ROOT, "child0", "b", "b0");

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener).nodeChildAdded(ROOT, "child0");
        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0")));

        mdt.remove(ROOT, "child1");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0", "child2")));
    }

    @Test
    public void test11() {
        mdt.add(ROOT, "child0");
        mdt.add(ROOT, "child0", "a");
        mdt.add(ROOT, "child0", "b");
        mdt.set(ROOT, "child0", "a", "a0");
        mdt.set(ROOT, "child0", "b", "b0");

        createBranch(true);

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        verify(listener).nodeChildAdded(ROOT, "child0");
        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0")));

        mdt.remove(ROOT, "child1");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0", "child2")));
    }

    @Test
    public void test12() {
        mdt.add(ROOT, "child0");
        mdt.add(ROOT, "child0", "a");
        mdt.add(ROOT, "child0", "b");
        mdt.set(ROOT, "child0", "a", "a0");
        mdt.set(ROOT, "child0", "b", "b0");

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        mdt.add(ROOT, "child2");
        mdt.add(ROOT, "child2", "a");
        mdt.add(ROOT, "child2", "b");
        mdt.set(ROOT, "child2", "a", "a2");
        mdt.set(ROOT, "child2", "b", "b2");

        createBranch(true);

        verify(listener).nodeChildAdded(ROOT, "child0");
        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener, never()).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0")));

        mdt.remove(ROOT, "child1");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        verify(listener).nodeChildAdded(ROOT, "child2");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0", "child2")));
    }

    @Test
    public void test13() {
        mdt.add(ROOT, "child0");
        mdt.add(ROOT, "child0", "a");
        mdt.add(ROOT, "child0", "b");
        mdt.set(ROOT, "child0", "a", "a0");
        mdt.set(ROOT, "child0", "b", "b0");

        mdt.add(ROOT, "child1");
        mdt.add(ROOT, "child1", "b");
        mdt.set(ROOT, "child1", "b", "b1");

        createBranch(true);

        verify(listener).nodeChildAdded(ROOT, "child0");
        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0")));

        mdt.remove(ROOT, "child1");

        verify(listener, never()).nodeChildAdded(ROOT, "child1");
        assertThat(branch.getChildren(), equalTo(Arrays.asList("child0")));
    }
///////////////////////////////////////////////////////////////////////////////////////////
    static Listener PRINTER = new Listener() {
        @Override
        public void nodeAdded(String node) {
            System.out.println("nodeAdded(" + node + ")");
        }

        @Override
        public void nodeDeleted(String node) {
            System.out.println("nodeRemoved(" + node + ")");
        }

        @Override
        public void nodeUpdated(String node) {
            System.out.println("nodeUpdated(" + node + ")");
        }

        @Override
        public void nodeChildAdded(String node, String child) {
            System.out.println("nodeChildAdded(" + node + ", " + child + ")");
        }

        @Override
        public void nodeChildDeleted(String node, String child) {
            System.out.println("nodeChildRemoved(" + node + ", " + child + ")");
        }

        @Override
        public void nodeChildUpdated(String node, String child) {
            System.out.println("nodeChildUpdated(" + node + ", " + child + ")");
        }
    };

    private static class MyLocalTree {
        private static final String SEP = "/";
        private final LocalTree tree;
        public MyLocalTree() {
            this.tree = new LocalTree();
        }

        private void remove(String ROOT, String child1) {
            tree.delete(ROOT+SEP+child1);
        }

        private void add(String ROOT, String child1) {
            tree.create(ROOT+SEP+child1, false);
        }

        private void add(String ROOT, String child2, String a) {
            tree.create(ROOT+SEP+child2+SEP+a, false);
        }

        private void set(String ROOT, String child0, String a, String a0) {
            tree.set(ROOT+SEP+child0+SEP+a,a0.getBytes());
        }
    }
}
