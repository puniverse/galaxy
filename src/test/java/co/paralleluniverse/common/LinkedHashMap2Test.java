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
package co.paralleluniverse.common;

import co.paralleluniverse.common.collection.LinkedHashMap2;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import static org.hamcrest.CoreMatchers.*;

/**
 *
 * @author pron
 */
public class LinkedHashMap2Test {
    LinkedHashMap2<String, Integer> map = null;

    public LinkedHashMap2Test() {
    }

    @Before
    public void setUp() {
        map = new LinkedHashMap2<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
    }

    @After
    public void tearDown() {
        map = null;
    }

    @Test
    public void testFirstKey() {
        assertThat(map.firstKey(), is("a"));
        assertThat(new LinkedHashMap2<String, Integer>().firstKey(), is(nullValue()));
    }

    @Test
    public void testFirstEntry() {
        assertThat(map.firstEntry().getKey(), is("a"));
        assertThat(map.firstEntry().getValue(), is(1));
        assertThat(new LinkedHashMap2<String, Integer>().firstEntry(), is(nullValue()));
    }

    @Test
    public void testLastKey() {
        assertThat(map.lastKey(), is("c"));
        assertThat(new LinkedHashMap2<String, Integer>().lastKey(), is(nullValue()));
    }

    @Test
    public void testLastEntry() {
        assertThat(map.lastEntry().getKey(), is("c"));
        assertThat(map.lastEntry().getValue(), is(3));
        assertThat(new LinkedHashMap2<String, Integer>().lastEntry(), is(nullValue()));
    }

    @Test
    public void testNextKey() {
        assertThat(map.nextKey("a"), is("b"));
        assertThat(map.nextKey("b"), is("c"));
        assertThat(map.nextKey("c"), is(nullValue()));
        assertThat(map.nextKey("d"), is(nullValue()));
    }

    @Test
    public void testNextEntry() {
        assertThat(map.nextEntry("a").getKey(), is("b"));
        assertThat(map.nextEntry("a").getValue(), is(2));
        assertThat(map.nextEntry("b").getKey(), is("c"));
        assertThat(map.nextEntry("b").getValue(), is(3));
        assertThat(map.nextEntry("c"), is(nullValue()));
        assertThat(map.nextEntry("d"), is(nullValue()));
    }

    @Test
    public void testPreviousKey() {
        assertThat(map.previousKey("b"), is("a"));
        assertThat(map.previousKey("c"), is("b"));
        assertThat(map.previousKey("a"), is(nullValue()));
        assertThat(map.previousKey("d"), is(nullValue()));
    }
    
    @Test
    public void testPreviousEntry() {
        assertThat(map.previousEntry("b").getKey(), is("a"));
        assertThat(map.previousEntry("b").getValue(), is(1));
        assertThat(map.previousEntry("c").getKey(), is("b"));
        assertThat(map.previousEntry("c").getValue(), is(2));
        assertThat(map.previousEntry("a"), is(nullValue()));
        assertThat(map.previousEntry("d"), is(nullValue()));
    }
}
