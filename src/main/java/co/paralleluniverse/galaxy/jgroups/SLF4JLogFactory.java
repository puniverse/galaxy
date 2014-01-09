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
package co.paralleluniverse.galaxy.jgroups;

import org.jgroups.logging.CustomLogFactory;
import org.jgroups.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pron
 */
public class SLF4JLogFactory implements CustomLogFactory {
    @Override
    public Log getLog(Class clazz) {
        return new SLF4JLog(LoggerFactory.getLogger(clazz));
    }

    @Override
    public Log getLog(String category) {
        return new SLF4JLog(LoggerFactory.getLogger(category));
    }

    static class SLF4JLog implements Log {
        private final Logger logger;

        public SLF4JLog(Logger logger) {
            this.logger = logger;
        }

        @Override
        public boolean isFatalEnabled() {
            return logger.isErrorEnabled();
        }

        @Override
        public boolean isErrorEnabled() {
            return logger.isErrorEnabled();
        }

        @Override
        public boolean isWarnEnabled() {
            return logger.isWarnEnabled();
        }

        @Override
        public boolean isInfoEnabled() {
            return logger.isInfoEnabled();
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        @Override
        public boolean isTraceEnabled() {
            return logger.isTraceEnabled();
        }

        @Override
        public void fatal(String msg) {
            logger.error(msg);
        }

        @Override
        public void fatal(String msg, Throwable throwable) {
            logger.error(msg, throwable);
        }

        @Override
        public void fatal(String msg, Object... args) {
            logger.error(msg, args);
        }

        @Override
        public void error(String msg) {
            logger.error(msg);
        }

        @Override
        public void error(String msg, Throwable throwable) {
            logger.error(msg, throwable);
        }

        @Override
        public void error(String format, Object... args) {
            logger.error(format, args);
        }

        @Override
        public void warn(String msg) {
            logger.warn(msg);
        }

        @Override
        public void warn(String msg, Throwable throwable) {
            logger.warn(msg, throwable);
        }

        @Override
        public void warn(String msg, Object... args) {
            logger.warn(msg, args);
        }

        @Override
        public void info(String msg) {
            logger.info(msg);
        }

        @Override
        public void info(String msg, Object... args) {
            logger.info(msg, args);
        }

        @Override
        public void debug(String msg) {
            logger.debug(msg);
        }

        @Override
        public void debug(String msg, Throwable throwable) {
            logger.debug(msg, throwable);
        }

        @Override
        public void debug(String msg, Object... args) {
            logger.debug(msg, args);
        }

        @Override
        public void trace(Object msg) {
            logger.trace(msg.toString());
        }

        @Override
        public void trace(String msg) {
            logger.trace(msg);
        }

        @Override
        public void trace(String msg, Throwable throwable) {
            logger.trace(msg, throwable);
        }

        @Override
        public void trace(String msg, Object... args) {
            logger.trace(msg, args);
        }

        @Override
        public void setLevel(String level) {
        }

        @Override
        public String getLevel() {
            return "";
        }
    }
}
