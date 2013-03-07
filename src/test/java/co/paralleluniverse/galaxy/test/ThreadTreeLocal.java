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
package co.paralleluniverse.galaxy.test;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 *
 * @author pron
 */
public class ThreadTreeLocal<T> {
    private static final InheritableThreadLocal<Map<ThreadTreeLocal, Object>> threadTreeLocalMap = new InheritableThreadLocal<Map<ThreadTreeLocal, Object>>() {

        @Override
        protected Map<ThreadTreeLocal, Object> initialValue() {
            return new IdentityHashMap<ThreadTreeLocal, Object>();
        }
    };

    public T get() {
        Map<ThreadTreeLocal, Object> map = getMap();
        if (map.containsKey(this))
            return (T) map.get(this);
        else
            return setInitialValue();
    }

    public void set(T value) {
        getMap().put(this, value);
    }

    protected T initialValue() {
        return null;
    }

    private T setInitialValue() {
        T value = initialValue();

        getMap().put(this, value);
        return value;
    }

    private Map<ThreadTreeLocal, Object> getMap() {
        return threadTreeLocalMap.get();
    }
}
