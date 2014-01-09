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
