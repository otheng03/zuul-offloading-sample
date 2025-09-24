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
package com.netflix.zuul.sample;

import com.netflix.zuul.sample.filters.BatchProcessingFilter;
import com.netflix.zuul.sample.filters.DatabaseLookupFilter;
import com.netflix.zuul.sample.filters.ExternalServiceCallFilter;
import com.netflix.zuul.sample.filters.HeavyComputationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstration of async patterns used in Zuul 2 filters for offloading work.
 * This demonstrates the filter patterns without a full Zuul server.
 */
public class OffloadingSampleServer {

    private static final Logger logger = LoggerFactory.getLogger(OffloadingSampleServer.class);

    public static void main(String[] args) {
        logger.info("Zuul 2 Async Filter Offloading Sample");
        logger.info("====================================");

        try {
            // Create instances of our async pattern demonstrations
            DatabaseLookupFilter dbFilter = new DatabaseLookupFilter();
            HeavyComputationFilter computeFilter = new HeavyComputationFilter();
            ExternalServiceCallFilter serviceFilter = new ExternalServiceCallFilter();
            BatchProcessingFilter batchFilter = new BatchProcessingFilter();

            logger.info("Async pattern demonstrations created:");
            logger.info("- DatabaseLookupFilter: I/O thread pool offloading");
            logger.info("- HeavyComputationFilter: CPU-bound work on custom thread pools");
            logger.info("- ExternalServiceCallFilter: HTTP client operations with timeouts");
            logger.info("- BatchProcessingFilter: Background scheduled task processing");

            logger.info("");
            logger.info("Testing async patterns...");

            // Test database lookup pattern
            testDatabaseLookup(dbFilter);

            // Test heavy computation pattern
            testHeavyComputation(computeFilter);

            // Test external service call pattern
            testExternalServiceCall(serviceFilter);

            // Test batch processing pattern
            testBatchProcessing(batchFilter);

            logger.info("");
            logger.info("All async patterns tested successfully!");

            logger.info("");
            logger.info("Key RxJava 2 patterns demonstrated:");
            logger.info("1. Observable.fromCallable(() -> blockingOperation()).subscribeOn(Schedulers.io())");
            logger.info("2. Custom thread pools: Schedulers.from(customThreadPool)");
            logger.info("3. Timeout handling: .timeout(duration, TimeUnit.SECONDS)");
            logger.info("4. Error handling: .onErrorReturn(error -> fallbackValue)");
            logger.info("5. Background tasks: ScheduledExecutorService for batch processing");

            // Cleanup
            batchFilter.shutdown();
            computeFilter.shutdown();

            logger.info("");
            logger.info("Sample demonstration completed successfully!");

        } catch (Exception e) {
            logger.error("Error during demonstration", e);
            System.exit(1);
        }
    }

    private static void testDatabaseLookup(DatabaseLookupFilter filter) {
        logger.info("Testing database lookup pattern...");
        try {
            String result = filter.simulateDatabaseLookup("user123").blockingFirst();
            logger.info("Database lookup result: {}", result);
        } catch (Exception e) {
            logger.error("Database lookup test failed", e);
        }
    }

    private static void testHeavyComputation(HeavyComputationFilter filter) {
        logger.info("Testing heavy computation pattern...");
        try {
            String result = filter.simulateHeavyComputation("test data").blockingFirst();
            logger.info("Heavy computation result: {}", result);
        } catch (Exception e) {
            logger.error("Heavy computation test failed", e);
        }
    }

    private static void testExternalServiceCall(ExternalServiceCallFilter filter) {
        logger.info("Testing external service call pattern...");
        try {
            String result = filter.simulateExternalServiceCall().blockingFirst();
            logger.info("External service call result: {}", result);
        } catch (Exception e) {
            logger.error("External service call test failed", e);
        }
    }

    private static void testBatchProcessing(BatchProcessingFilter filter) {
        logger.info("Testing batch processing pattern...");
        try {
            // Simulate some requests
            for (int i = 0; i < 5; i++) {
                filter.processRequest("/api/test" + i);
            }

            // Wait a bit to see batch processing
            Thread.sleep(2000);
            logger.info("Batch processing pattern demonstrated");
        } catch (Exception e) {
            logger.error("Batch processing test failed", e);
        }
    }
}