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

import co.paralleluniverse.common.io.Persistables;
import co.paralleluniverse.common.io.Streamable;
import co.paralleluniverse.common.io.Streamables;
import co.paralleluniverse.common.util.Enums;
import co.paralleluniverse.galaxy.LineFunction;
import co.paralleluniverse.io.serialization.Serialization;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class Message implements Streamable, Externalizable, Cloneable {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Message.class);

    public static enum Type {
        GET, GETX, INV, INVACK, PUT, PUTX, DEL, CHNGD_OWNR, NOT_FOUND, TIMEOUT,
        INVOKE, INVRES,
        BACKUP, BACKUPACK,
        BACKUP_PACKET, BACKUP_PACKETACK,
        ALLOC_REF, ALLOCED_REF,
        MSG, MSGACK,
        ACK;
        // INVACK can be sent w/o an INV (e.g. eviction). replied by ack. INVACKs don't timeout.
        // ACK never flows back to Cache (handled by Comm), but INVACK does

        public boolean isOf(long set) {
            return Enums.isIn(this, set);
        }
        public final static long REQUIRES_RESPONSE = Enums.setOf(GET, GETX, INV, BACKUP_PACKET, INVOKE);
    }

    public static INVOKE INVOKE(short node, long line, LineFunction data) {
        return new INVOKE(Type.INVOKE, node, line, data);
    }

    public static INVRES INVRES(LineMessage responseTo, long line, Object result) {
        return new INVRES(responseTo, line, result);
    }

    public static GET GET(short node, long line) {
        return new GET(Type.GET, node, line);
    }

    public static GET GETX(short node, long line) {
        return new GET(Type.GETX, node, line);
    }

    public static PUT PUT(LineMessage responseTo, long line, long version, ByteBuffer data) {
        return new PUT(responseTo, line, version, data);
    }

    public static PUT PUT(short node, long line, long version, ByteBuffer data) {
        return new PUT(node, line, version, data);
    }

    public static PUT PUT(short[] nodes, long line, long version, ByteBuffer data) {
        return new PUT(nodes, line, version, data);
    }

    public static PUTX PUTX(LineMessage responseTo, long line, short[] sharers, int messages, long version, ByteBuffer data) {
        return new PUTX(responseTo, line, sharers, messages, version, data);
    }

    public static PUTX PUTX(short node, long line, short[] sharers, int messages, long version, ByteBuffer data) {
        return new PUTX(node, line, sharers, messages, version, data);
    }

    public static LineMessage DEL(short node, long line) {
        return new LineMessage(node, Type.DEL, line);
    }

//    public static INV INV(short node, long line) {
//        return INV(node, line, (short)-1);
//    }
    public static INV INV(short node, long line, short previousOwner) {
        return new INV(node, line, previousOwner);
    }

    public static INV INV(LineMessage responseTo, long line, short previousOwner) {
        return new INV(responseTo, line, previousOwner);
    }

    public static LineMessage INVACK(LineMessage responseTo) {
        return new LineMessage(responseTo, Type.INVACK, responseTo.getLine());
    }

    public static LineMessage INVACK(short node, long line) {
        return new LineMessage(node, Type.INVACK, line);
    }

    public static CHNGD_OWNR CHNGD_OWNR(short node, long line, short newOwner, boolean certain) {
        return new CHNGD_OWNR(node, line, newOwner, certain);
    }

    public static CHNGD_OWNR CHNGD_OWNR(LineMessage responseTo, long line, short newOwner, boolean certain) {
        return new CHNGD_OWNR(responseTo, line, newOwner, certain);
    }

    public static LineMessage NOT_FOUND(LineMessage responseTo) {
        return new LineMessage(responseTo, Type.NOT_FOUND);
    }

    public static BACKUP BACKUP(long line, long version, ByteBuffer data) {
        return new BACKUP((short) 0, line, version, data);
    }

    public static BACKUPACK BACKUPACK(short node, long line, long version) {
        return new BACKUPACK(node, line, version);
    }

    public static BACKUP_PACKET BACKUP_PACKET(long id, Collection<BACKUP> backups) {
        return new BACKUP_PACKET(id, backups);
    }

    public static BACKUP_PACKETACK BACKUP_PACKETACK(BACKUP_PACKET responseTo) {
        return new BACKUP_PACKETACK(responseTo);
    }

    public static ALLOC_REF ALLOC_REF(short node, int num) {
        return new ALLOC_REF(node, num);
    }

    public static ALLOCED_REF ALLOCED_REF(ALLOC_REF responseTo, long start, int num) {
        return new ALLOCED_REF(responseTo, start, num);
    }

    public static MSG MSG(MSG responseTo, byte[] data) {
        return new MSG(responseTo, data);
    }

    public static MSG MSG(short node, long line, boolean messenger, byte[] data) {
        return new MSG(node, line, messenger, data);
    }

    public static MSG MSG(short[] nodes, long line, boolean messenger, byte[] data) {
        return new MSG(nodes, line, messenger, data);
    }

    public static MSG MSG(short node, long line, boolean messenger, boolean pending, byte[] data) {
        return new MSG(node, line, messenger, pending, data);
    }

    public static MSG MSG(short[] nodes, long line, boolean messenger, boolean pending, byte[] data) {
        return new MSG(nodes, line, messenger, pending, data);
    }

    public static LineMessage MSGACK(MSG responseTo) {
        return new LineMessage(responseTo, Type.MSGACK);
    }

    public static LineMessage TIMEOUT(LineMessage responseTo) {
        return new LineMessage(responseTo, Type.TIMEOUT);
    }

    public static Message ACK(Message responseTo) {
        return new Message(responseTo, Type.ACK);
    }

    public static Message readMessage(DataInput in) throws IOException {
        final Type type = Type.values()[in.readByte()];
        final Message message = newMessage(type);
        message.read(in);
        return message;
    }

    public static Message fromByteArray(byte[] array) {
        return fromByteArray(array, 0, array.length);
    }

    public static Message fromByteArray(byte[] array, int offset, int length) {
        final Type type = Type.values()[array[offset]];
        if (LOG.isDebugEnabled())
            LOG.debug("from byte array type:" + type.name());
        final Message message = newMessage(type);
        message.read(array, offset + 1, length - 1);
        return message;
    }

    public static Message fromByteBuffer(ByteBuffer buffer) {
        final Type type = Type.values()[buffer.get()];
        if (LOG.isDebugEnabled())
            LOG.debug("from ByteBuffer type:" + type.name());
        final Message message = newMessage(type);
        message.read(buffer);
        return message;
    }

    public static Message newMessage(Type type) {
        switch (type) {
            case INV:
                return new INV();
            case GET:
            case GETX:
                return new GET(type);
            case PUT:
                return new PUT(type);
            case PUTX:
                return new PUTX();
            case CHNGD_OWNR:
                return new CHNGD_OWNR();
            case BACKUP:
                return new BACKUP();
            case BACKUPACK:
                return new BACKUPACK();
            case BACKUP_PACKET:
                return new BACKUP_PACKET();
            case BACKUP_PACKETACK:
                return new BACKUP_PACKETACK();
            case ALLOC_REF:
                return new ALLOC_REF();
            case ALLOCED_REF:
                return new ALLOCED_REF();
            case MSG:
                return new MSG();
            case ACK:
                return new Message(type);
            case DEL:
            case INVACK:
            case NOT_FOUND:
            case MSGACK:
            case TIMEOUT:
                return new LineMessage(type);
            case INVOKE:
                return new INVOKE(type);
            case INVRES:
                return new INVRES(type);
            default:
                throw new RuntimeException("Unrecognized message: " + type);
        }
    }
//    static {
//        Comm.STREAMABLES.register((byte) Type.GET.ordinal(), GET.class);
//        Comm.STREAMABLES.register((byte) Type.GETX.ordinal(), GET.class);
//        Comm.STREAMABLES.register((byte) Type.PUT.ordinal(), PUT.class);
//        Comm.STREAMABLES.register((byte) Type.PUTX.ordinal(), PUTX.class);
//        Comm.STREAMABLES.register((byte) Type.CHNGD_OWNR.ordinal(), CHNGD_OWNR.class);
//        Comm.STREAMABLES.register((byte) Type.BACKUP.ordinal(), BACKUP.class);
//        Comm.STREAMABLES.register((byte) Type.BACKUPACK.ordinal(), BACKUPACK.class);
//        Comm.STREAMABLES.register((byte) Type.INV.ordinal(), Message.class);
//        Comm.STREAMABLES.register((byte) Type.INVACK.ordinal(), Message.class);
//        Comm.STREAMABLES.register((byte) Type.ACK.ordinal(), Message.class);
//    }
    private static final byte FLAG_RESPONSE = 1;
    private static final byte FLAG_BROADCAST = 1 << 1;
    private static final byte FLAG_REPLY_REQUIRED = 1 << 2;
    private Type type;
    private byte flags;
    private long messageId = -1;
    private transient boolean incoming;
    private transient short node;
    private transient long timestamp;

    Message(Type type) {
        this.type = type;
        this.incoming = true;
    }

    public Message(Message responseTo, Type type) {
        this.incoming = false;
        this.node = responseTo.getNode();
        this.type = type;
        this.messageId = responseTo.messageId;
        // broadcast = false, replyRequired = false
        this.flags = FLAG_RESPONSE;
    }

    public Message(short node, Type type) {
        this.incoming = false;
        this.node = node;
        this.type = type;
        // response = false
        setBroadcast(node == -1);
        setReplyRequired(true);
    }

    public Message(short[] nodes, Type type) {
        this.incoming = false;
        Arrays.sort(nodes);
        this.node = -1;
        this.type = type;
        // response = false, broadcast = false
        setReplyRequired(true);
    }

    @Override
    public Message clone() {
        try {
            return (Message) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * This is actually used to pair requests and responses!
     *
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof Message))
            return false;
        final Message other = (Message) obj;
        if (this.incoming == other.incoming || this.isResponse() == other.isResponse())
            return super.equals(obj);

        if (this.messageId != other.messageId)
            return false;

        if (this.node >= 0 && other.node >= 0)
            return this.node == other.node;

        if (this.isBroadcast()) {
            assert !other.isBroadcast() & !this.isResponse() & other.isResponse();
            return true;
        }
        if (other.isBroadcast()) {
            assert !this.isBroadcast() & !other.isResponse() & this.isResponse();
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 83 * hash + (int) (this.messageId ^ (this.messageId >>> 32));
        return hash;
    }

    @Override
    public final String toString() {
        return partialToString() + ']';
    }

    protected String partialToString() {
        final StringBuffer sb = new StringBuffer();
        sb.append('[');
        sb.append(type).append(' ');
        sb.append(incoming ? "IN" : "OUT");
        sb.append(" #").append(messageId >= 0 ? messageId : "_");
        if (isResponse())
            sb.append('R');
        if (incoming && isBroadcast())
            sb.append(" BCAST");
        sb.append(' ').append(incoming ? "FROM " : "TO ");
        sb.append(node);
        if (!isResponse() && !isReplyRequired())
            sb.append(' ').append("(NO REP REQ)");
        return sb.toString();
    }

    public short getNode() {
        return node;
    }

    public Message setNode(short node) {
        this.node = node;
        if (!incoming)
            setBroadcast(node == (short) -1);
        return this;
    }

    public Message setIncoming() {
        this.incoming = true;
        return this;
    }

    public Message setOutgoing() {
        this.incoming = false;
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Message setMessageId(long messageId) {
        this.messageId = messageId;
        return this;
    }

    public long getMessageId() {
        return messageId;
    }

    public boolean isResponse() {
        return (flags & FLAG_RESPONSE) != 0;
    }

    private Message setBroadcast(boolean value) {
        assert !incoming;
        flags = (byte) (value ? (flags | FLAG_BROADCAST) : (flags & ~FLAG_BROADCAST));
        return this;
    }

    public boolean isBroadcast() {
        return (flags & FLAG_BROADCAST) != 0;
    }

    public final Message setReplyRequired(boolean value) {
        assert !incoming;
        flags = (byte) (value ? (flags | FLAG_REPLY_REQUIRED) : (flags & ~FLAG_REPLY_REQUIRED));
        return this;
    }

    public boolean isReplyRequired() {
        return (flags & FLAG_REPLY_REQUIRED) != 0;
    }

    public Type getType() {
        return type;
    }

    @Override
    final public void writeExternal(ObjectOutput out) throws IOException {
        write(out);
    }

    @Override
    final public void readExternal(ObjectInput in) throws IOException {
        type = Type.values()[in.read()];
        read(in);
    }

    private Streamable streamableNoBuffers() {
        return new Streamable() {
            @Override
            public int size() {
                return size1();
            }

            @Override
            public void write(DataOutput out) throws IOException {
                write1(out);
            }

            @Override
            public void read(DataInput in) throws IOException {
                read1(in);
            }
        };
    }

    @Override
    public final int size() {
        int size = size1();
        for (int i = 0; i < getNumDataBuffers(); i++) {
            final int remaining = getDataBuffer(i) == null ? 0 : getDataBuffer(i).remaining();
            size += 2 + remaining;
        }
        return size;
    }

    @Override
    public final void write(DataOutput out) throws IOException {
        write1(out);
        for (int i = 0; i < getNumDataBuffers(); i++) {
            final ByteBuffer buffer = getDataBuffer(i);
            out.writeShort(verifyShort(buffer.remaining()));
            Streamables.writeBuffer(out, buffer);
            buffer.rewind();
        }
    }

    @Override
    public final void read(DataInput in) throws IOException {
        read1(in);
        for (int i = 0; i < getNumDataBuffers(); i++) {
            int size = in.readUnsignedShort();
            byte[] array = new byte[size];
            in.readFully(array);
            final ByteBuffer buffer = ByteBuffer.wrap(array);
            setDataBuffer(i, buffer);
        }
    }

    public byte[] toByteArray() {
        return Streamables.toByteArray(this);
    }

    public void read(byte[] array, int offset, int length) {
        Streamables.fromByteArray(streamableNoBuffers(), array, offset, length);
        offset += size1() - 1; // subtract 1 for the type, which is also included in the size
        for (int i = 0; i < getNumDataBuffers(); i++) {
            final int size = Ints.fromBytes((byte) 0, (byte) 0, array[offset], array[offset + 1]);// big-endian, unsigned short
            offset += 2;
            final ByteBuffer buffer = ByteBuffer.wrap(array, offset, size).slice(); // we slice because wrap() simply sets the position to 'offset', so when we rewind it will go to the beginning of the array.
            setDataBuffer(i, buffer);
            offset += size;
        }
    }

    /**
     * The contract is: the first buffer is new. Comm can do whatever with it. The rest of the buffers point to cache buffers, so if they're not handled during the send() call (i.e. if they're queued
     * and handled later), then they must be copied. <br/> Note that this returns a different representation from toByteArray!
     *
     * @return
     */
    public ByteBuffer[] toByteBuffers() {
        if (LOG.isDebugEnabled())
            LOG.debug("to bb type " + type.name());
        final ByteBuffer buffer0 = ByteBuffer.allocate(size1() + 2 * getNumDataBuffers());
        Persistables.persistable(streamableNoBuffers()).write(buffer0);
        for (int i = 0; i < getNumDataBuffers(); i++)
            buffer0.putShort(getDataBuffer(i) != null ? verifyShort(getDataBuffer(i).remaining()) : 0);
        buffer0.flip();

        final ByteBuffer[] buffers = new ByteBuffer[1 + getNumDataBuffers()];
        buffers[0] = buffer0;
        for (int i = 0; i < getNumDataBuffers(); i++)
            buffers[1 + i] = getDataBuffer(i);
        return buffers;
    }

    /**
     * Note that you cannot use this method to read a buffer wrapping the array returned from toByteArray as the internal representation is different!
     *
     * @param buffer
     */
    public void read(ByteBuffer buffer) {
        Persistables.persistable(streamableNoBuffers()).read(buffer);

        final int n = getNumDataBuffers();
        int lengthsPosition = buffer.position();
        buffer.position(buffer.position() + 2 * n); // skip positions
        for (int i = 0; i < n; i++) {
            final int size = buffer.getShort(lengthsPosition) & 0xFFFF; // big-endian
            lengthsPosition += 2;

            final ByteBuffer b1 = Persistables.slice(buffer, size);
            setDataBuffer(i, b1);
        }
    }

    public int size1() {
        return 1 + 8 + 1;
    }

    public void write1(DataOutput out) throws IOException {
        out.writeByte(type.ordinal());
        out.writeLong(messageId);
        out.writeByte(flags);
    }

    public void read1(DataInput in) throws IOException {
        messageId = in.readLong();
        flags = in.readByte();
    }

    public final Message cloneDataBuffers() {
        for (int i = 0; i < getNumDataBuffers(); i++) {
            final ByteBuffer db = getDataBuffer(i);
            setDataBuffer(i, db == null ? null : Persistables.copyOf(db));
        }
        return this;
    }

    public int getNumDataBuffers() {
        return 0;
    }

    ByteBuffer getDataBuffer(int index) {
        throw new IndexOutOfBoundsException();
    }

    void setDataBuffer(int index, ByteBuffer buffer) {
        throw new IndexOutOfBoundsException();
    }

    private static short verifyShort(int size) {
        if (size >= (2 << 16))
            throw new RuntimeException("Buffer size (" + size + ") exceeds maximum of " + (2 << 16));
        return (short) size;
    }
    ///////////////////////////////////////////////////////////////////////

    public static class LineMessage extends Message {
        private long line;

        public LineMessage(short[] nodes, Type type, long line) {
            super(nodes, type);
            this.line = line;
        }

        public LineMessage(short node, Type type, long line) {
            super(node, type);
            this.line = line;
        }

        public LineMessage(LineMessage responseTo, Type type) {
            this(responseTo, type, responseTo.getLine());
        }

        public LineMessage(LineMessage responseTo, Type type, long line) {
            super(responseTo, type);
            assert line == responseTo.line;
            this.line = line;
        }

        public LineMessage(Type type) {
            super(type);
            this.line = -1;
        }

        @Override
        public LineMessage setMessageId(long messageId) {
            return (LineMessage) super.setMessageId(messageId);
        }

        @Override
        public LineMessage setIncoming() {
            return (LineMessage) super.setIncoming();
        }

        public long getLine() {
            return line;
        }

        @Override
        public final int size1() {
            return super.size1() + sizeNoHeader();
        }

        @Override
        public final void write1(DataOutput out) throws IOException {
            super.write1(out);
            writeNoHeader(out);
        }

        @Override
        public final void read1(DataInput in) throws IOException {
            super.read1(in);
            readNoHeader(in);
        }

        int sizeNoHeader() {
            return 8;
        }

        void writeNoHeader(DataOutput out) throws IOException {
            out.writeLong(line);
        }

        void readNoHeader(DataInput in) throws IOException {
            line = in.readLong();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", line: " + Long.toHexString(line);
        }

        @Override
        public LineMessage clone() {
            return (LineMessage) super.clone();
        }
    }
    ///////////////////////////////////////////////////////////////////////

    public static class INV extends LineMessage {
        private short previousOwner;

        INV() {
            super(Type.INV);
        }

        public INV(short node, long line, short previousOwner) {
            super(node, Type.INV, line);
            this.previousOwner = previousOwner;
        }

        public INV(LineMessage responseTo, long line, short previousOwner) {
            super(responseTo, Type.INV, line);
            this.previousOwner = previousOwner;
        }

        public short getPreviousOwner() {
            return previousOwner;
        }

        @Override
        int sizeNoHeader() {
            return super.sizeNoHeader() + 2;
        }

        @Override
        void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeShort(previousOwner);
        }

        @Override
        void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            previousOwner = in.readShort();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", previousOwner: " + previousOwner;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class GET extends LineMessage {
        GET(Type type) {
            super(type);
        }

        public GET(Type type, short node, long line) {
            super(node, type, line);
            assert type == Type.GET || type == Type.GETX;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class INVOKE extends LineMessage {
        private byte[] function;

        INVOKE(Type type) {
            super(type);
        }

        public INVOKE(Type type, short node, long line, LineFunction function) {
            super(node, type, line);
            this.function = Serialization.getInstance().write(function); // new JDKSerializer().write(function);
            assert type == Type.INVOKE;
        }

        public LineFunction getFunction() {
            return (LineFunction) Serialization.getInstance().read(function); // new JDKSerializer().read(function);
        }

        @Override
        public int sizeNoHeader() {
            return super.sizeNoHeader() + 2 + (function != null ? function.length : 0);
        }

        @Override
        public void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeShort(function != null ? (short) function.length : 0);
            if (function != null)
                out.write(function);
        }

        @Override
        public void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            final int dataLen = in.readUnsignedShort();
            if (dataLen == 0)
                function = null;
            else {
                function = new byte[dataLen];
                in.readFully(function);
            }
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", function: " + getFunction();
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class INVRES extends LineMessage {
        private byte[] result;

        INVRES(Type type) {
            super(type);
        }

        public INVRES(LineMessage responseTo, long line, Object result) {
            super(responseTo, Type.INVRES, line);
            this.result = Serialization.getInstance().write(result); // new JDKSerializer().write(result);
        }

        public Object getResult() {
            return Serialization.getInstance().read(result); // new JDKSerializer().read(result);
        }

        @Override
        public int sizeNoHeader() {
            return super.sizeNoHeader() + 2 + (result != null ? result.length : 0);
        }

        @Override
        public void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeShort(result != null ? (short) result.length : 0);
            if (result != null)
                out.write(result);
        }

        @Override
        public void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            final int dataLen = in.readUnsignedShort();
            if (dataLen == 0)
                result = null;
            else {
                result = new byte[dataLen];
                in.readFully(result);
            }
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", result: " + getResult();
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class PUT extends LineMessage {
        private long version;
        private ByteBuffer data;

        PUT(Type type) {
            super(type);
        }

        public PUT(LineMessage responseTo, long line, long version, ByteBuffer data) {
            super(responseTo, Type.PUT, line);
            this.data = data;
            this.version = version;
        }

        private PUT(Type type, LineMessage responseTo, long line, long version, ByteBuffer data) {
            super(responseTo, type, line);
            this.data = data;
            this.version = version;
        }

        private PUT(Type type, short node, long line, long version, ByteBuffer data) {
            super(node, type, line);
            this.data = data;
            this.version = version;
        }

        private PUT(Type type, short[] nodes, long line, long version, ByteBuffer data) {
            super(nodes, type, line);
            this.data = data;
            this.version = version;
        }

        public PUT(short node, long line, long version, ByteBuffer data) {
            super(node, Type.PUT, line);
            this.data = data;
            this.version = version;
        }

        public PUT(short[] nodes, long line, long version, ByteBuffer data) {
            super(nodes, Type.PUT, line);
            this.data = data;
            this.version = version;
        }

        public long getVersion() {
            return version;
        }

        public ByteBuffer getData() {
            return data;
        }

        public void setData(ByteBuffer data) {
            this.data = data;
        }

        @Override
        public int getNumDataBuffers() {
            return 1;
        }

        @Override
        ByteBuffer getDataBuffer(int index) {
            if (index != 0)
                throw new IndexOutOfBoundsException();
            return getData();
        }

        @Override
        void setDataBuffer(int index, ByteBuffer buffer) {
            if (index != 0)
                throw new IndexOutOfBoundsException();
            setData(buffer);
        }

        @Override
        int sizeNoHeader() {
            return super.sizeNoHeader() + 8;
        }

        @Override
        void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeLong(version);
        }

        @Override
        void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            version = in.readLong();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", version: " + version + ", data: " + (data == null ? "null" : "(" + data.limit() + " bytes)");
        }

        @Override
        public PUT clone() {
            final PUT clone = (PUT) super.clone();
            clone.data = data != null ? Persistables.copyOf(data) : null;
            return clone;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class PUTX extends PUT {
        private int parts; // no. of pending messages
        private short[] sharers;

        PUTX() {
            super(Type.PUTX);
        }

        public PUTX(LineMessage responseTo, long line, short[] sharers, int parts, long version, ByteBuffer data) {
            super(Type.PUTX, responseTo, line, version, data);
            this.parts = parts;
            this.sharers = sharers;
        }

        public PUTX(short node, long line, short[] sharers, int parts, long version, ByteBuffer data) {
            super(Type.PUTX, node, line, version, data);
            this.parts = parts;
            this.sharers = sharers;
        }

        public short[] getSharers() {
            return sharers;
        }

        public int getMessages() {
            return parts;
        }

        @Override
        public int sizeNoHeader() {
            return super.sizeNoHeader() + 2 + 2 + 2 * sharers.length;
        }

        @Override
        public void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeShort(parts);
            out.writeShort(sharers.length);
            for (short s : sharers)
                out.writeShort(s);
        }

        @Override
        public void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            parts = in.readUnsignedShort();
            int numSharers = in.readUnsignedShort();
            sharers = new short[numSharers];
            for (int i = 0; i < numSharers; i++)
                sharers[i] = in.readShort();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", sharers: " + Arrays.toString(sharers) + ", messages: " + parts;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class CHNGD_OWNR extends LineMessage {
        private short newOwner;
        private boolean certain;

        public CHNGD_OWNR() {
            super(Type.CHNGD_OWNR);
        }

        public CHNGD_OWNR(short node, long line, short newOwner, boolean certain) {
            super(node, Type.CHNGD_OWNR, line);
            this.newOwner = newOwner;
            this.certain = certain;
        }

        public CHNGD_OWNR(LineMessage responseTo, long line, short newOwner, boolean certain) {
            super(responseTo, Type.CHNGD_OWNR, line);
            this.newOwner = newOwner;
            this.certain = certain;
        }

        public short getNewOwner() {
            return newOwner;
        }

        @Override
        public int sizeNoHeader() {
            return super.sizeNoHeader() + 1 + 2;
        }

        @Override
        public void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeBoolean(certain);
            out.writeShort(newOwner);
        }

        @Override
        public void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            certain = in.readBoolean();
            newOwner = in.readShort();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", newOwner: " + newOwner + ", certain: " + certain;
        }
    }
    ///////////////////////////////////////////////////////////////////////

    public static class BACKUP extends PUT {
        public BACKUP() {
            super(Type.BACKUP);
        }

        public BACKUP(short node, long line, long version, ByteBuffer data) {
            super(Type.BACKUP, node, line, version, data);
        }
    }
    ///////////////////////////////////////////////////////////////////////

    public static class BACKUPACK extends LineMessage {
        private long version;

        public BACKUPACK() {
            super(Type.BACKUPACK);
        }

        public BACKUPACK(short node, long line, long version) {
            super(node, Type.BACKUPACK, line);
            this.version = version;
        }

        public long getVersion() {
            return version;
        }

        @Override
        public int sizeNoHeader() {
            return super.sizeNoHeader() + 8;
        }

        @Override
        public void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeLong(version);
        }

        @Override
        public void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            version = in.readLong();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", version: " + version;
        }
    }
    ///////////////////////////////////////////////////////////////////////

    public static class BACKUP_PACKET extends Message {
        private List<BACKUP> backups;
        private long id;

        public BACKUP_PACKET(long id, Collection<BACKUP> backups) {
            super((short) 0, Type.BACKUP_PACKET);
            this.backups = ImmutableList.copyOf(backups);
            this.id = id;
        }

        public BACKUP_PACKET() {
            super(Type.BACKUP_PACKET);
        }

        public List<BACKUP> getBackups() {
            return backups;
        }

        public long getId() {
            return id;
        }

        @Override
        public int getNumDataBuffers() {
            return backups.size();
        }

        @Override
        ByteBuffer getDataBuffer(int index) {
            return backups.get(index).getData();
        }

        @Override
        void setDataBuffer(int index, ByteBuffer buffer) {
            backups.get(index).setData(buffer);
        }

        @Override
        public Message setNode(short node) {
            super.setNode(node);
            for (BACKUP msg : backups)
                msg.setNode(node);
            return this;
        }

        @Override
        public int size1() {
            int size = super.size1() + 8 + 4;
            for (BACKUP msg : backups)
                size += msg.sizeNoHeader();
            return size;
        }

        @Override
        public void write1(DataOutput out) throws IOException {
            super.write1(out);
            out.writeLong(id);
            out.writeInt(backups.size());
            for (BACKUP msg : backups)
                msg.writeNoHeader(out);
        }

        @Override
        public void read1(DataInput in) throws IOException {
            super.read1(in);
            id = in.readLong();
            final int num = in.readInt();
            this.backups = new ArrayList<BACKUP>(num);
            for (int i = 0; i < num; i++) {
                BACKUP backup = new BACKUP();
                backup.readNoHeader(in);
                backups.add(backup);
            }
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", id: " + id + ", backups: " + backups.toString();
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class BACKUP_PACKETACK extends Message {
        private long id;

        public BACKUP_PACKETACK(BACKUP_PACKET responseTo) {
            super(responseTo, Type.BACKUP_PACKETACK);
            this.id = responseTo.getId();
        }

        public BACKUP_PACKETACK() {
            super(Type.BACKUP_PACKETACK);
        }

        public long getId() {
            return id;
        }

        @Override
        public int size1() {
            return super.size1() + 8;
        }

        @Override
        public void write1(DataOutput out) throws IOException {
            super.write1(out);
            out.writeLong(id);
        }

        @Override
        public void read1(DataInput in) throws IOException {
            super.read1(in);
            id = in.readLong();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", id: " + id;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class ALLOC_REF extends Message {
        private int num;

        public ALLOC_REF(short node, int num) {
            super(node, Type.ALLOC_REF);
            this.num = num;
        }

        public ALLOC_REF() {
            super(Type.ALLOC_REF);
        }

        public int getNum() {
            return num;
        }

        @Override
        public int size1() {
            return super.size1() + 4;
        }

        @Override
        public void write1(DataOutput out) throws IOException {
            super.write1(out);
            out.writeInt(num);
        }

        @Override
        public void read1(DataInput in) throws IOException {
            super.read1(in);
            num = in.readInt();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", num: " + num;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class ALLOCED_REF extends Message {
        private long start;
        private int num;

        public ALLOCED_REF(ALLOC_REF responseTo, long start, int num) {
            super(responseTo, Type.ALLOCED_REF);
            this.start = start;
            this.num = num;
        }

        public ALLOCED_REF() {
            super(Type.ALLOCED_REF);
        }

        public long getStart() {
            return start;
        }

        public int getNum() {
            return num;
        }

        @Override
        public int size1() {
            return super.size1() + 8 + 4;
        }

        @Override
        public void write1(DataOutput out) throws IOException {
            super.write1(out);
            out.writeLong(start);
            out.writeInt(num);
        }

        @Override
        public void read1(DataInput in) throws IOException {
            super.read1(in);
            start = in.readLong();
            num = in.readInt();
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", start: " + Long.toHexString(start) + ", num: " + num;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static class MSG extends LineMessage {
        private static final byte MESSENGER = 1;
        private static final byte PENDING = 1 << 1;
        private byte[] data;
        private byte flags;

        MSG() {
            super(Type.MSG);
        }

        public MSG(MSG responseTo, byte[] data) {
            super(responseTo, Type.MSG);
            this.flags = 0;
            this.data = data;
        }

        private MSG(short node, long line, boolean messenger, byte[] data) {
            this(node, line, messenger, false, data);
        }

        private MSG(short[] nodes, long line, boolean messenger, byte[] data) {
            this(nodes, line, messenger, false, data);
        }

        private MSG(short node, long line, boolean messenger, boolean pending, byte[] data) {
            super(node, Type.MSG, line);
            this.flags = 0;
            flags |= messenger ? MESSENGER : 0;
            flags |= pending ? PENDING : 0;
            this.data = data;
        }

        private MSG(short[] nodes, long line, boolean messenger, boolean pending, byte[] data) {
            super(nodes, Type.MSG, line);
            flags |= messenger ? MESSENGER : 0;
            flags |= pending ? PENDING : 0;
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public boolean isMessenger() {
            return (flags & MESSENGER) != 0;
        }

        public boolean isPending() {
            return (flags & PENDING) != 0;
        }

        public void setPending(boolean value) {
            this.flags |= value ? PENDING : 0;
        }

        @Override
        public int sizeNoHeader() {
            return super.sizeNoHeader() + 2 + 1 + (data != null ? data.length : 0);
        }

        @Override
        public void writeNoHeader(DataOutput out) throws IOException {
            super.writeNoHeader(out);
            out.writeByte(flags);
            out.writeShort(data != null ? (short) data.length : 0);
            if (data != null)
                out.write(data);
        }

        @Override
        public void readNoHeader(DataInput in) throws IOException {
            super.readNoHeader(in);
            flags = in.readByte();
            final int dataLen = in.readUnsignedShort();
            if (dataLen == 0)
                data = null;
            else {
                data = new byte[dataLen];
                in.readFully(data);
            }
        }

        @Override
        public String partialToString() {
            return super.partialToString() + ", messenger: " + isMessenger() + ", pending: " + isPending() + ", data: " + (data == null ? "null" : "(" + data.length + " bytes)" + (isPending() ? " (pending)" : ""));
        }
    }
}
