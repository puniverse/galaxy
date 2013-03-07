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

import java.lang.reflect.Field;
import java.util.Objects;

/**
 *
 * @author pron
 */
public class MessageDeepEqualTo extends MessageMatcher {
    public MessageDeepEqualTo(Message message) {
        super(message);
    }

    @Override
    public boolean matchesSafely(Message item) {
        return equals(message, item);
    }

    private static boolean equals(Message m1, Message m2) {
        if (m1 == null)
            return m2 == null;
        if (m2 == null)
            return false;
        if (m2 == m1)
            return true;

        if (!(m1.getType() == m2.getType()
                && m1.isResponse() == m2.isResponse()
                && m1.getMessageId() == m2.getMessageId()))
            return false; // ignore nodes

        if (!m1.getClass().equals(m2.getClass()))
            return false;
        return fieldEqual(m1, m2, m1.getClass());
    }
    
    private static boolean fieldEqual(Object a, Object b, Class clazz) {
        if (clazz.equals(Message.class))
            return true;

        if (!fieldEqual(a, b, clazz.getSuperclass()))
            return false;

        try {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                if (!equals(field.get(a), field.get(b)))
                    return false;
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean equals(Object a, Object b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.getClass().isArray())
            return Objects.deepEquals(a, b);
        else
            return a.equals(b);
    }
}
