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

import co.paralleluniverse.common.monitoring.Metrics;
import co.paralleluniverse.galaxy.core.Message.Type;
import com.codahale.metrics.Meter;
import static com.codahale.metrics.MetricRegistry.name;
import com.codahale.metrics.Timer;
import java.util.EnumMap;
import java.util.concurrent.TimeUnit;


/**
 *
 * @author pron
 */
class MetricsCacheMonitor implements CacheMonitor {
    private final EnumMap<Message.Type, MessageMonitor> messageMonitors = new EnumMap<Message.Type, MessageMonitor>(Message.Type.class);
    private final EnumMap<Op.Type, Timer> opMonitors = new EnumMap<Op.Type, Timer>(Op.Type.class);
    private final EnumMap<MessageDelayReason, DelayedMessageMonitor> messageDelayMonitors = new EnumMap<MessageDelayReason, DelayedMessageMonitor>(MessageDelayReason.class);
    private final Meter hits = Metrics.meter(metric("hits"));
    private final Meter staleHits = Metrics.meter(metric("staleHits"));
    private final Meter misses = Metrics.meter(metric("misses"));
    private final Meter invalidates = Metrics.meter(metric("invalidates"));
    private final Meter stalePurges = Metrics.meter(metric("stalePurges"));

    public MetricsCacheMonitor() {
        for (Op.Type op : Op.Type.values())
            opMonitors.put(op, Metrics.timer(name("co.paralleluniverse", "galaxy", "Cache", "ops", op.name())));
        for (Message.Type m : Message.Type.values())
            messageMonitors.put(m, new MessageMonitor(m));
        for (MessageDelayReason reason : MessageDelayReason.values())
            messageDelayMonitors.put(reason, new DelayedMessageMonitor(reason));
    }

    protected final String metric(String name) {
        return name("co.paralleluniverse", "galaxy", "Cache", name);
    }

    @Override
    public void setMonitoredObject(Object obj) {
    }

    @Override
    public void addHit() {
        hits.mark();
    }

    @Override
    public void addStaleHit() {
        staleHits.mark();
    }

    @Override
    public void addMiss() {
        misses.mark();
    }

    @Override
    public void addInvalidate(int num) {
        invalidates.mark(num);
    }

    @Override
    public void addMessageSent(Type msg) {
        messageMonitors.get(msg).addSent();
    }

    @Override
    public void addMessageReceived(Type msg) {
        messageMonitors.get(msg).addReceived();
    }

    @Override
    public void addOp(Op.Type type, long durationMicroSeconds) {
        opMonitors.get(type).update(durationMicroSeconds, TimeUnit.MICROSECONDS);
    }

    @Override
    public void addMessageHandlingDelay(int numDelayed, long totalDelayNanos, MessageDelayReason reason) {
        final DelayedMessageMonitor m = messageDelayMonitors.get(reason);
        m.addMessages(numDelayed);
        m.addDelay(totalDelayNanos);
    }

    @Override
    public void addStalePurge(int num) {
        stalePurges.mark(num);
    }

    private static class MessageMonitor {
        private final Meter messagesSent;
        private final Meter messagesReceived;

        public MessageMonitor(Message.Type type) {
            this.messagesSent = Metrics.meter(name("co.paralleluniverse", "galaxy", "Cache", "messages", "sent", type.name()));
            this.messagesReceived = Metrics.meter(name("co.paralleluniverse", "galaxy", "Cache", "messages", "received", type.name()));
        }

        void addSent() {
            messagesSent.mark();
        }

        void addReceived() {
            messagesReceived.mark();
        }
    }

    private static class DelayedMessageMonitor {
        private final Meter messages;
        private final Timer delay;

        public DelayedMessageMonitor(MessageDelayReason reason) {
            this.messages = Metrics.meter(name("co.paralleluniverse", "galaxy", "Cache", "messages", "messagesDelayed", reason.name()));
            this.delay = Metrics.timer(name("co.paralleluniverse", "galaxy", "Cache", "messages", "delay", reason.name())); 
        }

        void addDelay(long nanos) {
            delay.update(nanos, TimeUnit.NANOSECONDS);
        }

        void addMessages(int num) {
            messages.mark(num);
        }
    }
}
