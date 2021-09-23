/*
 * Copyright 2015 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.cache;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.BroadcasterCache;
import org.atmosphere.cpr.BroadcasterCacheListener;
import org.atmosphere.cpr.BroadcasterConfig;
import org.atmosphere.util.ExecutorsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An improved {@link BroadcasterCache} implementation that is based on the unique identifier (UUID) that all
 * {@link AtmosphereResource}s have.
 *
 * @author Paul Khodchenkov
 * @author Jeanfrancois Arcand
 */
public class UUIDBroadcasterCache implements BroadcasterCache {

    private final static Logger logger = LoggerFactory.getLogger(UUIDBroadcasterCache.class);

    private final Map<String, ConcurrentLinkedQueue<CacheMessage>> messages = new ConcurrentHashMap<String, ConcurrentLinkedQueue<CacheMessage>>();
    private final Map<String, Long> activeClients = new ConcurrentHashMap<String, Long>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    protected final List<BroadcasterCacheInspector> inspectors = new LinkedList<BroadcasterCacheInspector>();
    private ScheduledFuture scheduledFuture;
    protected ScheduledExecutorService taskScheduler;
    private long clientIdleTime = TimeUnit.SECONDS.toMillis(60); // 1 minutes
    private long invalidateCacheInterval = TimeUnit.SECONDS.toMillis(30); // 30 seconds
    private boolean shared = true;
    protected final List<Object> emptyList = Collections.<Object>emptyList();
    protected final List<BroadcasterCacheListener> listeners = new LinkedList<BroadcasterCacheListener>();

    /**
     * This class wraps all messages to be delivered to a client. The class is thread safe to be accessed in a
     * concurrent context.
     */
    public final static class ClientQueue implements Serializable {
        private static final long serialVersionUID = -126253550299206646L;

        private final ConcurrentLinkedQueue<CacheMessage> queue = new ConcurrentLinkedQueue<CacheMessage>();
        private final Set<String> ids = Collections.synchronizedSet(new HashSet<String>());

        public ConcurrentLinkedQueue<CacheMessage> getQueue() {
            return queue;
        }

        public Set<String> getIds() {
            return ids;
        }

        @Override
        public String toString() {
            return queue.toString();
        }
    }

    @Override
    public void configure(BroadcasterConfig config) {
        Object o = config.getAtmosphereConfig().properties().get("shared");
        if (o != null) {
            shared = Boolean.parseBoolean(o.toString());
        }

        if (shared) {
            taskScheduler = ExecutorsFactory.getScheduler(config.getAtmosphereConfig());
        } else {
            taskScheduler = Executors.newSingleThreadScheduledExecutor();
        }

        clientIdleTime = TimeUnit.SECONDS.toMillis(
                Long.valueOf(config.getAtmosphereConfig().getInitParameter(ApplicationConfig.UUIDBROADCASTERCACHE_CLIENT_IDLETIME, "60")));

        invalidateCacheInterval = TimeUnit.SECONDS.toMillis(
                Long.valueOf(config.getAtmosphereConfig().getInitParameter(ApplicationConfig.UUIDBROADCASTERCACHE_IDLE_CACHE_INTERVAL, "30")));
    }

    @Override
    public void start() {
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                invalidateExpiredEntries();
            }
        }, 0, invalidateCacheInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        cleanup();

        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
    }

    @Override
    public void cleanup() {
        messages.clear();
        activeClients.clear();
        emptyList.clear();
        inspectors.clear();

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    @Override
    public CacheMessage addToCache(String broadcasterId, String uuid, BroadcastMessage message) {
        if (logger.isTraceEnabled()) {
            logger.trace("Adding for AtmosphereResource {} cached messages {}", uuid, message.message());
            logger.trace("Active clients {}", activeClients());
        }

        String messageId = UUID.randomUUID().toString();
        boolean cache = true;
        if (!inspect(message)) {
            cache = false;
        }

        CacheMessage cacheMessage = new CacheMessage(messageId, message.message(), uuid);
        if (cache) {
            if (uuid.equals(NULL)) {
                //no clients are connected right now, caching message for all active clients
                for (Map.Entry<String, Long> entry : activeClients.entrySet()) {
                    addMessageIfNotExists(broadcasterId, entry.getKey(), cacheMessage);
                }
            } else {
                cacheCandidate(broadcasterId, uuid);
                addMessageIfNotExists(broadcasterId, uuid, cacheMessage);
            }
        }
        return cacheMessage;
    }

    @Override
    public List<Object> retrieveFromCache(String broadcasterId, String uuid) {
        try {
            readWriteLock.writeLock().lock();
            cacheCandidate(broadcasterId, uuid);

            ConcurrentLinkedQueue<CacheMessage> clientQueue = messages.remove(uuid);
            if (clientQueue != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Retrieved for AtmosphereResource {} cached messages {}", uuid, (long) clientQueue.size());
                    logger.trace("Available cached message {}", messages);
                }
                return queueToList(clientQueue);
            } else {
                return Collections.emptyList();
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private List<Object> queueToList(ConcurrentLinkedQueue<CacheMessage> clientQueue) {
        Iterator<CacheMessage> iter = clientQueue.iterator();
        List<Object> list = new ArrayList<>();
        while (iter.hasNext()) {
            list.add(iter.next().getMessage());
        }
        return list;
    }    

    @Override
    public BroadcasterCache clearCache(String broadcasterId, String uuid, CacheMessage message) {
        ConcurrentLinkedQueue<CacheMessage> clientQueue = messages.get(uuid);
        if (clientQueue != null && !clientQueue.isEmpty()) {
            logger.trace("Removing for AtmosphereResource {} cached message {}", uuid, message.getMessage());
            notifyRemoveCache(broadcasterId, new CacheMessage(message.getId(), message.getCreateTime(), message.getMessage(), uuid));
            clientQueue.remove(message);
        }
        return this;
    }

    @Override
    public BroadcasterCache inspector(BroadcasterCacheInspector b) {
        inspectors.add(b);
        return this;
    }

    @Override
    public BroadcasterCache addBroadcasterCacheListener(BroadcasterCacheListener l) {
        listeners.add(l);
        return this;
    }

    @Override
    public BroadcasterCache removeBroadcasterCacheListener(BroadcasterCacheListener l) {
        listeners.remove(l);
        return this;
    }

    protected String uuid(AtmosphereResource r) {
        return r.uuid();
    }

    private void addMessageIfNotExists(String broadcasterId, String clientId, CacheMessage message) {
        if (!hasMessage(clientId, message.getId())) {
            addMessage(broadcasterId, clientId, message);
        } else {
            logger.debug("Duplicate message {} for client {}", message, clientId);
        }
    }

    private void addMessage(String broadcasterId, String clientId, CacheMessage message) {
        try {
            readWriteLock.readLock().lock();
            ConcurrentLinkedQueue<CacheMessage> clientQueue = messages.get(clientId);

            if (clientQueue == null) {
                clientQueue = new ConcurrentLinkedQueue<>();
                // Make sure the client is not in the process of being invalidated
                if (activeClients.get(clientId) != null) {
                    messages.put(clientId, clientQueue);
                } else {
                    // The entry has been invalidated
                    logger.debug("Client {} is no longer active. Not caching message {}}", clientId, message);
                    return;
                }
            }
            notifyAddCache(broadcasterId, message);
            clientQueue.offer(message);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    private void notifyAddCache(String broadcasterId, CacheMessage message) {
        for (BroadcasterCacheListener l : listeners) {
            try {
                l.onAddCache(broadcasterId, message);
            } catch (Exception ex) {
                logger.warn("Listener exception", ex);
            }
        }
    }

    private void notifyRemoveCache(String broadcasterId, CacheMessage message) {
        for (BroadcasterCacheListener l : listeners) {
            try {
                l.onRemoveCache(broadcasterId, message);
            } catch (Exception ex) {
                logger.warn("Listener exception", ex);
            }
        }
    }

    private boolean hasMessage(String clientId, String messageId) {
        ConcurrentLinkedQueue<CacheMessage> clientQueue = messages.get(clientId);
        return clientQueue != null && queueHasMessage(messageId, clientQueue);
    }

    private boolean queueHasMessage(String messageId, ConcurrentLinkedQueue<CacheMessage> clientQueue) {
        Iterator<CacheMessage> iter = clientQueue.iterator();
        boolean match = false;
        while (iter.hasNext() && !match) {
            CacheMessage m = iter.next();
            match = Objects.equals(m.getId(), messageId);
        }
        return match;
    }

    public Map<String, ClientQueue> messages() {
        return messages;
    }

    public Map<String, Long> activeClients() {
        return activeClients;
    }

    protected boolean inspect(BroadcastMessage m) {
        for (BroadcasterCacheInspector b : inspectors) {
            if (!b.inspect(m)) return false;
        }
        return true;
    }

    public void setInvalidateCacheInterval(long invalidateCacheInterval) {
        this.invalidateCacheInterval = invalidateCacheInterval;
        scheduledFuture.cancel(true);
        start();
    }

    public void setClientIdleTime(long clientIdleTime) {
        this.clientIdleTime = clientIdleTime;
    }

    protected void invalidateExpiredEntries() {
        long now = System.currentTimeMillis();

        Set<String> inactiveClients = new HashSet<String>();
        for (Map.Entry<String, Long> entry : activeClients.entrySet()) {
            if (now - entry.getValue() > clientIdleTime) {
                logger.trace("Invalidate client {}", entry.getKey());
                inactiveClients.add(entry.getKey());
            }
        }

        for (String clientId : inactiveClients) {
            activeClients.remove(clientId);
            messages.remove(clientId);
        }

        for (String msg : messages().keySet()) {
            if (!activeClients().containsKey(msg)) {
                messages().remove(msg);
            }
        }
    }

    @Override
    public BroadcasterCache excludeFromCache(String broadcasterId, AtmosphereResource r) {
        activeClients.remove(r.uuid());
        return this;
    }

    @Override
    public BroadcasterCache cacheCandidate(String broadcasterId, String uuid) {
        long now = System.currentTimeMillis();
        activeClients.put(uuid, now);
        return this;
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }

    public List<BroadcasterCacheListener> listeners() {
        return listeners;
    }

    public List<BroadcasterCacheInspector> inspectors() {
        return inspectors;
    }
}
