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

import co.paralleluniverse.galaxy.core.Message.Type;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Timer;
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
    private final Meter hits = Metrics.newMeter(Cache.class, "hits", "hit", TimeUnit.SECONDS);
    private final Meter staleHits = Metrics.newMeter(Cache.class, "staleHits", "staleHit", TimeUnit.SECONDS);
    private final Meter misses = Metrics.newMeter(Cache.class, "misses", "miss", TimeUnit.SECONDS);
    private final Meter invalidates = Metrics.newMeter(Cache.class, "invalidates", "invalidation", TimeUnit.SECONDS);
    private final Meter stalePurges = Metrics.newMeter(Cache.class, "stalePurges", "stalePurge", TimeUnit.SECONDS);

    public MetricsCacheMonitor() {
        for (Op.Type op : Op.Type.values())
            opMonitors.put(op, Metrics.newTimer(Cache.class, "ops", op.name(), TimeUnit.MICROSECONDS, TimeUnit.SECONDS));
        for (Message.Type m : Message.Type.values())
            messageMonitors.put(m, new MessageMonitor(m));
        for (MessageDelayReason reason : MessageDelayReason.values())
            messageDelayMonitors.put(reason, new DelayedMessageMonitor(reason));
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
            this.messagesSent = Metrics.newMeter(Cache.class, "sent", type.name(), "messages", TimeUnit.SECONDS);
            this.messagesReceived = Metrics.newMeter(Cache.class, "received", type.name(), "messages", TimeUnit.SECONDS);
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
            this.messages = Metrics.newMeter(Cache.class, "messagesDelayed", reason.name(), "messages", TimeUnit.SECONDS);
            this.delay = Metrics.newTimer(Cache.class, "delay", reason.name(), TimeUnit.MICROSECONDS, TimeUnit.SECONDS);
        }

        void addDelay(long nanos) {
            delay.update(nanos, TimeUnit.NANOSECONDS);
        }
        
        void addMessages(int num) {
            messages.mark(num);
        }
    }
}
