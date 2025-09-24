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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Example demonstrating CPU-intensive computation pattern in Zuul 2.
 * Uses a custom thread pool for CPU-bound tasks to avoid blocking Netty threads.
 */
public class HeavyComputationFilter {

    private static final Logger logger = LoggerFactory.getLogger(HeavyComputationFilter.class);

    // Custom thread pool sized for CPU-bound tasks
    private static final ThreadPoolExecutor COMPUTATION_POOL = new ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(), // Core pool size
        Runtime.getRuntime().availableProcessors(), // Max pool size
        60L, TimeUnit.SECONDS,                      // Keep alive time
        new LinkedBlockingQueue<>(1000),            // Work queue
        r -> {
            Thread t = new Thread(r, "computation-pool-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        }
    );

    /**
     * Demonstrates the async pattern for CPU-intensive computation.
     */
    public Observable<String> simulateHeavyComputation(String input) {
        return Observable
            .fromCallable(() -> {
                logger.info("Starting heavy computation for input: {}", input);

                // Simulate CPU-intensive work
                String result = performExpensiveComputation(input);

                logger.info("Completed computation for input: {}", input);
                return result;
            })
            .subscribeOn(Schedulers.from(COMPUTATION_POOL)) // Use custom thread pool
            .timeout(10, TimeUnit.SECONDS)
            .doOnError(error -> {
                logger.error("Heavy computation failed for input {}: {}", input, error.getMessage());
            })
            .onErrorReturn(error -> {
                return "COMPUTATION_FAILED";
            });
    }

    private String performExpensiveComputation(String input) {
        try {
            // Simulate expensive computation - hash calculation with multiple rounds
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = input != null ? input : "default-data";

            // Perform multiple rounds of hashing to simulate CPU work
            for (int i = 0; i < 10000; i++) {
                data = bytesToHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
            }

            return data.substring(0, 16); // Return first 16 characters
        } catch (Exception e) {
            throw new RuntimeException("Computation failed", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public void shutdown() {
        logger.info("Shutting down computation thread pool");
        COMPUTATION_POOL.shutdown();
        try {
            if (!COMPUTATION_POOL.awaitTermination(10, TimeUnit.SECONDS)) {
                COMPUTATION_POOL.shutdownNow();
            }
        } catch (InterruptedException e) {
            COMPUTATION_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}