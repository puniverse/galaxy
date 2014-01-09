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
