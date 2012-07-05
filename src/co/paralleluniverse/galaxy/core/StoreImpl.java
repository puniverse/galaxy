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

import co.paralleluniverse.common.io.Persistable;
import co.paralleluniverse.galaxy.CacheListener;
import co.paralleluniverse.galaxy.ItemState;
import co.paralleluniverse.galaxy.Store;
import co.paralleluniverse.galaxy.StoreTransaction;
import co.paralleluniverse.galaxy.TimeoutException;
import static co.paralleluniverse.galaxy.core.Op.Type.*;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 *
 * @author pron
 */
public class StoreImpl implements Store {
    final Cache cache;
    private final StringRootManager rootManager;

    public StoreImpl(Cache cache) {
        this.cache = cache;
        this.rootManager = new StringRootManager(this, cache.getCluster());
    }

    @Override
    public int getMaxItemSize() {
        return cache.getMaxItemSize();
    }

    @Override
    public StoreTransaction beginTransaction() {
        return cache.beginTransaction();
    }

    @Override
    public void commit(StoreTransaction txn) throws InterruptedException {
        cache.endTransaction((Transaction) txn, false);
    }

    @Override
    public void abort(StoreTransaction txn) {
        try {
            cache.endTransaction((Transaction) txn, true);
        } catch (InterruptedException ex) {
            throw Throwables.propagate(ex);
        }
    }

    @Override
    public void rollback(StoreTransaction txn) {
        cache.rollback((Transaction) txn);
    }

    @Override
    public void release(long id) {
        cache.release(id);
    }

    @Override
    public long getRoot(String root, StoreTransaction txn) throws TimeoutException {
        return rootManager.get(root, (Transaction) verifyNonNull(txn));
    }

    @Override
    public boolean isRootCreated(long rootId, StoreTransaction txn) {
        return ((Transaction) txn).contains(rootId);
    }

    @Override
    public long alloc(int count, StoreTransaction txn) throws TimeoutException {
        return (Long) cache.doOp(ALLOC, -1L, null, count, (Transaction) verifyNonNull(txn));
    }

    @Override
    public long put(byte[] data, StoreTransaction txn) throws TimeoutException {
        return (Long) cache.doOp(PUT, -1L, copyOf(data), null, (Transaction) txn);
    }

    @Override
    public long put(ByteBuffer data, StoreTransaction txn) throws TimeoutException {
        return (Long) cache.doOp(PUT, -1L, data, null, (Transaction) txn);
    }

    @Override
    public long put(Persistable object, StoreTransaction txn) throws TimeoutException {
        return (Long) cache.doOp(PUT, -1L, object, null, (Transaction) txn);
    }

    @Override
    public byte[] get(long id) throws TimeoutException {
        return get(GET, id, null);
    }

    @Override
    public byte[] get(long id, short nodeHint) throws TimeoutException {
        return get(GET, id, nodeHint, null);
    }

    @Override
    public byte[] getFromOwner(long id, long ownerOf) throws TimeoutException {
        return getFromOwner(GET, id, ownerOf, null);
    }

    @Override
    public void get(long id, Persistable object) throws TimeoutException {
        get(GET, id, object, null);
    }

    @Override
    public void get(long id, short nodeHint, Persistable object) throws TimeoutException {
        get(GET, id, nodeHint, object, null);
    }

    @Override
    public void getFromOwner(long id, long ownerOf, Persistable object) throws TimeoutException {
        getFromOwner(GET, id, ownerOf, object, null);
    }

    @Override
    public byte[] gets(long id, StoreTransaction txn) throws TimeoutException {
        return get(GETS, id, txn);
    }

    @Override
    public byte[] gets(long id, short nodeHint, StoreTransaction txn) throws TimeoutException {
        return get(GETS, id, nodeHint, txn);
    }

    @Override
    public byte[] getsFromOwner(long id, long ownerOf, StoreTransaction txn) throws TimeoutException {
        return getFromOwner(GETS, id, ownerOf, txn);
    }

    @Override
    public void gets(long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        get(GETS, id, object, txn);
    }

    @Override
    public void gets(long id, short nodeHint, Persistable object, StoreTransaction txn) throws TimeoutException {
        get(GETS, id, nodeHint, object, txn);
    }

    @Override
    public void getsFromOwner(long id, long ownerOf, Persistable object, StoreTransaction txn) throws TimeoutException {
        getFromOwner(GETS, id, ownerOf, object, txn);
    }

    @Override
    public byte[] getx(long id, StoreTransaction txn) throws TimeoutException {
        return get(GETX, id, txn);
    }

    @Override
    public byte[] getx(long id, short nodeHint, StoreTransaction txn) throws TimeoutException {
        return get(GETX, id, nodeHint, txn);
    }

    @Override
    public byte[] getxFromOwner(long id, long ownerOf, StoreTransaction txn) throws TimeoutException {
        return getFromOwner(GETX, id, ownerOf, txn);
    }

    @Override
    public void getx(long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        get(GETX, id, object, txn);
    }

    @Override
    public void getx(long id, short nodeHint, Persistable object, StoreTransaction txn) throws TimeoutException {
        get(GETX, id, nodeHint, object, txn);
    }

    @Override
    public void getxFromOwner(long id, long ownerOf, Persistable object, StoreTransaction txn) throws TimeoutException {
        getFromOwner(GETX, id, ownerOf, object, txn);
    }

    @Override
    public void set(long id, byte[] data, StoreTransaction txn) throws TimeoutException {
        cache.doOp(SET, nonReserved(id), copyOf(data), null, (Transaction) txn);
    }

    @Override
    public void set(long id, ByteBuffer data, StoreTransaction txn) throws TimeoutException {
        cache.doOp(SET, nonReserved(id), data, null, (Transaction) txn);
    }

    @Override
    public void set(long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(SET, nonReserved(id), object, null, (Transaction) txn);
    }

    @Override
    public void del(long id, StoreTransaction txn) throws TimeoutException {
        cache.doOp(DEL, nonReserved(id), null, null, (Transaction) txn);
    }

    @Override
    public ListenableFuture<byte[]> getAsync(long id) {
        return getAsync(GET, id, null);
    }

    @Override
    public ListenableFuture<byte[]> getAsync(long id, short nodeHint) {
        return getAsync(GET, id, nodeHint, null);
    }

    @Override
    public ListenableFuture<byte[]> getFromOwnerAsync(long id, long ownerOf) {
        return getFromOwnerAsync(GET, id, ownerOf, null);
    }

    @Override
    public ListenableFuture<Persistable> getAsync(long id, Persistable object) {
        return getAsync(GET, id, object, null);
    }

    @Override
    public ListenableFuture<Persistable> getAsync(long id, short nodeHint, Persistable object) {
        return getAsync(GET, id, nodeHint, object, null);
    }

    @Override
    public ListenableFuture<Persistable> getFromOwnerAsync(long id, long ownerOf, Persistable object) {
        return getFromOwnerAsync(GET, id, ownerOf, object, null);
    }

    @Override
    public ListenableFuture<byte[]> getsAsync(long id, StoreTransaction txn) {
        return getAsync(GETS, id, txn);
    }

    @Override
    public ListenableFuture<byte[]> getsAsync(long id, short nodeHint, StoreTransaction txn) {
        return getAsync(GETS, id, nodeHint, txn);
    }

    @Override
    public ListenableFuture<byte[]> getsFromOwnerAsync(long id, long ownerOf, StoreTransaction txn) {
        return getFromOwnerAsync(GETS, id, ownerOf, txn);
    }

    @Override
    public ListenableFuture<Persistable> getsAsync(long id, Persistable object, StoreTransaction txn) {
        return getAsync(GETS, id, object, txn);
    }

    @Override
    public ListenableFuture<Persistable> getsAsync(long id, short nodeHint, Persistable object, StoreTransaction txn) {
        return getAsync(GETS, id, nodeHint, object, txn);
    }

    @Override
    public ListenableFuture<Persistable> getsFromOwnerAsync(long id, long ownerOf, Persistable object, StoreTransaction txn) {
        return getFromOwnerAsync(GETS, id, ownerOf, object, txn);
    }

    @Override
    public ListenableFuture<byte[]> getxAsync(long id, StoreTransaction txn) {
        return getAsync(GETX, id, txn);
    }

    @Override
    public ListenableFuture<byte[]> getxAsync(long id, short nodeHint, StoreTransaction txn) {
        return getAsync(GETX, id, nodeHint, txn);
    }

    @Override
    public ListenableFuture<byte[]> getxFromOwnerAsync(long id, long ownerOf, StoreTransaction txn) {
        return getFromOwnerAsync(GETX, id, ownerOf, txn);
    }

    @Override
    public ListenableFuture<Persistable> getxAsync(long id, Persistable object, StoreTransaction txn) {
        return getAsync(GETX, id, object, txn);
    }

    @Override
    public ListenableFuture<Persistable> getxAsync(long id, short nodeHint, Persistable object, StoreTransaction txn) {
        return getAsync(GETX, id, nodeHint, object, txn);
    }

    @Override
    public ListenableFuture<Persistable> getxFromOwnerAsync(long id, long ownerOf, Persistable object, StoreTransaction txn) {
        return getFromOwnerAsync(GETX, id, ownerOf, object, txn);
    }

    @Override
    public ListenableFuture<Void> setAsync(long id, byte[] data, StoreTransaction txn) {
        return (ListenableFuture<Void>) (Object) cache.doOpAsync(SET, nonReserved(id), copyOf(data), null, (Transaction) txn);
    }

    @Override
    public ListenableFuture<Void> setAsync(long id, ByteBuffer data, StoreTransaction txn) {
        return (ListenableFuture<Void>) (Object) cache.doOpAsync(SET, nonReserved(id), data, null, (Transaction) txn);
    }

    @Override
    public ListenableFuture<Void> setAsync(long id, Persistable object, StoreTransaction txn) {
        return (ListenableFuture<Void>) (Object) cache.doOpAsync(SET, nonReserved(id), object, null, (Transaction) txn);
    }

    @Override
    public void setListener(long id, CacheListener listener) {
        try {
            cache.doOp(LSTN, id, null, listener, null);
        } catch (TimeoutException e) {
            throw new AssertionError();
        }
    }

    @Override
    public void push(long id, short... toNodes) {
        try {
            cache.doOp(PUSH, id, null, toNodes, null);
        } catch (TimeoutException e) {
            // ignore
        }
    }

    @Override
    public void pushx(long id, short toNode) {
        try {
            cache.doOp(PUSHX, id, null, toNode, null);
        } catch (TimeoutException e) {
            // ignore
        }
    }

    @Override
    public boolean isPinned(long id) {
        return cache.isLocked(id);
    }

    @Override
    public ItemState getState(long id) {
        final Cache.State state = cache.getState(id);
        if (state == null)
            return ItemState.INVALID;
        else {
            switch (state) {
                case I:
                    return ItemState.INVALID;
                case S:
                    return ItemState.SHARED;
                case O:
                case E:
                    return ItemState.OWNED;
            }
            throw new AssertionError();
        }
    }

    ///////////////////////////////////////////////////////////////////
    void get1(long id, Persistable object) throws TimeoutException {
        get1(GET, id, object, null);
    }

    void get1(long id, short nodeHint, Persistable object) throws TimeoutException {
        get1(GET, id, nodeHint, object, null);
    }

    void getx1(long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        get1(GETX, id, object, txn);
    }

    void getx1(long id, short nodeHint, Persistable object, StoreTransaction txn) throws TimeoutException {
        get1(GETX, id, nodeHint, object, txn);
    }

    void set1(long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(SET, id, object, null, (Transaction) txn);
    }

    //////////////////////////////////////////////////////////////////
    private static StoreTransaction verifyNonNull(StoreTransaction txn) {
        if (txn == null)
            throw new IllegalArgumentException("Transaction may not be null for this operation.");
        return txn;
    }

    private static byte[] copyOf(byte[] array) {
        return array == null ? null : Arrays.copyOf(array, array.length);
    }

    private byte[] get(Op.Type type, long id, StoreTransaction txn) throws TimeoutException {
        return (byte[]) cache.doOp(type, id, null, null, (Transaction) txn);
    }

    private void get(Op.Type type, long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(type, nonReserved(id), object, null, (Transaction) txn);
    }

    private byte[] get(Op.Type type, long id, short nodeHint, StoreTransaction txn) throws TimeoutException {
        return (byte[]) cache.doOp(type, nonReserved(id), null, nodeHint, (Transaction) txn);
    }

    private void get(Op.Type type, long id, short nodeHint, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(type, nonReserved(id), object, nodeHint, (Transaction) txn);
    }

    private byte[] getFromOwner(Op.Type type, long id, long ownerOf, StoreTransaction txn) throws TimeoutException {
        return (byte[]) cache.doOp(GET_FROM_OWNER, nonReserved(ownerOf), null, new Op(type, nonReserved(id), (Transaction) txn), (Transaction) txn);
    }

    private void getFromOwner(Op.Type type, long id, long ownerOf, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(GET_FROM_OWNER, nonReserved(ownerOf), null, new Op(type, nonReserved(id), object, (Transaction) txn), (Transaction) txn);
    }

    private ListenableFuture<byte[]> getAsync(Op.Type type, long id, StoreTransaction txn) {
        return (ListenableFuture<byte[]>) (Object) cache.doOpAsync(type, id, null, null, (Transaction) txn);
    }

    private ListenableFuture<Persistable> getAsync(Op.Type type, long id, Persistable object, StoreTransaction txn) {
        return (ListenableFuture<Persistable>) (Object) cache.doOpAsync(type, nonReserved(id), object, null, (Transaction) txn);
    }

    private ListenableFuture<byte[]> getAsync(Op.Type type, long id, short nodeHint, StoreTransaction txn) {
        return (ListenableFuture<byte[]>) (Object) cache.doOpAsync(type, nonReserved(id), null, nodeHint, (Transaction) txn);
    }

    private ListenableFuture<Persistable> getAsync(Op.Type type, long id, short nodeHint, Persistable object, StoreTransaction txn) {
        return (ListenableFuture<Persistable>) (Object) cache.doOpAsync(type, nonReserved(id), object, nodeHint, (Transaction) txn);
    }

    private ListenableFuture<byte[]> getFromOwnerAsync(Op.Type type, long id, long ownerOf, StoreTransaction txn) {
        return (ListenableFuture<byte[]>) (Object) cache.doOpAsync(GET_FROM_OWNER, nonReserved(ownerOf), null, new Op(type, nonReserved(id), (Transaction) txn), (Transaction) txn);
    }

    private ListenableFuture<Persistable> getFromOwnerAsync(Op.Type type, long id, long ownerOf, Persistable object, StoreTransaction txn) {
        return (ListenableFuture<Persistable>) (Object) cache.doOpAsync(GET_FROM_OWNER, nonReserved(ownerOf), null, new Op(type, nonReserved(id), object, (Transaction) txn), (Transaction) txn);
    }

    private long nonReserved(long id) {
        if (id <= Cache.MAX_RESERVED_REF_ID)
            throw new IllegalArgumentException("Illegal use of reserved id " + id);
        else
            return id;
    }

    private void get1(Op.Type type, long id, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(type, id, object, null, (Transaction) txn);
    }

    private void get1(Op.Type type, long id, short nodeHint, Persistable object, StoreTransaction txn) throws TimeoutException {
        cache.doOp(type, id, object, nodeHint, (Transaction) txn);
    }
}
