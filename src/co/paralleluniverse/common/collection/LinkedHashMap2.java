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
package co.paralleluniverse.common.collection;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds navigation methods (next, previous etc.) to {@link LinkedHashMap}.
 * @author pron
 */
public class LinkedHashMap2<K, V> extends LinkedHashMap<K, V> {
    private static final Class entryClass;
    private static final Field header;
    private static final Method getEntry;
    private static final Field before;
    private static final Field after;

    static {
        try {
            getEntry = HashMap.class.getDeclaredMethod("getEntry", Object.class);
            
            header = LinkedHashMap.class.getDeclaredField("header");
            
            Class<?>[] declared = LinkedHashMap.class.getDeclaredClasses();
            Class<?> tmpEntryClass = null;
            for (Class<?> cls : declared) {
                if (cls.getName().endsWith("$Entry")) {
                    tmpEntryClass = cls;
                    break;
                }
            }
            assert tmpEntryClass != null;
            entryClass = tmpEntryClass;

            before = entryClass.getDeclaredField("before");
            after = entryClass.getDeclaredField("after");
            
            AccessibleObject.setAccessible(new AccessibleObject[]{getEntry, header, before, after}, true);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    
    public LinkedHashMap2(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public LinkedHashMap2(int initialCapacity) {
        super(initialCapacity);
    }

    public LinkedHashMap2() {
    }

    public LinkedHashMap2(Map<? extends K, ? extends V> m) {
        super(m);
    }

    public Map.Entry<K, V> firstEntry() {
        final Map.Entry<K, V> header = getHeader();
        final Map.Entry<K, V> first = getNextEntry(header);
        return (first != header ? first : null);
    }

    public Map.Entry<K, V> lastEntry() {
        final Map.Entry<K, V> header = getHeader();
        final Map.Entry<K, V> last = getPreviousEntry(header);
        return (last != header ? last : null);
    }

    public Map.Entry<K, V> nextEntry(K key) {
        final Map.Entry<K, V> nextEntry = getNextEntry(getEntry(key));
        return (nextEntry != getHeader() ? nextEntry : null);
    }

    public Map.Entry<K, V> previousEntry(K key) {
        final Map.Entry<K, V> previousEntry = getPreviousEntry(getEntry(key));
        return (previousEntry != getHeader() ? previousEntry : null);
    }

    public K firstKey() {
        return getKey(getNextEntry(getHeader()));
    }

    public K lastKey() {
        return getKey(getPreviousEntry(getHeader()));
    }

    public K nextKey(K key) {
        return getKey(getNextEntry(getEntry(key)));
    }

    public K previousKey(K key) {
        return getKey(getPreviousEntry(getEntry(key)));
    }

    public V nextValue(K key) {
        return getValue(getNextEntry(getEntry(key)));
    }

    public V previousValue(K key) {
        return getValue(getPreviousEntry(getEntry(key)));
    }

    private K getKey(Map.Entry<K, V> entry) {
        return entry != null ? entry.getKey() : null;
    }

    private V getValue(Map.Entry<K, V> entry) {
        return entry != null ? entry.getValue() : null;
    }

    protected final Map.Entry<K, V> getEntry(K key) {
        try {
            return (Map.Entry<K, V>) getEntry.invoke(this, key);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError(ex);
        } catch (InvocationTargetException ex) {
            throw new AssertionError(ex);
        }
    }

    protected final Map.Entry<K, V> getHeader() {
        try {
            return (Map.Entry<K, V>) header.get(this);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError(ex);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    protected final Map.Entry<K, V> getNextEntry(Map.Entry<K, V> entry) {
        if(entry == null)
            return null;
        try {
            return (Map.Entry<K, V>) after.get(entry);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError(ex);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }

    protected final Map.Entry<K, V> getPreviousEntry(Map.Entry<K, V> entry) {
        if(entry == null)
            return null;
        try {
            return (Map.Entry<K, V>) before.get(entry);
        } catch (IllegalArgumentException ex) {
            throw new AssertionError(ex);
        } catch (IllegalAccessException ex) {
            throw new AssertionError(ex);
        }
    }
}
