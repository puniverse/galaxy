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
