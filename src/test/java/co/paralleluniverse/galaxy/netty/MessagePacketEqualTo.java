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
package co.paralleluniverse.galaxy.netty;

import co.paralleluniverse.galaxy.core.MessageEqualTo;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 *
 * @author pron
 */
public class MessagePacketEqualTo extends TypeSafeMatcher<MessagePacket> {
    private final MessagePacket packet;
    
    public MessagePacketEqualTo(MessagePacket packet) {
        this.packet = packet;
    }

    @Override
    public boolean matchesSafely(MessagePacket item) {
        if(packet.getMessages().size() != item.getMessages().size())
            return false;
        for(int i=0; i<packet.getMessages().size(); i++) {
            if(!new MessageEqualTo(packet.getMessages().get(i)).matches(item.getMessages().get(i)))
                return false;
        }
        return true;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText(packet.toString());
    }
}
