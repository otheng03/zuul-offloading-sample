# Zuul Async Filter Offloading Sample

This standalone project demonstrates how to use Zuul's async filter capabilities to offload long-running tasks from the Netty event loop, enabling high-performance request processing.

## Overview

The sample includes four different patterns for offloading work:

1. **Database Lookups** - I/O-bound operations using RxJava's I/O scheduler
2. **Heavy Computation** - CPU-bound operations using custom thread pools
3. **External Service Calls** - HTTP client operations with timeouts and error handling
4. **Batch Processing** - Background scheduled tasks for metrics aggregation

## Project Structure

```
zuul-offloading-sample/
├── src/main/java/com/netflix/zuul/sample/
│   ├── OffloadingSampleServer.java          # Main demonstration class
│   └── filters/
│       ├── BatchProcessingFilter.java       # Background batch processing
│       ├── DatabaseLookupFilter.java        # Database lookup simulation
│       ├── ExternalServiceCallFilter.java   # External HTTP service calls
│       └── HeavyComputationFilter.java      # CPU-intensive computations
├── src/main/resources/
│   └── application.properties               # Configuration
├── gradle/wrapper/                          # Gradle wrapper files
├── build.gradle                             # Build configuration
├── gradle.properties                        # Gradle settings
└── gradlew                                  # Gradle wrapper script
```

## Key Features Demonstrated

### 1. Async Filter Pattern
All filters use `FilterSyncType.ASYNC` and return `Observable<HttpRequestMessage>`:

```java
@Filter(order = 10, type = FilterType.INBOUND, sync = FilterSyncType.ASYNC)
public class DatabaseLookupFilter extends HttpInboundFilter {

    @Override
    public Observable<HttpRequestMessage> applyAsync(HttpRequestMessage request) {
        return Observable
            .fromCallable(() -> performDatabaseLookup())
            .subscribeOn(Schedulers.io()) // Offload to I/O thread pool
            .timeout(5, TimeUnit.SECONDS)
            .map(result -> enrichRequest(request, result));
    }
}
```

### 2. Thread Pool Management
- **I/O Operations**: Uses `Schedulers.io()` for database and HTTP calls
- **CPU Operations**: Custom sized thread pool based on available processors
- **Batch Processing**: Dedicated single-thread executor for background tasks

### 3. Error Handling and Resilience
- Timeout protection for all async operations
- Graceful error handling with fallback responses
- Circuit breaker patterns for external service calls

### 4. Performance Optimization
- Non-blocking request processing
- Efficient thread utilization
- Background batch processing for metrics

## Prerequisites

- **Java 17+**
- **Gradle 8.0+** (or use included wrapper)
- **Internet connection** (for downloading dependencies)

## Quick Start

### 1. Clone or Download
```bash
# If you have the project directory
cd zuul-offloading-sample

# Or clone if it's in a git repository
git clone <repository-url>
cd zuul-offloading-sample
```

### 2. Build the Project
```bash
./gradlew build
```

### 3. Run the Demonstration

**Option 1: Console Demonstration**
```bash
./gradlew run
```
This runs a console application that demonstrates the filter patterns and shows how they would be used in a real Zuul server.

**Option 2: HTTP Server for Testing with curl**
```bash
./gradlew runServer
```
This starts an HTTP server on port 8080 that allows you to test the async filter patterns using curl commands (see Testing section below).

## Sample Output

```
Zuul Async Filter Offloading Sample
===================================
Async filters created successfully:
- DatabaseLookupFilter: Demonstrates I/O thread pool offloading
- HeavyComputationFilter: Shows CPU-bound work on custom thread pools
- ExternalServiceCallFilter: HTTP client operations with timeouts
- BatchProcessingFilter: Background scheduled task processing

Key patterns demonstrated:
1. Observable.fromCallable(() -> blockingOperation()).subscribeOn(Schedulers.io())
2. Custom thread pools for CPU-bound work
3. Timeout and error handling with onErrorReturn()
4. Background scheduled executors for batch processing
```

## Integration with Real Zuul Server

To integrate these filters into a real Zuul server:

### 1. Add to Filter Directory
Copy the filter classes to your Zuul server's filter directory, or include this JAR in your classpath.

### 2. Filter Registration
Filters are automatically discovered via the `@Filter` annotation:

```java
@Filter(order = 10, type = FilterType.INBOUND, sync = FilterSyncType.ASYNC)
public class YourAsyncFilter extends HttpInboundFilter {
    // Implementation
}
```

### 3. Server Configuration
Configure your Zuul server to load and use the filters. Example server startup:

```java
public class YourZuulServer extends BaseServerStartup {
    // Standard Zuul server configuration
    // Filters will be auto-discovered and loaded
}
```

## Testing the Async Patterns

### Option 1: Using the HTTP Server
Start the HTTP server:
```bash
./gradlew runServer
```

Then test with these curl commands:
```bash
# Database lookup filter
curl -H "X-User-ID: user123" http://localhost:8080/api/test

# Heavy computation filter
curl -H "X-Compute-Hash: true" -d "test data" http://localhost:8080/api/compute

# External service call filter
curl -H "X-Enrich-Data: true" http://localhost:8080/api/enrich

# Test all filters together
curl -H "X-User-ID: user123" -H "X-Compute-Hash: true" -H "X-Enrich-Data: true" \
     -d "test data" http://localhost:8080/api/all

# Batch processing runs automatically on all requests
```

You can see the filter results in:
- **Response body**: Shows which filters were triggered and their results
- **Response headers**: Contains filter outputs (X-User-Role, X-Computed-Hash, X-Enrichment-Data)
- **Server logs**: Show async thread pool usage (RxCachedThreadScheduler-N for I/O, computation-pool-N for CPU)

### Option 2: Integration with Real Zuul Server
Use HTTP headers to trigger specific filters in your Zuul server:

```bash
# Database lookup filter
curl -H "X-User-ID: user123" http://your-zuul-server/api/test

# Heavy computation filter
curl -H "X-Compute-Hash: true" -d "data" http://your-zuul-server/api/compute

# External service call filter
curl -H "X-Enrich-Data: true" http://your-zuul-server/api/enrich

# Batch processing (runs on all requests)
curl http://your-zuul-server/api/any-path
```

## Architecture Patterns

### Thread Pool Selection
```java
// I/O Bound Operations
Observable.fromCallable(() -> databaseCall())
    .subscribeOn(Schedulers.io())

// CPU Bound Operations
Observable.fromCallable(() -> heavyComputation())
    .subscribeOn(Schedulers.from(customCpuPool))

// Background Tasks
ScheduledExecutorService backgroundPool =
    Executors.newSingleThreadScheduledExecutor()
```

### Timeout and Error Handling
```java
return Observable
    .fromCallable(() -> riskyOperation())
    .subscribeOn(Schedulers.io())
    .timeout(5, TimeUnit.SECONDS)
    .onErrorReturn(error -> {
        logger.error("Operation failed", error);
        return fallbackResult();
    });
```

### Resource Management
```java
// Proper cleanup in filter lifecycle
public void shutdown() {
    threadPool.shutdown();
    try {
        if (!threadPool.awaitTermination(10, TimeUnit.SECONDS)) {
            threadPool.shutdownNow();
        }
    } catch (InterruptedException e) {
        threadPool.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

## Configuration Options

### Dependencies
The project demonstrates RxJava async patterns without requiring a specific Zuul version:

```gradle
dependencies {
    implementation 'io.reactivex:rxjava:1.3.8'
    implementation 'com.google.guava:guava:32.1.3-jre'
    // Other dependencies...
}
```

### JVM Settings
Configured for optimal performance:

```gradle
run {
    jvmArgs = [
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-opens=java.base/java.util=ALL-UNNAMED'
    ]
}
```

## Monitoring and Observability

The sample includes:
- Detailed logging of filter execution
- Metrics collection for performance monitoring
- Request tracing through processing pipeline
- Error tracking and reporting

## Extending the Sample

To add new async filters:

1. **Create Filter Class**:
   ```java
   @Filter(order = 40, type = FilterType.INBOUND, sync = FilterSyncType.ASYNC)
   public class YourAsyncFilter extends HttpInboundFilter {
       // Implementation
   }
   ```

2. **Implement Async Logic**:
   ```java
   @Override
   public Observable<HttpRequestMessage> applyAsync(HttpRequestMessage request) {
       return Observable.fromCallable(() -> yourAsyncWork())
           .subscribeOn(appropriateScheduler())
           .timeout(reasonableTimeout(), TimeUnit.SECONDS);
   }
   ```

3. **Add Conditional Logic**:
   ```java
   @Override
   public boolean shouldFilter(HttpRequestMessage msg) {
       return msg.getHeaders().getFirst("X-Your-Header") != null;
   }
   ```

## Performance Considerations

- **Thread Pool Sizing**: Size pools appropriately for your workload
- **Timeout Values**: Set reasonable timeouts to prevent resource exhaustion
- **Error Handling**: Always provide fallback behavior
- **Resource Cleanup**: Implement proper shutdown procedures
- **Monitoring**: Track filter performance and error rates

## Troubleshooting

### Common Issues

1. **Dependency Conflicts**: Update Zuul version in `build.gradle`
2. **Memory Issues**: Adjust JVM heap settings in `gradle.properties`
3. **Timeout Errors**: Increase timeout values for slow operations
4. **Thread Pool Exhaustion**: Monitor and size pools appropriately

### Debug Mode
Enable debug logging:
```properties
# In application.properties
logging.level.org.example.zuul.sample=DEBUG
```

## Contributing

This sample is designed to be educational and extensible. Feel free to:
- Add new filter patterns
- Improve error handling
- Add more comprehensive monitoring
- Create additional examples

## License

Licensed under the Apache License, Version 2.0. See the source files for full license text.

---

**Note**: This is a demonstration project showing async filter patterns. For production use, ensure proper testing, monitoring, and resource management based on your specific requirements.