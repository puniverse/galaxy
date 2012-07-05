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
        public void error(String msg) {
            logger.error(msg);
        }

        @Override
        public void error(String msg, Throwable throwable) {
            logger.error(msg, throwable);
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
        public void info(String msg) {
            logger.info(msg);
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
        public void setLevel(String level) {
        }

        @Override
        public String getLevel() {
            return "";
        }
    }
}
