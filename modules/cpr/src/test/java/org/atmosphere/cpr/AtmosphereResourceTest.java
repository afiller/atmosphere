/*
 * Copyright 2014 Jean-Francois Arcand
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

import org.atmosphere.container.BlockingIOCometSupport;
import org.atmosphere.handler.AbstractReflectorAtmosphereHandler;
import org.atmosphere.websocket.WebSocket;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.ApplicationConfig.SUSPENDED_ATMOSPHERE_RESOURCE_UUID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;

public class AtmosphereResourceTest {
    private AtmosphereFramework framework;

    @BeforeMethod
    public void create() throws Throwable {
        framework = new AtmosphereFramework();
        framework.setAsyncSupport(new BlockingIOCometSupport(framework.getAtmosphereConfig()));
        framework.init(new ServletConfig() {
            @Override
            public String getServletName() {
                return "void";
            }

            @Override
            public ServletContext getServletContext() {
                return mock(ServletContext.class);
            }

            @Override
            public String getInitParameter(String name) {
                return null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return null;
            }
        });
    }


    @Test
    public void testUUID() throws IOException, ServletException {
        framework.addAtmosphereHandler("/a", new AbstractReflectorAtmosphereHandler() {
            @Override
            public void onRequest(AtmosphereResource resource) throws IOException {
            }

            @Override
            public void destroy() {
            }
        });

        AtmosphereRequest request = new AtmosphereRequest.Builder().pathInfo("/a").build();

        final AtomicReference<String> e = new AtomicReference<String>();
        final AtomicReference<String> e2 = new AtomicReference<String>();

        framework.interceptor(new AtmosphereInterceptor() {
            @Override
            public void configure(AtmosphereConfig config) {
            }

            @Override
            public Action inspect(AtmosphereResource r) {
                e.set(r.uuid());
                e2.set(r.getResponse().getHeader(HeaderConfig.X_ATMOSPHERE_TRACKING_ID));
                return Action.CANCELLED;
            }

            @Override
            public void postInspect(AtmosphereResource r) {
            }
        });
        framework.doCometSupport(request, AtmosphereResponse.newInstance());

        assertEquals(e.get(), e2.get());
    }

    @Test
    public void testCancelParentUUID() throws IOException, ServletException, InterruptedException {
        framework.addAtmosphereHandler("/a", new AbstractReflectorAtmosphereHandler() {
            @Override
            public void onRequest(AtmosphereResource resource) throws IOException {
            }

            @Override
            public void destroy() {
            }
        });

        final AtmosphereRequest parentRequest = new AtmosphereRequest.Builder().pathInfo("/a").queryString(HeaderConfig.WEBSOCKET_X_ATMOSPHERE_TRANSPORT).build();
        final CountDownLatch suspended = new CountDownLatch(1);

        framework.interceptor(new AtmosphereInterceptor() {
            @Override
            public void configure(AtmosphereConfig config) {
            }

            @Override
            public Action inspect(AtmosphereResource r) {
                try {
                    r.getBroadcaster().addAtmosphereResource(r);
                    if (suspended.getCount() == 1) {
                        r.suspend();
                        return Action.SUSPEND;
                    } else {
                        return Action.CONTINUE;
                    }
                } finally {
                    suspended.countDown();
                }
            }

            @Override
            public void postInspect(AtmosphereResource r) {
            }
        });

        new Thread() {
            public void run() {
                try {
                    framework.doCometSupport(parentRequest, AtmosphereResponse.newInstance().request(parentRequest));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ServletException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        suspended.await();
        Map<String, Object> m = new HashMap<String, Object>();
        m.put(SUSPENDED_ATMOSPHERE_RESOURCE_UUID, parentRequest.resource().uuid());

        AtmosphereRequest request = new AtmosphereRequest.Builder().attributes(m).pathInfo("/a").queryString(HeaderConfig.WEBSOCKET_X_ATMOSPHERE_TRANSPORT).build();
        request.setAttribute(FrameworkConfig.WEBSOCKET_MESSAGE, "true");

        framework.doCometSupport(request, AtmosphereResponse.newInstance().request(request));

        AtmosphereResource r = parentRequest.resource();
        Broadcaster b = r.getBroadcaster();

        assertEquals(b.getAtmosphereResources().size(), 1);

        AtmosphereResourceImpl.class.cast(r).cancel();

        assertEquals(b.getAtmosphereResources().size(), 0);

    }

    @Test
    public void testCloseResponseOutputStream() throws IOException {
        AtmosphereResponse response = AtmosphereResponse.newInstance();
        AsyncIOWriter writer = mock(AsyncIOWriter.class);
        AsyncIOWriter wswriter = mock(WebSocket.class);

        response.asyncIOWriter(writer);
        ServletOutputStream sos = response.getOutputStream();
        sos.close();

        verify(writer, times(1)).close(response);
        reset(writer);

        response.asyncIOWriter(wswriter);
        sos = response.getOutputStream();
        sos.close();
        verify(wswriter, times(0)).close(response);
    }

    @Test
    public void testCloseResponseWriter() throws IOException {
        AtmosphereResponse response = AtmosphereResponse.newInstance();
        AsyncIOWriter writer = mock(AsyncIOWriter.class);
        AsyncIOWriter wswriter = mock(WebSocket.class);

        response.asyncIOWriter(writer);
        PrintWriter pw = response.getWriter();
        pw.close();

        verify(writer, times(1)).close(response);
        reset(writer);

        response.asyncIOWriter(wswriter);
        pw = response.getWriter();
        pw.close();
        verify(wswriter, times(0)).close(response);
    }

}
