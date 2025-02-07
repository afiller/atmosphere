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
package org.atmosphere.cpr;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * A Factory used to manage {@link AtmosphereResource} instances. You can use this factory to create, remove and find
 * {@link AtmosphereResource} instances that are associated with one or several {@link Broadcaster}s.
 *
 * @author Jeanfrancois Arcand
 */
public abstract class AtmosphereResourceFactory {

    private static AtmosphereResourceFactory factory;

    /**
     * Package-private method used by DefaultAtmosphereResourceFactory to store
     * the current AtmosphereResourceFactory instance.
     *
     * @param atmosphereResourceFactory the current AtmosphereResourceFactory instance
     */
    static void updateAtmosphereResourceFactory(AtmosphereResourceFactory atmosphereResourceFactory) {
        factory = atmosphereResourceFactory;
    }

    /**
     * Use {@link AtmosphereConfig#resourcesFactory() instead}
     *
     * @deprecated Use {@link org.atmosphere.cpr.AtmosphereConfig#resourcesFactory()}
     */
    @Deprecated
    public static AtmosphereResourceFactory getDefault() {
        return factory;
    }

    public abstract void configure(AtmosphereConfig config);

    /**
     * Create an {@link AtmosphereResourceImpl}
     *
     * @param config  an {@link AtmosphereConfig}
     * @param request an {@link AtmosphereResponse}
     * @param a       {@link AsyncSupport}
     * @return an {@link AtmosphereResourceImpl}
     */
    public abstract AtmosphereResource create(AtmosphereConfig config,
                              AtmosphereRequest request,
                              AtmosphereResponse response,
                              AsyncSupport<?> a);

    /**
     * Create an {@link AtmosphereResourceImpl}.
     *
     * @param config      an {@link AtmosphereConfig}
     * @param broadcaster a {@link Broadcaster}
     * @param response    an {@link AtmosphereResponse}
     * @param a           {@link AsyncSupport}
     * @param handler     an {@link AtmosphereHandler}
     * @return an {@link AtmosphereResourceImpl}
     */
    public abstract AtmosphereResource create(AtmosphereConfig config,
                              Broadcaster broadcaster,
                              AtmosphereRequest request,
                              AtmosphereResponse response,
                              AsyncSupport<?> a,
                              AtmosphereHandler handler);

    /**
     * Create an {@link AtmosphereResourceImpl}.
     *
     * @param config      an {@link AtmosphereConfig}
     * @param broadcaster a {@link Broadcaster}
     * @param response    an {@link AtmosphereResponse}
     * @param a           {@link AsyncSupport}
     * @param handler     an {@link AtmosphereHandler}
     * @param t           an {@link org.atmosphere.cpr.AtmosphereResource.TRANSPORT}
     * @return an {@link AtmosphereResourceImpl}
     */
    public abstract AtmosphereResource create(AtmosphereConfig config,
                              Broadcaster broadcaster,
                              AtmosphereRequest request,
                              AtmosphereResponse response,
                              AsyncSupport<?> a,
                              AtmosphereHandler handler,
                              AtmosphereResource.TRANSPORT t);

    /**
     * Create an {@link AtmosphereResourceImpl}.
     *
     * @param config      an {@link AtmosphereConfig}
     * @param broadcaster a {@link Broadcaster}
     * @param response    an {@link AtmosphereResponse}
     * @param a           {@link AsyncSupport}
     * @param handler     an {@link AtmosphereHandler}
     * @return an {@link AtmosphereResourceImpl}
     */
    public abstract AtmosphereResource create(AtmosphereConfig config,
                              Broadcaster broadcaster,
                              AtmosphereResponse response,
                              AsyncSupport<?> a,
                              AtmosphereHandler handler);

    public abstract AtmosphereResource create(AtmosphereConfig config,
                              Broadcaster broadcaster,
                              AtmosphereResponse response,
                              AsyncSupport<?> a,
                              AtmosphereHandler handler,
                              AtmosphereResource.TRANSPORT t);

    /**
     * Create an {@link AtmosphereResourceImpl}.
     *
     * @param config   an {@link AtmosphereConfig}
     * @param response an {@link AtmosphereResponse}
     * @param a        {@link AsyncSupport}
     * @return an {@link AtmosphereResourceImpl}
     */
    public abstract AtmosphereResource create(AtmosphereConfig config,
                              AtmosphereResponse response,
                              AsyncSupport<?> a);

    /**
     * Create an {@link AtmosphereResource} associated with the uuid.
     *
     * @param config an {@link AtmosphereConfig}
     * @param uuid   a String representing a UUID
     * @return
     */
    public abstract AtmosphereResource create(AtmosphereConfig config, String uuid);

    /**
     * Create an {@link AtmosphereResource} associated with the uuid.
     *
     * @param config  an {@link AtmosphereConfig}
     * @param uuid    a String representing a UUID
     * @param request a {@link org.atmosphere.cpr.AtmosphereRequest}
     * @return
     */
    public abstract AtmosphereResource create(AtmosphereConfig config, String uuid, AtmosphereRequest request);

    /**
     * Remove the {@link AtmosphereResource} from all instances of {@link Broadcaster}.
     *
     * @param uuid the {@link org.atmosphere.cpr.AtmosphereResource#uuid()}
     * @return the {@link AtmosphereResource}, or null if not found.
     */
    public abstract AtmosphereResource remove(String uuid);

    /**
     * Find an {@link AtmosphereResource} based on its {@link org.atmosphere.cpr.AtmosphereResource#uuid()}.
     *
     * @param uuid the {@link org.atmosphere.cpr.AtmosphereResource#uuid()}
     * @return the {@link AtmosphereResource}, or null if not found.
     */
    public abstract AtmosphereResource find(String uuid);

    /**
     * Return all {@link Broadcaster} associated with a {@link AtmosphereResource#uuid}, e.g for which
     * {@link Broadcaster#addAtmosphereResource(AtmosphereResource)} has been called. Note that this
     * method is not synchronized and may not return all the {@link Broadcaster} in case
     * {@link Broadcaster#addAtmosphereResource(AtmosphereResource)} is being called concurrently.
     *
     * @param uuid the {@link org.atmosphere.cpr.AtmosphereResource#uuid()}
     * @return all {@link Broadcaster} associated with a {@link AtmosphereResource#uuid}
     */
    public abstract Set<Broadcaster> broadcasters(String uuid);

    /**
     * Register an {@link AtmosphereResource} for being a candidate to {@link #find(String)} operation.
     *
     * @param r {@link AtmosphereResource}
     */
    public abstract void registerUuidForFindCandidate(AtmosphereResource r);

    /**
     * Un register an {@link AtmosphereResource} for being a candidate to {@link #find(String)} operation.
     *
     * @param r {@link AtmosphereResource}
     */
    public abstract void unRegisterUuidForFindCandidate(AtmosphereResource r);

    public abstract void destroy();

    public abstract ConcurrentMap<String, AtmosphereResource> resources();

    public abstract Collection<AtmosphereResource> findAll();
}
