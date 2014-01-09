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
package co.paralleluniverse.galaxy.core;

import co.paralleluniverse.common.monitoring.PeriodicMonitor;
import co.paralleluniverse.galaxy.monitoring.CacheMXBean;
import co.paralleluniverse.galaxy.monitoring.Counter;
import java.beans.ConstructorProperties;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author pron
 */
class JMXCacheMonitor extends PeriodicMonitor implements CacheMonitor, CacheMXBean {
    private static final long[] histogramBins = {500, 1000, 10000, 100000}; // in microseconds
    private final EnumMap<Message.Type, MessageMonitor> messageMonitors = new EnumMap<Message.Type, MessageMonitor>(Message.Type.class);
    private final EnumMap<Op.Type, OpMonitor> opMonitors = new EnumMap<Op.Type, OpMonitor>(Op.Type.class);
    private final EnumMap<MessageDelayReason, DelayedMessageMonitor> messageDelayMonitors = new EnumMap<MessageDelayReason, DelayedMessageMonitor>(MessageDelayReason.class);
    //
    private final Counter hitsCounter = new Counter();
    private final Counter staleHitsCounter = new Counter();
    private final Counter missesCounter = new Counter();
    private final Counter invalidatesCounter = new Counter();
    private final Counter stalePurgesCounter = new Counter();
    private int hits;
    private int staleHits;
    private int misses;
    private int invalidates;
    private int stalePurges;

    @ConstructorProperties({"name"})
    public JMXCacheMonitor(String name) {
        super(CacheMXBean.class, "co.paralleluniverse.galaxy.core:type=Cache");
    }

    @Override
    protected void initCounters() {
        for (Op.Type op : Op.Type.values())
            opMonitors.put(op, new OpMonitor(histogramBins));
        for (Message.Type m : Message.Type.values())
            messageMonitors.put(m, new MessageMonitor());
        for (MessageDelayReason reason : MessageDelayReason.values())
            messageDelayMonitors.put(reason, new DelayedMessageMonitor());
    }

    @Override
    protected void collectAndResetCounters() {
        for (MessageMonitor mm : messageMonitors.values())
            mm.collectAndResetCounters();
        for (OpMonitor om : opMonitors.values())
            om.collectAndResetCounters();
        for (DelayedMessageMonitor dm : messageDelayMonitors.values())
            dm.collectAndResetCounters();

        hits = (int) hitsCounter.get();
        staleHits = (int) staleHitsCounter.get();
        misses = (int) missesCounter.get();
        invalidates = (int) invalidatesCounter.get();
        stalePurges = (int)stalePurgesCounter.get();
        
        hitsCounter.reset();
        staleHitsCounter.reset();
        missesCounter.reset();
        invalidatesCounter.reset();
        stalePurgesCounter.reset();
    }

    @Override
    protected void resetCounters() {
        for (MessageMonitor mm : messageMonitors.values())
            mm.reset();
        for (OpMonitor om : opMonitors.values())
            om.reset();
        for (DelayedMessageMonitor dm : messageDelayMonitors.values())
            dm.reset();
        hitsCounter.reset();
        staleHitsCounter.reset();
        missesCounter.reset();
        invalidatesCounter.reset();
        stalePurgesCounter.reset();
    }

    @Override
    public void addHit() {
        hitsCounter.inc();
    }

    @Override
    public void addStaleHit() {
        staleHitsCounter.inc();
    }

    @Override
    public void addMiss() {
        missesCounter.inc();
    }

    @Override
    public void addInvalidate(int num) {
        invalidatesCounter.add(num);
    }

    @Override
    public void addMessageSent(Message.Type msg) {
        messageMonitors.get(msg).addSent();
    }

    @Override
    public void addMessageReceived(Message.Type msg) {
        messageMonitors.get(msg).addReceived();
    }

    @Override
    public void addOp(Op.Type type, long durationMicroSeconds) {
        if (durationMicroSeconds > 0)
            opMonitors.get(type).addOp(durationMicroSeconds);
    }

    @Override
    public void addMessageHandlingDelay(int numDelayed, long totalDelayNanos, MessageDelayReason reason) {
        final DelayedMessageMonitor m = messageDelayMonitors.get(reason);
        m.addMessages(numDelayed);
        m.addDelay(totalDelayNanos);
    }

    @Override
    public void addStalePurge(int num) {
        stalePurgesCounter.add(num);
    }

    ////////////////////////////////////////////
    private static class MessageMonitor {
        private final Counter messagesSentCounter = new Counter();
        private final Counter messagesReceivedCounter = new Counter();
        private int messagesSent;
        private int messagesReceived;

        void addSent() {
            messagesSentCounter.inc();
        }

        void addReceived() {
            messagesReceivedCounter.inc();
        }

        int getSent() {
            return messagesSent;
        }

        int getReceived() {
            return messagesReceived;
        }

        void collectAndResetCounters() {
            collect();
            reset();
        }

        void collect() {
            messagesSent = (int) messagesSentCounter.get();
            messagesReceived = (int) messagesReceivedCounter.get();
        }

        void reset() {
            messagesSentCounter.reset();
            messagesReceivedCounter.reset();
        }
    }

    private static class OpMonitor {
        private final Counter numOpsCounter = new Counter();
        private final AtomicLong maxTimeCounter = new AtomicLong();
        private final long[] histogramBins;
        private final Counter[] histogramCounters;
        private int numOps;
        private long maxTime;
        private final long[] rawHistogram;
        private final float[] histogram;

        OpMonitor(long... histogramBins) {
            this.histogramBins = histogramBins;
            this.histogramCounters = new Counter[histogramBins.length + 1];
            this.rawHistogram = new long[histogramCounters.length];
            this.histogram = new float[histogramCounters.length];
            for (int i = 0; i < histogramCounters.length; i++)
                this.histogramCounters[i] = new Counter();
        }

        int getNumOps() {
            return numOps;
        }

        synchronized long getMaxTime() {
            return maxTime;
        }

        synchronized float[] getHistogram() {
            return histogram;
        }

        void addOp(long duration) {
            numOpsCounter.inc();

            if (duration == 0) {
                histogramCounters[0].inc();
                return;
            }

            for (;;) {
                long currentMax = maxTimeCounter.get();
                if (duration > currentMax) {
                    if (maxTimeCounter.compareAndSet(currentMax, duration))
                        break;
                } else
                    break;
            }
            for (int i = 0; i < histogramBins.length; i++) {
                if (duration < histogramBins[i]) {
                    histogramCounters[i].inc();
                    return;
                }
            }
            histogramCounters[histogramBins.length].inc();
        }

        void collectAndResetCounters() {
            collect();
            reset();
        }

        synchronized void collect() {
            numOps = (int) numOpsCounter.get();
            maxTime = maxTimeCounter.get();
            long sum = 0;
            for (int i = 0; i < rawHistogram.length; i++) {
                final long val = histogramCounters[i].get();
                sum += val;
                rawHistogram[i] = val;
            }

            for (int i = 0; i < histogram.length; i++)
                histogram[i] = (float) ((double) rawHistogram[i] / sum * 100.0);

        }

        void reset() {
            numOpsCounter.reset();
            maxTimeCounter.set(0);
            for (Counter histogramCounter : histogramCounters)
                histogramCounter.reset();
        }
    }

    private class DelayedMessageMonitor {
        private final Counter messagesCounter = new Counter();
        private final Counter delayCounter = new Counter();
        private int messages;
        private long totalDelay;

        void addDelay(long nanos) {
            delayCounter.add(TimeUnit.MICROSECONDS.convert(nanos, TimeUnit.NANOSECONDS));
        }
        
        void addMessages(int num) {
            messagesCounter.add(num);
        }

        int getMessages() {
            return messages;
        }

        long getTotalMicrosecondDelayPerSecond() {
            return totalDelay;
        }

        void collectAndResetCounters() {
            collect();
            reset();
        }

        void collect() {
            messages = (int) messagesCounter.get();
            
            double secondsSinceLast = getMillisSinceLastCollect() / 1000.0;
            totalDelay = (long) ((double)delayCounter.get() / secondsSinceLast);
        }

        void reset() {
            messagesCounter.reset();
            messagesCounter.reset();
        }
    }

    private int getNumOp(Op.Type ot) {
        return opMonitors.get(ot).getNumOps();
    }

    private float[] getOpHistogram(Op.Type ot) {
        return opMonitors.get(ot).getHistogram();
    }

    private int getNumMessagesReceived(Message.Type mt) {
        return messageMonitors.get(mt).getReceived();
    }

    private int getNumMessagesSent(Message.Type mt) {
        return messageMonitors.get(mt).getSent();
    }

    @Override
    public int getNumOpGet() {
        return getNumOp(Op.Type.GET);
    }

    @Override
    public float[] getOpHistogramGet() {
        return getOpHistogram(Op.Type.GET);
    }

    @Override
    public int getNumOpGetS() {
        return getNumOp(Op.Type.GETS);
    }

    @Override
    public float[] getOpHistogramGetS() {
        return getOpHistogram(Op.Type.GETS);
    }

    @Override
    public int getNumOpGetX() {
        return getNumOp(Op.Type.GETX);
    }

    @Override
    public float[] getOpHistogramGetX() {
        return getOpHistogram(Op.Type.GETX);
    }

    @Override
    public int getNumOpSet() {
        return getNumOp(Op.Type.SET);
    }

    @Override
    public float[] getOpHistogramSet() {
        return getOpHistogram(Op.Type.SET);
    }

    @Override
    public int getNumOpPut() {
        return getNumOp(Op.Type.PUT);
    }

    @Override
    public int getNumOpDel() {
        return getNumOp(Op.Type.DEL);
    }

    @Override
    public int getNumOpSend() {
        return getNumOp(Op.Type.SEND);
    }

    @Override
    public int getNumMessagesReceivedGET() {
        return getNumMessagesReceived(Message.Type.GET);
    }

    @Override
    public int getNumMessagesSentGET() {
        return getNumMessagesSent(Message.Type.GET);
    }

    @Override
    public int getNumMessagesReceivedGETX() {
        return getNumMessagesReceived(Message.Type.GETX);
    }

    @Override
    public int getNumMessagesSentGETX() {
        return getNumMessagesSent(Message.Type.GETX);
    }

    @Override
    public int getNumMessagesReceivedPUT() {
        return getNumMessagesReceived(Message.Type.PUT);
    }

    @Override
    public int getNumMessagesSentPUT() {
        return getNumMessagesSent(Message.Type.PUT);
    }

    @Override
    public int getNumMessagesReceivedPUTX() {
        return getNumMessagesReceived(Message.Type.PUTX);
    }

    @Override
    public int getNumMessagesSentPUTX() {
        return getNumMessagesSent(Message.Type.PUTX);
    }

    @Override
    public int getNumMessagesReceivedINV() {
        return getNumMessagesReceived(Message.Type.INV);
    }

    @Override
    public int getNumMessagesSentINV() {
        return getNumMessagesSent(Message.Type.INV);
    }

    @Override
    public int getNumMessagesReceivedINVACK() {
        return getNumMessagesReceived(Message.Type.INVACK);
    }

    @Override
    public int getNumMessagesSentINVACK() {
        return getNumMessagesSent(Message.Type.INVACK);
    }

    @Override
    public int getNumMessagesReceivedCHNGD_OWNR() {
        return getNumMessagesReceived(Message.Type.CHNGD_OWNR);
    }

    @Override
    public int getNumMessagesSentCHNGD_OWNR() {
        return getNumMessagesSent(Message.Type.CHNGD_OWNR);
    }

    @Override
    public int getNumMessagesReceivedMSG() {
        return getNumMessagesReceived(Message.Type.MSG);
    }

    @Override
    public int getNumMessagesSentMSG() {
        return getNumMessagesSent(Message.Type.MSG);
    }

    @Override
    public int getNumMessagesReceivedMSGACK() {
        return getNumMessagesReceived(Message.Type.MSGACK);
    }

    @Override
    public int getNumMessagesSentMSGACK() {
        return getNumMessagesSent(Message.Type.MSGACK);
    }

    @Override
    public int getNumMessagesDelayedDueLock() {
        return messageDelayMonitors.get(MessageDelayReason.LOCK).getMessages();
    }
    
    @Override
    public long getTotalMicrosecondDelayPerSecondDueLock() {
        return messageDelayMonitors.get(MessageDelayReason.LOCK).getTotalMicrosecondDelayPerSecond();
    }
    
    @Override
    public int getNumMessagesDelayedDueBackup() {
        return messageDelayMonitors.get(MessageDelayReason.BACKUP).getMessages();
    }
    
    @Override
    public long getTotalMicrosecondDelayPerSecondDueBackup() {
        return messageDelayMonitors.get(MessageDelayReason.BACKUP).getTotalMicrosecondDelayPerSecond();
    }
    
    @Override
    public int getNumMessagesDelayedDueOther() {
        return messageDelayMonitors.get(MessageDelayReason.OTHER).getMessages();
    }
    
    @Override
    public long getTotalMicrosecondDelayPerSecondDueOther() {
        return messageDelayMonitors.get(MessageDelayReason.OTHER).getTotalMicrosecondDelayPerSecond();
    }
    
    @Override
    public int getHits() {
        return hits;
    }

    @Override
    public int getStaleHits() {
        return staleHits;
    }

    @Override
    public int getMisses() {
        return misses;
    }

    @Override
    public int getInvalidates() {
        return invalidates;
    }
}
