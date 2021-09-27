package org.atmosphere.cpr;

import org.atmosphere.cache.BroadcastMessage;
import org.atmosphere.cache.UUIDBroadcasterCache;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class UUIDBroadcasterCacheThreadingTest {

    private static final String BROADCASTER_ID = "B1";
    public static final int NUM_MESSAGES = 100000;
    private final AtomicInteger counter = new AtomicInteger(0);
    private static final String CLIENT_ID = java.util.UUID.randomUUID().toString();
    private final ConcurrentLinkedQueue<Object> retreivedMessages = new ConcurrentLinkedQueue<Object>();

    @Test
    public void testUuidBroadcasterCacheThreading() {
        AtmosphereConfig config = new AtmosphereFramework().getAtmosphereConfig();
        BroadcasterConfig bConfig = new BroadcasterConfig(null,config,false,null);
        DefaultBroadcasterFactory factory = new DefaultBroadcasterFactory(DefaultBroadcaster.class, "NEVER", config);
        config.framework().setBroadcasterFactory(factory);
        final UUIDBroadcasterCache cache = new UUIDBroadcasterCache();
        cache.configure(bConfig);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < NUM_MESSAGES; i++) {
                    BroadcastMessage broadcastMessage = createBroadcastMessage();
                    cache.addToCache(BROADCASTER_ID, CLIENT_ID, broadcastMessage);
                }
            }
        });
        t.start();

        long endTime = System.currentTimeMillis() + 15000;
        int totalRetrieved = 0;
        while (totalRetrieved < NUM_MESSAGES && System.currentTimeMillis() < endTime) {
            List<Object> messages = cache.retrieveFromCache(BROADCASTER_ID, CLIENT_ID);
            if (!messages.isEmpty()) {
                retreivedMessages.addAll(messages);
                totalRetrieved += messages.size();
            }
        }
        Assert.assertEquals(totalRetrieved, NUM_MESSAGES);
    }

    private BroadcastMessage createBroadcastMessage() {
        counter.addAndGet(1);
        return new BroadcastMessage("" + counter, counter);
    }

}
