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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Simple HTTP server to demonstrate the async filter patterns with real HTTP requests.
 * This allows testing with curl commands as requested.
 */
public class SimpleHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleHttpServer.class);

    private final DatabaseLookupFilter dbFilter;
    private final HeavyComputationFilter computeFilter;
    private final ExternalServiceCallFilter serviceFilter;
    private final BatchProcessingFilter batchFilter;

    public SimpleHttpServer() {
        this.dbFilter = new DatabaseLookupFilter();
        this.computeFilter = new HeavyComputationFilter();
        this.serviceFilter = new ExternalServiceCallFilter();
        this.batchFilter = new BatchProcessingFilter();
    }

    public static void main(String[] args) throws Exception {
        SimpleHttpServer server = new SimpleHttpServer();
        server.start();
    }

    public void start() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Register handlers for different paths
        server.createContext("/api/", new ApiHandler());
        server.setExecutor(Executors.newCachedThreadPool());

        server.start();

        logger.info("Zuul 2 Async Filter Pattern Demo Server Started");
        logger.info("===============================================");
        logger.info("Server is running on http://localhost:8080");
        logger.info("");
        logger.info("Test the async filter patterns with these curl commands:");
        logger.info("");
        logger.info("1. Database Lookup Filter:");
        logger.info("   curl -H \"X-User-ID: user123\" http://localhost:8080/api/test");
        logger.info("");
        logger.info("2. Heavy Computation Filter:");
        logger.info("   curl -H \"X-Compute-Hash: true\" -d \"test data\" http://localhost:8080/api/compute");
        logger.info("");
        logger.info("3. External Service Call Filter:");
        logger.info("   curl -H \"X-Enrich-Data: true\" http://localhost:8080/api/enrich");
        logger.info("");
        logger.info("4. Test all filters on a single request:");
        logger.info("   curl -H \"X-User-ID: user123\" -H \"X-Compute-Hash: true\" -H \"X-Enrich-Data: true\" \\");
        logger.info("        -d \"test data\" http://localhost:8080/api/all");
        logger.info("");
        logger.info("Check the response headers to see the results of each filter.");
        logger.info("Press Ctrl+C to stop the server");

        // Keep the main thread alive
        Thread.currentThread().join();
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            Map<String, String> headers = getRequestHeaders(exchange);
            String body = getRequestBody(exchange);

            logger.info("Processing request: {} {}", exchange.getRequestMethod(), path);

            // Always process batch filter for metrics
            batchFilter.processRequest(path);

            Map<String, String> responseHeaders = new HashMap<>();
            StringBuilder response = new StringBuilder();

            response.append("Zuul 2 Async Filter Pattern Demo Response\n");
            response.append("==========================================\n");
            response.append("Path: ").append(path).append("\n");
            response.append("Method: ").append(exchange.getRequestMethod()).append("\n\n");

            // Process filters based on headers
            processFilters(headers, body, responseHeaders, response);

            // Set response headers
            for (Map.Entry<String, String> header : responseHeaders.entrySet()) {
                exchange.getResponseHeaders().add(header.getKey(), header.getValue());
            }

            // Send response
            byte[] responseBytes = response.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }

            logger.info("Request completed: {} {}", exchange.getRequestMethod(), path);
        }

        private Map<String, String> getRequestHeaders(HttpExchange exchange) {
            Map<String, String> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach((key, values) -> {
                if (!values.isEmpty()) {
                    // Store headers in uppercase for case-insensitive lookup
                    headers.put(key.toUpperCase(), values.get(0));
                }
            });
            return headers;
        }

        private String getRequestBody(HttpExchange exchange) throws IOException {
            if (exchange.getRequestBody() != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            }
            return "";
        }

        private void processFilters(Map<String, String> headers, String body,
                                  Map<String, String> responseHeaders, StringBuilder response) {

            // Database Lookup Filter
            if (headers.containsKey("X-USER-ID")) {
                String userId = headers.get("X-USER-ID");
                try {
                    String userRole = dbFilter.simulateDatabaseLookup(userId).blockingFirst();
                    responseHeaders.put("X-User-Role", userRole);
                    response.append("Database Lookup Result: ").append(userRole).append("\n");
                } catch (Exception e) {
                    responseHeaders.put("X-User-Role", "ERROR");
                    response.append("Database Lookup Result: ERROR\n");
                    logger.error("Database lookup failed", e);
                }
            }

            // Heavy Computation Filter
            if ("true".equalsIgnoreCase(headers.get("X-COMPUTE-HASH"))) {
                try {
                    String hash = computeFilter.simulateHeavyComputation(body).blockingFirst();
                    responseHeaders.put("X-Computed-Hash", hash);
                    response.append("Heavy Computation Result: ").append(hash).append("\n");
                } catch (Exception e) {
                    responseHeaders.put("X-Computed-Hash", "ERROR");
                    response.append("Heavy Computation Result: ERROR\n");
                    logger.error("Heavy computation failed", e);
                }
            }

            // External Service Call Filter
            if ("true".equalsIgnoreCase(headers.get("X-ENRICH-DATA"))) {
                try {
                    String enrichmentData = serviceFilter.simulateExternalServiceCall().blockingFirst();
                    responseHeaders.put("X-Enrichment-Data", enrichmentData);
                    response.append("External Service Result: ").append(enrichmentData).append("\n");
                } catch (Exception e) {
                    responseHeaders.put("X-Enrichment-Data", "ERROR");
                    response.append("External Service Result: ERROR\n");
                    logger.error("External service call failed", e);
                }
            }

            if (responseHeaders.isEmpty()) {
                response.append("No filters triggered. Use headers like X-User-ID, X-Compute-Hash, X-Enrich-Data\n");
            }

            response.append("\nAsync Pattern Demonstration Complete!\n");
            response.append("Check the response headers for filter results.\n");
        }
    }

    // Shutdown hook for cleanup
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
        }));
    }
}