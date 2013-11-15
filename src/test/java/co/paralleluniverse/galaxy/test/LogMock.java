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

import java.util.Arrays;
import org.mockito.MockSettings;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.CallsRealMethods;
import org.mockito.internal.stubbing.answers.DoesNothing;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.internal.stubbing.answers.ThrowsException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.OngoingStubbing;
import org.mockito.stubbing.Stubber;

/**
 *
 * @author pron
 */
public final class LogMock {
    //private static boolean log = false;
    private static final ThreadTreeLocal<Boolean> log = new ThreadTreeLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private static final ThreadTreeLocal<Thread> mainThread = new ThreadTreeLocal<Thread>();
    public static final Answer<?> LOGS_CALLS = logAnswer(Mockito.RETURNS_DEFAULTS);

    private static <T> Answer<T> logAnswer(final Answer<T> answer) {
        return new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object retValue;
                try {
                    retValue = answer.answer(invocation);
                    if (log.get()) {
                        if (!(invocation.getMethod().getName().equals("toString") && invocation.getMethod().getParameterTypes().length == 0 && invocation.getMethod().getReturnType().equals(String.class))) {
                            if (invocation.getMethod().getReturnType().equals(void.class) || invocation.getMethod().getReturnType().equals(Void.class))
                                System.out.println((Thread.currentThread() != mainThread.get() ? Thread.currentThread().getName() : "") + " CALL: " + invocation + " (" + Arrays.toString(Thread.currentThread().getStackTrace()) + ")");
                            else
                                System.out.println((Thread.currentThread() != mainThread.get() ? Thread.currentThread().getName() : "") + " CALL: " + invocation + " => " + retValue + " (" + Arrays.toString(Thread.currentThread().getStackTrace()) + ")");
                        }
                    }
                    return retValue;
                } catch (Throwable t) {
                    if (log.get())
                        System.out.println((Thread.currentThread() != mainThread.get() ? Thread.currentThread().getName() : "") + " CALL: " + invocation + " => " + "thrown " + t);
                    throw t;
                }
            }
        };
    }

    public static void startLogging() {
        mainThread.set(Thread.currentThread());
        log.set(true); // log = true; //
    }

    public static void stopLogging() {
        log.set(false); // log = false; //
        mainThread.set(null);
    }

    public static <T> T spy(T object) {
        return Mockito.mock((Class<T>) object.getClass(), Mockito.withSettings().spiedInstance(object).defaultAnswer(logAnswer(Mockito.CALLS_REAL_METHODS)));
    }

    public static <T> T mock(Class<T> classToMock) {
        return Mockito.mock(classToMock, Mockito.withSettings().defaultAnswer(LOGS_CALLS));
    }

    public static <T> T mock(Class<T> classToMock, String name) {
        return Mockito.mock(classToMock, Mockito.withSettings().name(name).defaultAnswer(LOGS_CALLS));
    }

    public static <T> T mock(Class<T> classToMock, MockSettings mockSettings) {
        return Mockito.mock(classToMock, mockSettings.defaultAnswer(LOGS_CALLS));
    }

    public static <T> T mock(Class<T> classToMock, Answer answer) {
        return Mockito.mock(classToMock, answer);
    }

    public static <T> OngoingStubbing<T> when(T methodCall) {
        return new OngoingStubbingExt<T>(Mockito.when(methodCall));
    }

    public static Stubber doThrow(Throwable toBeThrown) {
        return doAnswer(new ThrowsException(toBeThrown));
    }

    public static Stubber doReturn(Object toBeReturned) {
        return doAnswer(new Returns(toBeReturned));
    }

    public static Stubber doNothing() {
        return doAnswer(new DoesNothing());
    }

    public static Stubber doAnswer(Answer answer) {
        return new StubberExt(Mockito.doAnswer(logAnswer(answer)));
    }

    private static class OngoingStubbingExt<T> implements OngoingStubbing<T> {
        private final OngoingStubbing<T> delegate;

        public OngoingStubbingExt(OngoingStubbing<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public OngoingStubbing<T> thenThrow(Throwable... throwables) {
            if (throwables.length > 1)
                throw new UnsupportedOperationException();
            return thenAnswer(new ThrowsException(throwables[0]));
        }

        @Override
        public OngoingStubbing<T> thenThrow(Class<? extends Throwable>... throwableClasses) {
            try {
                if (throwableClasses.length > 0)
                    throw new UnsupportedOperationException();
                return thenAnswer(new ThrowsException(throwableClasses[0].newInstance()));
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public OngoingStubbing<T> thenReturn(T value, T... values) {
            if (values.length > 0)
                throw new UnsupportedOperationException();
            return thenReturn(value);
        }

        @Override
        public OngoingStubbing<T> thenReturn(T retValue) {
            return thenAnswer(new Returns(retValue));
        }

        @Override
        public OngoingStubbing<T> thenCallRealMethod() {
            return thenAnswer(new CallsRealMethods());
        }

        @Override
        public OngoingStubbing<T> thenAnswer(Answer<?> answer) {
            return new OngoingStubbingExt(delegate.thenAnswer(logAnswer(answer)));
        }

        @Override
        public OngoingStubbing<T> then(Answer<?> answer) {
            return new OngoingStubbingExt(delegate.then(logAnswer(answer)));
        }

        @Override
        public <M> M getMock() {
            return delegate.getMock();
        }
    }

    private static class StubberExt implements Stubber {
        private final Stubber delegate;

        public StubberExt(Stubber delegate) {
            this.delegate = delegate;
        }

        @Override
        public Stubber doThrow(Throwable toBeThrown) {
            return doAnswer(new ThrowsException(toBeThrown));
        }

        @Override
        public Stubber doThrow(Class<? extends Throwable> toBeThrown) {
            try {
                return doAnswer(new ThrowsException(toBeThrown.newInstance()));
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public Stubber doReturn(Object toBeReturned) {
            return doAnswer(new Returns(toBeReturned));
        }

        @Override
        public Stubber doNothing() {
            return doAnswer(new DoesNothing());
        }

        @Override
        public Stubber doAnswer(Answer answer) {
            return new StubberExt(delegate.doAnswer(logAnswer(answer)));
        }

        @Override
        public Stubber doCallRealMethod() {
            return delegate.doCallRealMethod();
        }

        @Override
        public <T> T when(T mock) {
            return delegate.when(mock);
        }
    }

    private LogMock() {
    }
}
