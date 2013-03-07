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

import co.paralleluniverse.galaxy.cluster.NodeInfo;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 *
 * @author pron
 */
class NodeInfoPropertyEqual extends TypeSafeMatcher<NodeInfo> {
    private final String property;
    private final Object value;

    public NodeInfoPropertyEqual(String property, Object value) {
        super(NodeInfo.class);
        this.property = property;
        this.value = value;
    }

    @Override
    public boolean matchesSafely(NodeInfo item) {
        if (item == null)
            return false;

        if (property.equals("id"))
            return new Short(item.getNodeId()).equals(value);
        if (property.equals("name"))
            return item.getName().equals(value);

        final Object actualValue = item.get(property);

        if (actualValue == value)
            return true;

        if (value != null)
            return value.equals(actualValue);
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("property " + property + " is " + value.toString());
    }
}
