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
package org.example.zuul.sample.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example demonstrating batch processing and background task scheduling in Zuul 2.
 * Collects request metrics and processes them in batches on a separate thread.
 */
public class BatchProcessingFilter {

    private static final Logger logger = LoggerFactory.getLogger(BatchProcessingFilter.class);

    // Metrics collection
    private final ConcurrentHashMap<String, AtomicLong> pathCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService batchProcessor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "batch-processor");
            t.setDaemon(true);
            return t;
        }
    );

    public BatchProcessingFilter() {
        // Schedule batch processing every 5 seconds for demo
        batchProcessor.scheduleAtFixedRate(this::processBatch, 2, 5, TimeUnit.SECONDS);
        logger.info("Batch processing scheduled every 5 seconds");
    }

    /**
     * Simulates processing a request and collecting metrics.
     */
    public void processRequest(String path) {
        // Collect metrics (non-blocking operation)
        pathCounts.computeIfAbsent(path, k -> new AtomicLong(0)).incrementAndGet();

        logger.debug("Processed request for path: {}", path);
    }

    /**
     * Background batch processing method that runs on a schedule
     */
    private void processBatch() {
        try {
            if (pathCounts.isEmpty()) {
                logger.debug("No metrics to process in this batch");
                return;
            }

            logger.info("Processing batch - current path counts:");

            // Process accumulated metrics
            pathCounts.forEach((path, count) -> {
                long currentCount = count.getAndSet(0); // Reset counter
                if (currentCount > 0) {
                    logger.info("  Path: {} - Requests: {}", path, currentCount);

                    // Here you could:
                    // - Send metrics to monitoring system
                    // - Update database statistics
                    // - Trigger alerts if thresholds exceeded
                    processPathMetrics(path, currentCount);
                }
            });

            logger.info("Batch processing completed");

        } catch (Exception e) {
            logger.error("Error during batch processing", e);
        }
    }

    private void processPathMetrics(String path, long requestCount) {
        // Simulate processing metrics
        if (requestCount > 3) {
            logger.warn("High traffic detected for path: {} ({} requests)", path, requestCount);
        }

        // In a real implementation, you might:
        // - Send to metrics aggregation service
        // - Update caches
        // - Trigger auto-scaling decisions
        // - Generate reports
    }

    /**
     * Cleanup method for graceful shutdown
     */
    public void shutdown() {
        logger.info("Shutting down batch processor");
        batchProcessor.shutdown();
        try {
            if (!batchProcessor.awaitTermination(10, TimeUnit.SECONDS)) {
                batchProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}