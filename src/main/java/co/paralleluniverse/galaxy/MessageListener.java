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
package co.paralleluniverse.galaxy;

/**
 * A listener for messages sent in the grid.
 *
 * @see Messenger
 */
public interface MessageListener {
    /**
     * Called when a message has been received.
     *
     * @param fromNode The cluster node ID of the node that originated the message.
     * @param message  The message's contents.
     */
    void messageReceived(short fromNode, byte[] message);
}
