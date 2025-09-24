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

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating database lookup operation in Zuul 2.
 * Uses RxJava 2 to offload the blocking database call to an I/O thread pool.
 * This demonstrates the pattern that would be used in a real Zuul filter.
 */
public class DatabaseLookupFilter {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseLookupFilter.class);

    /**
     * Demonstrates the async pattern for database lookups.
     */
    public Observable<String> simulateDatabaseLookup(String userId) {
        return Observable
            .fromCallable(() -> {
                // Simulate database lookup - this would be a real DB call in practice
                logger.info("Looking up user permissions for user: {}", userId);

                // Simulate database latency
                Thread.sleep(100);

                // Simulate database result
                String userRole = "user123".equals(userId) ? "ADMIN" : "USER";

                logger.info("User {} has role: {}", userId, userRole);
                return userRole;
            })
            .subscribeOn(Schedulers.io()) // Offload to I/O thread pool
            .timeout(5, TimeUnit.SECONDS) // Add timeout protection
            .doOnError(error -> {
                logger.error("Database lookup failed for user {}: {}", userId, error.getMessage());
            })
            .onErrorReturn(error -> {
                // Return fallback value on error
                logger.warn("Returning fallback role for user: {}", userId);
                return "UNKNOWN";
            });
    }
}