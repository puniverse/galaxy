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

import java.net.InetSocketAddress;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.Channels;

/**
 * Adapted from https://github.com/netty/netty/blob/master/handler/src/main/java/io/netty/handler/ipfilter/IpFilteringHandlerImpl.java Remove this and use official when new Netty ships.
 *
 * @author pron
 */
public abstract class IpFilteringHandler implements ChannelUpstreamHandler {
    /**
     * Called when the channel is connected. It returns True if the corresponding connection is to be allowed. Else it returns False.
     *
     * @param inetSocketAddress the remote {@link InetSocketAddress} from client
     * @return True if the corresponding connection is allowed, else False.
     */
    protected abstract boolean accept(ChannelHandlerContext ctx, ChannelEvent e, InetSocketAddress inetSocketAddress) throws Exception;

    /**
     * Internal method to test if the current channel is blocked. Should not be overridden.
     *
     * @return True if the current channel is blocked, else False
     */
    protected boolean isBlocked(ChannelHandlerContext ctx) {
        return ctx.getAttachment() != null;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent evt = (ChannelStateEvent) e;
            switch (evt.getState()) {
                case OPEN:
                case BOUND:
                    // Special case: OPEND and BOUND events are before CONNECTED,
                    // but CLOSED and UNBOUND events are after DISCONNECTED: should those events be blocked too?
                    if (isBlocked(ctx))         
                        return; // don't pass to next level since channel was blocked early
                    else {
                        ctx.sendUpstream(e);
                        return;
                    }
                case CONNECTED:
                    if (evt.getValue() != null) {
                        // CONNECTED
                        InetSocketAddress inetSocketAddress = (InetSocketAddress) e.getChannel().getRemoteAddress();
                        if (!accept(ctx, e, inetSocketAddress)) {
                            ctx.setAttachment(Boolean.TRUE);
                            Channels.close(e.getChannel());

                            if (isBlocked(ctx))
                                return; // don't pass to next level since channel was blocked early
                        }
                        // This channel is not blocked
                        ctx.setAttachment(null);
                    } else {
                        // DISCONNECTED
                        if (isBlocked(ctx))
                            return; // don't pass to next level since channel was blocked early
                    }
                    break;
            }
        }
        if (isBlocked(ctx))
            return; // don't pass to next level since channel was blocked early
        
        // Whatever it is, if not blocked, goes to the next level
        ctx.sendUpstream(e);
    }
}