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

import co.paralleluniverse.galaxy.cluster.DistributedTree;
import co.paralleluniverse.galaxy.cluster.DistributedTree.Listener;
import static co.paralleluniverse.galaxy.test.MockitoUtil.*;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 * @author pron
 */
public class MockDistributedTree {
    public final DistributedTree tree;

    public MockDistributedTree() {
        this.tree = mock(DistributedTree.class);
        when(tree.getChildren(any(String.class))).thenAnswer(new Answer<List<String>>() {
            @Override
            public List<String> answer(InvocationOnMock invocation) throws Throwable {
                return new ArrayList<String>();
            }
        });
    }

    public Listener getListener(String path) {
        try {
            return (Listener) capture(tree, "addListener", path, arg(Listener.class));
        } catch (Exception e) {
            return null;
        }
    }
    
    private static final Joiner joiner = Joiner.on('/');

    private String join(String[] pathElements, int length) {
        return joiner.join(Arrays.copyOf(pathElements, length));
    }

    public void add(String... pathElements) {
        final String parentPath = join(pathElements, pathElements.length - 1);
        final String child = pathElements[pathElements.length - 1];

        addToList(parentPath, child);

        final Listener listener = getListener(parentPath);
        if (listener != null)
            listener.nodeChildAdded(parentPath, child);
    }

    public void remove(String... pathElements) {
        final String parentPath = join(pathElements, pathElements.length - 1);
        final String child = pathElements[pathElements.length - 1];

        removeFromList(parentPath, child);

        final Listener listener = getListener(parentPath);
        if (listener != null)
            listener.nodeChildDeleted(parentPath, child);
    }

    public void set(String... args) {
        final String parentPath = join(args, args.length - 2);
        final String child = args[args.length - 2];
        final String value = args[args.length - 1];

        setValue(parentPath + '/' + child, value);

        final Listener listener = getListener(parentPath);
        if (listener != null)
            listener.nodeChildUpdated(parentPath, child);
    }

    private void addToList(String path, String child) {
        List<String> children = tree.getChildren(path);
        if (children == null)
            children = new ArrayList<String>();
        when(tree.getChildren(path)).thenReturn(children);
        children.add(child);
    }

    private void removeFromList(String path, String child) {
        List<String> children = tree.getChildren(path);
        if (children != null)
            children.remove(child);
    }

    public void setValue(String path, String value) {
        when(tree.get(path)).thenReturn(toBuffer(value));
    }

    public void setValue(String path, byte[] value) {
        when(tree.get(path)).thenReturn(value);
    }

    public static String toString(byte[] buffer) {
        return new String(buffer, Charsets.UTF_8);
    }

    public static byte[] toBuffer(String str) {
        return str.getBytes(Charsets.UTF_8);
    }
}
