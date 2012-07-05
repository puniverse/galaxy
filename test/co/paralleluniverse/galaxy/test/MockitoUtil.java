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

import com.google.common.primitives.Primitives;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.mockito.ArgumentCaptor;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import org.mockito.verification.VerificationMode;

/**
 *
 * @author pron
 */
public class MockitoUtil {
    public static <T> Placeholder<T> arg(Class<T> clazz) {
        return new Placeholder(clazz);
    }

    public static Object capture(Object mock, String methodName, Object... args) {
        return capture(mock, times(1), methodName, args);
    }
    
    public static Object capture(Object mock, VerificationMode mode, String methodName, Object... args) {
        try {
            int argIndex = -1;
            Class argType = null;
            Class[] types = new Class[args.length];
            for (int i = 0; i < args.length; i++) {

                if (args[i] instanceof Placeholder) {
                    if (argType != null)
                        throw new IllegalArgumentException("More than one argument placeholder provided");
                    argIndex = i;
                    argType = ((Placeholder) args[i]).clazz;
                    types[i] = argType;
                } else
                    types[i] = Primitives.unwrap(args[i].getClass());
            }

            final Method method = mock.getClass().getDeclaredMethod(methodName, types);
            method.setAccessible(true);

            ArgumentCaptor captor = ArgumentCaptor.forClass(argType);
            method.invoke(verify(mock, mode), replace(args, argIndex, captor));
            return captor.getValue();
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException(ex.getCause());
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        } catch (SecurityException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Object[] replace(Object[] args, int index, ArgumentCaptor captor) {
        Object[] res = new Object[args.length];
        for(int i=0; i<args.length; i++) {
            if(i == index)
                res[i] = captor.capture();
            else
                res[i] = eq(args[i]);
        }
        return res;
    }
    private static class Placeholder<T> {
        final Class<T> clazz;

        public Placeholder(Class<T> clazz) {
            this.clazz = clazz;
        }
    }

    private MockitoUtil() {
    }
}
