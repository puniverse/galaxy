package co.paralleluniverse.galaxy.objects;

import co.paralleluniverse.common.io.Persistable;
import java.nio.ByteBuffer;

/**
 * Wraps T and implements Distributed interface
 *
 * @author eitan
 * @param <T>
 */
public class DistributedObject<T> implements Distributed {
    private final T obj;
    private long id;

    public DistributedObject(T obj) {
        this.obj = obj;
        this.id = 0;

    }

    public T getObj() {
        return obj;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public int size() {
        if (obj instanceof Persistable)
            return ((Persistable) obj).size() + Long.SIZE / 8;
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void write(ByteBuffer buffer) {
        buffer.putLong(id);
        if (obj instanceof Persistable) {
            ((Persistable) obj).write(buffer);
            return;
        }
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void read(ByteBuffer buffer) {
        id = buffer.getLong();
        if (obj instanceof Persistable) {
            ((Persistable) obj).read(buffer);
            return;
        }
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
