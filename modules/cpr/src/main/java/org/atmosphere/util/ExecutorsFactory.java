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
package org.atmosphere.util;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stateless Factory to create {@link ExecutorService} used in all Atmosphere Component. By default they are
 * shared amongst all component. To change the behavior, add {
 * link ApplicationConfig#BROADCASTER_SHARABLE_THREAD_POOLS}
 *
 * @author Jeanfrancois Arcand
 */
public class ExecutorsFactory {

    private final static Logger logger = LoggerFactory.getLogger(ExecutorsFactory.class);
    public final static int DEFAULT_ASYNC_THREAD = 200;
    public final static int DEFAULT_MESSAGE_THREAD = -1;
    public final static int DEFAULT_KEEP_ALIVE = 30;

    /**
     * Create an {@link ExecutorService} to be used for dispatching messages, not I/O events.
     *
     * @param config the {@link AtmosphereConfig}
     * @param name   a name to use if shared is false.
     * @return {@link ExecutorService}
     */
    public static ExecutorService getMessageDispatcher(final AtmosphereConfig config, final String name) {
        final boolean shared = config.framework().isShareExecutorServices();

        boolean isExecutorShared = shared ? true : false;
        if (!shared || config.properties().get("executorService") == null) {
            int numberOfMessageProcessingThread = DEFAULT_MESSAGE_THREAD;
            String s = config.getInitParameter(ApplicationConfig.BROADCASTER_MESSAGE_PROCESSING_THREADPOOL_MAXSIZE);
            if (s != null) {
                numberOfMessageProcessingThread = Integer.parseInt(s);
            }

            if (isExecutorShared && numberOfMessageProcessingThread == 1) {
                logger.warn("Not enough numberOfMessageProcessingThread for a shareable thread pool {}, " +
                        "Setting it to a newCachedThreadPool", numberOfMessageProcessingThread);
                numberOfMessageProcessingThread = -1;
            }

            ThreadPoolExecutor messageService;
            if (numberOfMessageProcessingThread == -1) {
                messageService = (ThreadPoolExecutor) Executors.newCachedThreadPool(new ThreadFactory() {

                    private final AtomicInteger count = new AtomicInteger();

                    @Override
                    public Thread newThread(final Runnable runnable) {
                        Thread t = new Thread(runnable, (shared ? "Atmosphere-Shared" : name) + "-DispatchOp-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
            } else {
                messageService = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfMessageProcessingThread, new ThreadFactory() {

                    private final AtomicInteger count = new AtomicInteger();

                    @Override
                    public Thread newThread(final Runnable runnable) {
                        Thread t = new Thread(runnable, (shared ? "Atmosphere-Shared" : name) + "-DispatchOp-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
            }

            keepAliveThreads(messageService, config);

            if (shared) {
                config.properties().put("executorService", messageService);
            }
            return messageService;
        } else {
            return (ExecutorService) config.properties().get("executorService");
        }
    }

    private static void keepAliveThreads(ThreadPoolExecutor e, AtmosphereConfig config) {
        int keepAlive = DEFAULT_KEEP_ALIVE;
        String s = config.getInitParameter(ApplicationConfig.EXECUTORFACTORY_KEEP_ALIVE);
        if (s != null) {
            keepAlive = Integer.parseInt(s);
        }
        e.setKeepAliveTime(keepAlive, TimeUnit.SECONDS);
        e.allowCoreThreadTimeOut(config.getInitParameter(ApplicationConfig.ALLOW_CORE_THREAD_TIMEOUT, true));
    }

    /**
     * Create an {@link ExecutorService} to be used for dispatching I/O events.
     *
     * @param config the {@link AtmosphereConfig}
     * @param name   a name to use if shared is false.
     * @return {@link ExecutorService}
     */
    public static ExecutorService getAsyncOperationExecutor(final AtmosphereConfig config, final String name) {
        final boolean shared = config.framework().isShareExecutorServices();

        boolean isAsyncExecutorShared = shared ? true : false;
        if (!shared || config.properties().get("asyncWriteService") == null) {
            int numberOfAsyncThread = DEFAULT_ASYNC_THREAD;
            String s = config.getInitParameter(ApplicationConfig.BROADCASTER_ASYNC_WRITE_THREADPOOL_MAXSIZE);
            if (s != null) {
                numberOfAsyncThread = Integer.parseInt(s);
            }

            if (isAsyncExecutorShared && numberOfAsyncThread == 1) {
                logger.warn("Not enough numberOfAsyncThread for a shareable thread pool {}, " +
                        "Setting it to a newCachedThreadPool", numberOfAsyncThread);
                numberOfAsyncThread = -1;
            }

            ThreadPoolExecutor asyncWriteService;
            if (numberOfAsyncThread == -1) {
                asyncWriteService = (ThreadPoolExecutor) Executors.newCachedThreadPool(new ThreadFactory() {

                    private final AtomicInteger count = new AtomicInteger();

                    @Override
                    public Thread newThread(final Runnable runnable) {
                        Thread t = new Thread(runnable, (shared ? "Atmosphere-Shared" : name) + "-AsyncOp-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
            } else {
                asyncWriteService = (ThreadPoolExecutor) Executors.newFixedThreadPool(numberOfAsyncThread, new ThreadFactory() {

                    private final AtomicInteger count = new AtomicInteger();

                    @Override
                    public Thread newThread(final Runnable runnable) {
                        Thread t = new Thread(runnable, (shared ? "Atmosphere-Shared" : name) + "-AsyncOp-" + count.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                });
            }

            keepAliveThreads(asyncWriteService, config);

            if (shared) {
                config.properties().put("asyncWriteService", asyncWriteService);
            }
            return asyncWriteService;
        } else {
            return (ExecutorService) config.properties().get("asyncWriteService");
        }
    }

    /**
     * Create a {@link ScheduledExecutorService} used ot schedule I/O and non I/O events.
     *
     * @param config the {@link AtmosphereConfig}
     * @return {@link ScheduledExecutorService}
     */
    public static ScheduledExecutorService getScheduler(final AtmosphereConfig config) {
        final boolean shared = config.framework().isShareExecutorServices();

        if (!shared || config.properties().get("scheduler") == null) {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {

                private final AtomicInteger count = new AtomicInteger();

                @Override
                public Thread newThread(final Runnable runnable) {
                    Thread t = new Thread(runnable, "Atmosphere-Scheduler-" + count.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

            if (shared) {
                config.properties().put("scheduler", scheduler);
            }

            keepAliveThreads((ThreadPoolExecutor)scheduler, config);

            return scheduler;
        } else {
            return (ScheduledExecutorService) config.properties().get("scheduler");
        }
    }
}
