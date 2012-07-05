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

import com.google.common.primitives.Shorts;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Objects;

/**
 *
 * @author pron
 */
public class MessageEqualTo extends MessageMatcher {
    public MessageEqualTo(Message message) {
        super(message);
    }

    @Override
    public boolean matchesSafely(Message item) {
        if (message == null)
            return item == null;
        if (item == null)
            return false;
        if (item == message)
            return true;

        if (!(message.getType() == item.getType()
                && message.isResponse() == item.isResponse()
                && message.getMessageId() == item.getMessageId()
                && message.getNode() == item.getNode()))
            return false;

        if (!message.getClass().equals(item.getClass()))
            return false;
        return fieldEqual(message, item, message.getClass());
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
        if (a.getClass().isArray()) {
            if (a.getClass().getComponentType().equals(short.class))
                return new HashSet<Short>(Shorts.asList((short[])a)).equals(new HashSet<Short>(Shorts.asList((short[])b)));
            else
                return Objects.deepEquals(a, b);
        } else
            return a.equals(b);
    }
}
