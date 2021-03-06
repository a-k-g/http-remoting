/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting3.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.palantir.remoting3.clients.ClientConfiguration;
import feign.RetryableException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


public final class JaxRsClientFailoverTest extends TestBase {

    @Rule
    public final MockWebServer server1 = new MockWebServer();
    @Rule
    public final MockWebServer server2 = new MockWebServer();

    private TestService proxy;

    @Before
    public void before() throws Exception {
        proxy = JaxRsClient.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://localhost:" + server1.getPort(),
                                "http://localhost:" + server2.getPort()))
                        .maxNumRetries(1)
                        .build());
    }

    @Test
    public void testConnectionError_performsFailover() throws Exception {
        server1.shutdown();
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        assertThat(proxy.string(), is("foo"));
    }

    @Test
    public void testConnectionError_whenOneCallFailsThenSubsequentNewCallsCanStillSucceed() throws Exception {
        // Call fails when servers are down.
        server1.shutdown();
        server2.shutdown();
        try {
            proxy.string();
            fail();
        } catch (RetryableException e) {
            assertThat(e.getMessage(), startsWith("Could not connect to any of the configured URLs: "));
        }

        // Subsequent call (with the same proxy instance) succeeds.
        MockWebServer anotherServer1 = new MockWebServer(); // Not a @Rule so we can control start/stop/port explicitly
        anotherServer1.start(server1.getPort());
        anotherServer1.enqueue(new MockResponse().setBody("\"foo\""));
        assertThat(proxy.string(), is("foo"));
        anotherServer1.shutdown();
    }

    @Test
    public void testConnectionError_performsFailoverOnDnsFailure() throws Exception {
        server1.enqueue(new MockResponse().setBody("\"foo\""));

        TestService bogusHostProxy = JaxRsClient.create(TestService.class, AGENT,
                ClientConfiguration.builder()
                        .from(createTestConfig(
                                "http://foo-bar-bogus-host.unresolvable:80",
                                "http://localhost:" + server1.getPort()))
                        .maxNumRetries(1)
                        .build());
        assertThat(bogusHostProxy.string(), is("foo"));
        assertThat(server1.getRequestCount(), is(1));
    }

    @Test
    public void testQosError_performsFailover() throws Exception {
        server1.enqueue(new MockResponse().setResponseCode(503));
        server1.enqueue(new MockResponse().setBody("\"foo\""));
        server2.enqueue(new MockResponse().setBody("\"foo\""));

        assertThat(proxy.string(), is("foo"));
    }
}
