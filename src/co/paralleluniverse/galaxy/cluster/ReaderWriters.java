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
package co.paralleluniverse.galaxy.cluster;

import com.google.common.base.Charsets;

/**
 * Some {@link ReaderWriter}s for common types.
 */
public final class ReaderWriters {
    public final static ReaderWriter<String> STRING = new ReaderWriter<String>() {

        @Override
        public String read(byte[] data) {
            return new String(data, Charsets.UTF_8);
        }

        @Override
        public byte[] write(String value) {
            return value.getBytes(Charsets.UTF_8);
        }
    };
    
    public final static ReaderWriter<Boolean> BOOLEAN = new ReaderWriter<Boolean>() {

        @Override
        public Boolean read(byte[] data) {
            return Boolean.parseBoolean(STRING.read(data));
        }

        @Override
        public byte[] write(Boolean value) {
            return STRING.write(value.toString());
        }
    };
    
    public final static ReaderWriter<Short> SHORT = new ReaderWriter<Short>() {

        @Override
        public Short read(byte[] data) {
            return Short.parseShort(STRING.read(data));
        }

        @Override
        public byte[] write(Short value) {
            return STRING.write(value.toString());
        }
    };
    
    public final static ReaderWriter<Integer> INTEGER = new ReaderWriter<Integer>() {

        @Override
        public Integer read(byte[] data) {
            return Integer.parseInt(STRING.read(data));
        }

        @Override
        public byte[] write(Integer value) {
            return STRING.write(value.toString());
        }
    };
    
    public final static ReaderWriter<Long> LONG = new ReaderWriter<Long>() {

        @Override
        public Long read(byte[] data) {
            return Long.parseLong(STRING.read(data));
        }

        @Override
        public byte[] write(Long value) {
            return STRING.write(value.toString());
        }
    };
    
    
    private ReaderWriters() {
    }
}
