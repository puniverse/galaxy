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
package co.paralleluniverse.common.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 *
 * @author pron
 */
public class DegenerateInvocationHandler implements InvocationHandler {
    public static DegenerateInvocationHandler INSTANCE = new DegenerateInvocationHandler();
    
    private DegenerateInvocationHandler() {
    }
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class))
            return Boolean.FALSE;
        if (method.getReturnType().equals(byte.class) || method.getReturnType().equals(Byte.class))
            return (byte) 0;
        if (method.getReturnType().equals(char.class) || method.getReturnType().equals(Character.class))
            return (char) 0;
        if (method.getReturnType().equals(short.class) || method.getReturnType().equals(Short.class))
            return (short) 0;
        if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class))
            return 0;
        if (method.getReturnType().equals(long.class) || method.getReturnType().equals(Long.class))
            return 0;
        if (method.getReturnType().equals(float.class) || method.getReturnType().equals(Float.class))
            return 0.0f;
        if (method.getReturnType().equals(double.class) || method.getReturnType().equals(Double.class))
            return 0.0f;
        else
            return null;
    }
}
