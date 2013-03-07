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
package co.paralleluniverse.galaxy.objects;

import co.paralleluniverse.galaxy.CacheListener;
import co.paralleluniverse.galaxy.Store;
import co.paralleluniverse.galaxy.TimeoutException;
import java.nio.ByteBuffer;

/**
 *
 * @author pron
 */
public abstract class SerializingDistributedObject<T> implements CacheListener {
    private T ref;
    private long id;

    public SerializingDistributedObject() {
    }

    private byte[] serialize(Object obj) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private T deserialize(byte[] buffer) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void invalidated(long id) {
        ref = null;
    }

    @Override
    public void evicted(long id) {
        ref = null;
    }

    @Override
    public void received(long id, long version, ByteBuffer data) {
    }

    public T get(Store store) {
        if (ref == null) {
            for (;;) {
                try {
                    byte[] data = store.get(id);
                    ref = deserialize(data);
                    break;
                } catch (TimeoutException e) {
                }
            }
        }
        return ref;
    }
}
