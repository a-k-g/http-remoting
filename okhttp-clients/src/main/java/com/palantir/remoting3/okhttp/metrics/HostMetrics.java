/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp.metrics;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

/**
 * Records per-target-host HTTP response code metrics in a {@link MetricRegistry}.
 */
public final class HostMetrics {

    public static final String CLIENT_RESPONSE_METRIC_NAME_SUFFIX = ".client.response";
    public static final String HOSTNAME_TAG = "hostname";
    public static final String FAMILY_TAG = "family";

    private final Meter informational;
    private final Meter successful;
    private final Meter redirection;
    private final Meter clientError;
    private final Meter serverError;
    private final Meter other;

    /** Creates a metrics registry for calls from the given service to the given host. */
    public HostMetrics(TaggedMetricRegistry registry, String serviceName, String hostname) {
        informational = registry.meter(name(serviceName, hostname, "informational"));
        successful = registry.meter(name(serviceName, hostname, "successful"));
        redirection = registry.meter(name(serviceName, hostname, "redirection"));
        clientError = registry.meter(name(serviceName, hostname, "client-error"));
        serverError = registry.meter(name(serviceName, hostname, "server-error"));
        other = registry.meter(name(serviceName, hostname, "other"));
    }

    private static MetricName name(String serviceName, String hostname, String family) {
        return MetricName.builder()
                .safeName(serviceName + CLIENT_RESPONSE_METRIC_NAME_SUFFIX)
                .putSafeTags(HOSTNAME_TAG, hostname)
                .putSafeTags(FAMILY_TAG, family)
                .build();
    }

    /**
     * Records that an HTTP call from the configured service to the configured host (see constructor) yielded the given
     * HTTP status code.
     */
    public void record(int statusCode) {
        // Explicitly not using javax.ws.rs.core.Response API since it's incompatible across versions.
        switch (statusCode / 100) {
            case 1:
                informational.mark();
                break;
            case 2:
                successful.mark();
                break;
            case 3:
                redirection.mark();
                break;
            case 4:
                clientError.mark();
                break;
            case 5:
                serverError.mark();
                break;
            default:
                other.mark();
                break;
        }
    }
}
