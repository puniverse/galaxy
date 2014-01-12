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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.io.Persistable;
import co.paralleluniverse.common.util.Enums;
import co.paralleluniverse.galaxy.Grid;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author pron
 */
class Op {
    public enum Type {
        GET,
        INVOKE,
        GETS,
        GETX,
        GET_FROM_OWNER,
        SET,
        PUT,
        ALLOC,
        DEL,
        SEND,
        PUSH,
        PUSHX,
        LSTN;

        public boolean isOf(long set) {
            return Enums.isIn(this, set);
        }
    }
    private static final byte COMPLETED = 1;
    private static final byte CANCELLED = 2;
    public final Type type;
    public final long line;
    public final Transaction txn;
    public final Object data;
    private Object extra;
    private OpFuture<Object> future;
    private long startTime;
    private byte status;

    Op(Type type, long line, Object data, Object extra, Transaction txn) {
        this.type = type;
        this.line = line;
        if (data != null && data instanceof byte[])
            data = Arrays.copyOf(((byte[]) data), ((byte[]) data).length);
        this.data = data;
        this.txn = txn;
        this.extra = extra;
    }

    public Op(Type type, long line, byte[] data, Object extra, Transaction txn) {
        this(type, line, (Object) data, extra, txn);
    }

    public Op(Type type, long line, ByteBuffer data, Object extra, Transaction txn) {
        this(type, line, (Object) data, extra, txn);
    }

    public Op(Type type, long line, Persistable data, Object extra, Transaction txn) {
        this(type, line, (Object) data, extra, txn);
    }

    public Op(Type type, long line, byte[] data, Transaction txn) {
        this(type, line, data, null, txn);
    }

    public Op(Type type, long line, ByteBuffer data, Transaction txn) {
        this(type, line, data, null, txn);
    }

    public Op(Type type, long line, Persistable data, Transaction txn) {
        this(type, line, data, null, txn);
    }

    public Op(Type type, long line, Object extra, Transaction txn) {
        this(type, line, (Object) null, extra, txn);
    }

    public Op(Type type, long line, Transaction txn) {
        this(type, line, (Object) null, null, txn);
    }

    public Object getExtra() {
        return extra;
    }

    public void setExtra(Object extra) {
        this.extra = extra;
    }

    void createFuture() {
        assert future == null;
        this.future = new OpFuture<Object>(this);
    }

    public boolean hasFuture() {
        return future != null;
    }

    public ListenableFuture<Object> getFuture() {
        return future;
    }

    public void setResult(Object result) {
        setCompleted();
        future.set(result);
    }

    public void setException(Throwable t) {
        setCompleted();
        future.setException(t);
    }

    public Object getResult() throws InterruptedException, ExecutionException {
        return future.get();
    }

    public Object getResult(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    void setCancelled() {
        assert status == 0;
        this.status = CANCELLED;
    }

    private void setCompleted() {
        assert status == 0;
        this.status = COMPLETED;
    }

    public boolean isCancelled() {
        return status == CANCELLED;
    }

    public boolean isCompleted() {
        return status == COMPLETED;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Op other = (Op) obj;
        if (this.type != other.type)
            return false;
        if (this.line != other.line)
            return false;
        if (!Objects.equals(this.data, other.data))
            return false;
        if (!Objects.equals(this.extra, other.extra))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + (this.type != null ? this.type.hashCode() : 0);
        hash = 89 * hash + (int) (this.line ^ (this.line >>> 32));
        hash = 89 * hash + Objects.hashCode(this.data);
        hash = 89 * hash + Objects.hashCode(this.extra);
        return hash;
    }

    @Override
    public String toString() {
        return "Op." + type + "(line:" + Long.toHexString(line) + (data != null ? ", data:" + data : "") + (extra != null ? ", extra:" + extra : "") + ')';
    }

    private static final class OpFuture<V> extends AbstractFuture<V> {
        private final Op op;

        OpFuture(Op op) {
            this.op = op;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!getCache().cancelOp(op))
                return false;
            assert op.isCancelled();
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean set(V value) {
            return super.set(value);
        }

        @Override
        public boolean setException(Throwable throwable) {
            return super.setException(throwable);
        }
    }

    static Cache getCache() {
        try {
            return ((StoreImpl) Grid.getInstance().store()).cache;
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }
}