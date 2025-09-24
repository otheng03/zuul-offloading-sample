/**
 * Copyright 2025 Netflix, Inc.
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
package com.netflix.zuul.sample.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating external HTTP service call pattern in Zuul 2.
 * Demonstrates proper offloading of blocking I/O operations.
 */
public class ExternalServiceCallFilter {

    private static final Logger logger = LoggerFactory.getLogger(ExternalServiceCallFilter.class);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExternalServiceCallFilter() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Demonstrates the async pattern for external service calls.
     */
    public Observable<String> simulateExternalServiceCall() {
        return Observable
            .fromCallable(() -> {
                logger.info("Making external service call...");

                // Make HTTP call to external service (using JSONPlaceholder as example)
                Request httpRequest = new Request.Builder()
                    .url("https://jsonplaceholder.typicode.com/users/1")
                    .header("User-Agent", "Zuul-Gateway/2.0")
                    .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userData = objectMapper.readValue(responseBody, Map.class);

                        logger.info("Successfully retrieved external data");
                        String userName = (String) userData.get("name");
                        return "User: " + userName;
                    } else {
                        throw new RuntimeException("External service call failed with status: " + response.code());
                    }
                }
            })
            .subscribeOn(Schedulers.io()) // Offload to I/O thread pool
            .timeout(8, TimeUnit.SECONDS) // Overall timeout
            .doOnError(error -> {
                logger.error("External service call failed: {}", error.getMessage());
            })
            .onErrorReturn(error -> {
                // Gracefully handle failures - continue processing without enrichment
                return "Service unavailable";
            });
    }
}