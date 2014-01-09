/*
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
package co.paralleluniverse.common.collection;

import gnu.trove.TLongCollection;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.procedure.TLongProcedure;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;

/**
 *
 * @author pron
 */
public abstract class TLongAbstractCollection implements TLongCollection {
    public static final TLongCollection EMPTY_COLLECTION = new TLongAbstractCollection() {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public TLongIterator iterator() {
            return new TLongIterator() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public long next() {
                    throw new NoSuchElementException();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    };

    // Query Operations
    @Override
    public abstract int size();

    @Override
    public abstract TLongIterator iterator();

    @Override
    public long getNoEntryValue() {
        return 0L;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns <tt>size() == 0</tt>.
     */
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over the elements in the collection,
     * checking each element in turn for equality with the specified element.
     */
    @Override
    public boolean contains(long entry) {
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (entry == it.next())
                return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation returns an array containing all the elements
     * returned by this collection's iterator, in the same order, stored in
     * consecutive elements of the array, starting with index {@code 0}.
     * The length of the returned array is equal to the number of elements
     * returned by the iterator, even if the size of this collection changes
     * during iteration, as might happen if the collection permits
     * concurrent modification during iteration. The {@code size} method is
     * called only as an optimization hint; the correct result is returned
     * even if the iterator returns a different number of elements.
     */
    @Override
    public long[] toArray() {
        long[] dest = new long[size()];
        final TLongIterator it = iterator();
        for (int i = 0; i < dest.length; i++) {
            if (!it.hasNext()) // fewer elements than expected
                return Arrays.copyOf(dest, i);
            dest[i] = it.next();
        }
        return it.hasNext() ? finishToArray(dest, it) : dest;
    }
    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Reallocates the array being used within toArray when the iterator
     * returned more elements than expected, and finishes filling it from
     * the iterator.
     *
     * @param r the array, replete with previously stored elements
     * @param it the in-progress iterator over this collection
     * @return array containing the elements in the given array, plus any
     * further elements returned by the iterator, trimmed to size
     */
    private static long[] finishToArray(long[] r, TLongIterator it) {
        int i = r.length;
        while (it.hasNext()) {
            int cap = r.length;
            if (i == cap) {
                int newCap = cap + (cap >> 1) + 1;
                // overflow-conscious code
                if (newCap - MAX_ARRAY_SIZE > 0)
                    newCap = hugeCapacity(cap + 1);
                r = Arrays.copyOf(r, newCap);
            }
            r[i++] = it.next();
        }
        // trim if overallocated
        return (i == r.length) ? r : Arrays.copyOf(r, i);
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError("Required array size too large");
        return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation uses the iterator to fill the array according
     * to spec.
     */
    @Override
    public long[] toArray(long[] dest) {
        final TLongIterator it = iterator();
        for (int i = 0; i < dest.length; i++) {
            if (!it.hasNext()) {// fewer elements than expected
                dest[i] = getNoEntryValue();
                break;
            }
            dest[i] = it.next();
        }
        return dest;
    }

    // Modification Operations
    /**
     * {@inheritDoc}
     *
     * <p>This implementation always throws an
     * <tt>UnsupportedOperationException</tt>.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     */
    @Override
    public boolean add(long entry) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over the collection looking for the
     * specified element. If it finds the element, it removes the element
     * from the collection using the iterator's remove method.
     *
     * <p>Note that this implementation throws an
     * <tt>UnsupportedOperationException</tt> if the iterator returned by this
     * collection's iterator method does not implement the <tt>remove</tt>
     * method and this collection contains the specified object.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     */
    @Override
    public boolean remove(long entry) {
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (entry == it.next()) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    // Bulk Operations
    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over the specified collection,
     * checking each element returned by the iterator in turn to see
     * if it's contained in this collection. If all elements are so
     * contained <tt>true</tt> is returned, otherwise <tt>false</tt>.
     *
     * @throws ClassCastException {@inheritDoc}
     * @see #contains(Object)
     */
    @Override
    public boolean containsAll(Collection<?> collection) {
        for (Object e : collection) {
            if (!contains((Long) e))
                return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over the specified collection,
     * checking each element returned by the iterator in turn to see
     * if it's contained in this collection. If all elements are so
     * contained <tt>true</tt> is returned, otherwise <tt>false</tt>.
     *
     * @see #contains(Object)
     */
    @Override
    public boolean containsAll(TLongCollection collection) {
        for (TLongIterator it = collection.iterator(); it.hasNext();) {
            if (!contains(it.next()))
                return false;
        }
        return true;
    }

    @Override
    public boolean containsAll(long[] array) {
        for (long e : array) {
            if (!contains(e))
                return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over the specified collection, and adds
     * each object returned by the iterator to this collection, in turn.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> unless <tt>add</tt> is
     * overridden (assuming the specified collection is non-empty).
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     *
     * @see #add(Object)
     */
    @Override
    public boolean addAll(Collection<? extends Long> collection) {
        boolean modified = false;
        for (Long e : collection) {
            if (add(e))
                modified = true;
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over the specified collection, and adds
     * each object returned by the iterator to this collection, in turn.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> unless <tt>add</tt> is
     * overridden (assuming the specified collection is non-empty).
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     *
     * @see #add(Object)
     */
    @Override
    public boolean addAll(TLongCollection collection) {
        boolean modified = false;
        for (TLongIterator it = collection.iterator(); it.hasNext();) {
            if (add(it.next()))
                modified = true;
        }
        return modified;
    }

    @Override
    public boolean addAll(long[] array) {
        boolean modified = false;
        for (long e : array) {
            if (add(e))
                modified = true;
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over this collection, checking each
     * element returned by the iterator in turn to see if it's contained
     * in the specified collection. If it's so contained, it's removed from
     * this collection with the iterator's <tt>remove</tt> method.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> if the iterator returned by the
     * <tt>iterator</tt> method does not implement the <tt>remove</tt> method
     * and this collection contains one or more elements in common with the
     * specified collection.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     *
     * @see #remove(Object)
     * @see #contains(Object)
     */
    @Override
    public boolean removeAll(Collection<?> collection) {
        boolean modified = false;
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (collection.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over this collection, checking each
     * element returned by the iterator in turn to see if it's contained
     * in the specified collection. If it's so contained, it's removed from
     * this collection with the iterator's <tt>remove</tt> method.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> if the iterator returned by the
     * <tt>iterator</tt> method does not implement the <tt>remove</tt> method
     * and this collection contains one or more elements in common with the
     * specified collection.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     *
     * @see #remove(Object)
     * @see #contains(Object)
     */
    @Override
    public boolean removeAll(TLongCollection collection) {
        boolean modified = false;
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (collection.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean removeAll(long[] array) {
        boolean modified = false;
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (contains(array, it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over this collection, checking each
     * element returned by the iterator in turn to see if it's contained
     * in the specified collection. If it's not so contained, it's removed
     * from this collection with the iterator's <tt>remove</tt> method.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> if the iterator returned by the
     * <tt>iterator</tt> method does not implement the <tt>remove</tt> method
     * and this collection contains one or more elements not present in the
     * specified collection.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     *
     * @see #remove(Object)
     * @see #contains(Object)
     */
    @Override
    public boolean retainAll(Collection<?> collection) {
        boolean modified = false;
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (!collection.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over this collection, checking each
     * element returned by the iterator in turn to see if it's contained
     * in the specified collection. If it's not so contained, it's removed
     * from this collection with the iterator's <tt>remove</tt> method.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> if the iterator returned by the
     * <tt>iterator</tt> method does not implement the <tt>remove</tt> method
     * and this collection contains one or more elements not present in the
     * specified collection.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     *
     * @see #remove(Object)
     * @see #contains(Object)
     */
    @Override
    public boolean retainAll(TLongCollection collection) {
        boolean modified = false;
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (!collection.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(long[] array) {
        boolean modified = false;
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (!contains(array, it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation iterates over this collection, removing each
     * element using the <tt>Iterator.remove</tt> operation. Most
     * implementations will probably choose to override this method for
     * efficiency.
     *
     * <p>Note that this implementation will throw an
     * <tt>UnsupportedOperationException</tt> if the iterator returned by this
     * collection's <tt>iterator</tt> method does not implement the
     * <tt>remove</tt> method and this collection is non-empty.
     *
     * @throws UnsupportedOperationException {@inheritDoc}
     */
    @Override
    public void clear() {
        for (TLongIterator it = iterator(); it.hasNext();) {
            it.next();
            it.remove();
        }
    }

    @Override
    public boolean forEach(TLongProcedure procedure) {
        for (TLongIterator it = iterator(); it.hasNext();) {
            if (!procedure.execute(it.next()))
                return false;
        }
        return true;
    }

    private static boolean contains(long[] array, long value) {
        for (long e : array) {
            if (e == value)
                return true;
        }
        return false;
    }

    //  String conversion
    /**
     * Returns a string representation of this collection. The string
     * representation consists of a list of the collection's elements in the
     * order they are returned by its iterator, enclosed in square brackets
     * (<tt>"[]"</tt>). Adjacent elements are separated by the characters
     * <tt>", "</tt> (comma and space). Elements are converted to strings as
     * by {@link String#valueOf(Object)}.
     *
     * @return a string representation of this collection
     */
    @Override
    public String toString() {
        final TLongIterator it = iterator();
        if (!it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            long e = it.next();
            sb.append(e);
            if (!it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }
}
