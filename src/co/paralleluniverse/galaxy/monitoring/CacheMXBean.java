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
package co.paralleluniverse.galaxy.monitoring;

/**
 *
 * @author pron
 */
public interface CacheMXBean extends PeriodicMXBean {
    // OPS
    int getNumOpGet(); // GET

    float[] getOpHistogramGet();

    int getNumOpGetS(); // GETS

    float[] getOpHistogramGetS();

    int getNumOpGetX(); // GETX

    float[] getOpHistogramGetX();

    int getNumOpSet(); // SET

    float[] getOpHistogramSet();

    int getNumOpPut(); // PUT

    int getNumOpDel(); // DEL

    int getNumOpSend(); // SEND

    // MESSAGES
    int getNumMessagesReceivedGET(); // GET

    int getNumMessagesSentGET();

    int getNumMessagesReceivedGETX(); // GETX

    int getNumMessagesSentGETX();

    int getNumMessagesReceivedPUT(); // PUT

    int getNumMessagesSentPUT();

    int getNumMessagesReceivedPUTX(); // PUTX

    int getNumMessagesSentPUTX();

    int getNumMessagesReceivedINV(); // INV

    int getNumMessagesSentINV();

    int getNumMessagesReceivedINVACK(); // INVACK

    int getNumMessagesSentINVACK();

    int getNumMessagesReceivedCHNGD_OWNR(); // CHNGD_OWNR

    int getNumMessagesSentCHNGD_OWNR();

    int getNumMessagesReceivedMSG(); // MSG

    int getNumMessagesSentMSG();

    int getNumMessagesReceivedMSGACK(); // MSGACK

    int getNumMessagesSentMSGACK();

    // HITS/MISSED
    int getHits();

    int getStaleHits();

    int getMisses();

    int getInvalidates();

    // Message processing delays
    int getNumMessagesDelayedDueLock();

    long getTotalMicrosecondDelayPerSecondDueLock();

    int getNumMessagesDelayedDueBackup();

    long getTotalMicrosecondDelayPerSecondDueBackup();

    int getNumMessagesDelayedDueOther();

    long getTotalMicrosecondDelayPerSecondDueOther();
}
