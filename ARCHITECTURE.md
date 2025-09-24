# Zuul 2 Async Filter Offloading - Architecture & Flow

This document explains the overall structure, flow, and design patterns of the Zuul 2 async filter offloading sample project.

## Project Overview

This project demonstrates how to implement async patterns in Zuul 2 filters to offload blocking operations from the main Netty event loop, enabling high-performance request processing. While the sample runs as a standalone demonstration, the patterns shown are directly applicable to real Zuul 2 implementations.

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Main Application                         │
│                OffloadingSampleServer                       │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  ├── Creates & Tests Filter Instances
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                    Filter Layer                             │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │
│  │ Database    │ │ Heavy       │ │ External    │ │ Batch  │ │
│  │ Lookup      │ │ Computation │ │ Service     │ │ Process│ │
│  │ Filter      │ │ Filter      │ │ Call Filter │ │ Filter │ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │
└─────────────────┬───────────────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────────────┐
│                   Async Layer                               │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌────────┐ │
│  │ I/O         │ │ Custom CPU  │ │ I/O         │ │ Single │ │
│  │ Scheduler   │ │ Thread Pool │ │ Scheduler   │ │ Thread │ │
│  │(RxJava)     │ │(Custom)     │ │(RxJava)     │ │Executor│ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/java/com/netflix/zuul/sample/
├── OffloadingSampleServer.java          # Main demonstration orchestrator
└── filters/                             # Async pattern implementations
    ├── DatabaseLookupFilter.java        # I/O-bound operations pattern
    ├── HeavyComputationFilter.java      # CPU-bound operations pattern
    ├── ExternalServiceCallFilter.java   # HTTP client pattern
    └── BatchProcessingFilter.java       # Background processing pattern
```

## Core Design Patterns

### 1. RxJava Observable Pattern

All async operations follow the same fundamental pattern:

```java
Observable.fromCallable(() -> {
    // Blocking operation here
    return result;
})
.subscribeOn(appropriateScheduler())  // Offload from main thread
.timeout(timeoutDuration)             // Prevent resource leaks
.doOnError(errorHandler)              // Log errors
.onErrorReturn(fallbackHandler);      // Graceful degradation
```

### 2. Thread Pool Selection Strategy

| Operation Type | Scheduler/Pool | Reasoning |
|---------------|----------------|-----------|
| I/O Operations | `Schedulers.io()` | Optimized for blocking I/O, unbounded pool |
| CPU-Bound Work | Custom `ThreadPoolExecutor` | Sized to CPU cores, prevents over-subscription |
| Background Tasks | Single-thread `ScheduledExecutorService` | Sequential processing, predictable scheduling |

### 3. Error Handling & Resilience

Each async operation includes:
- **Timeout Protection**: Prevents thread pool exhaustion
- **Error Logging**: Detailed error information for debugging
- **Fallback Values**: Graceful degradation when operations fail
- **Resource Cleanup**: Proper shutdown of thread pools

## Detailed Component Flow

### 1. Database Lookup Filter

**Purpose**: Demonstrates I/O-bound database operations

**Flow**:
```
Request → Check userId → Observable.fromCallable() → Schedulers.io()
    ↓
Database Simulation (100ms sleep) → User Role Lookup → Result
    ↓
Timeout (5s) → Error Handling → Fallback ("UNKNOWN")
```

**Thread Usage**: `RxCachedThreadScheduler-N` (I/O pool)

**Key Features**:
- Uses RxJava's I/O scheduler for blocking database calls
- 5-second timeout to prevent hanging requests
- Returns fallback role on failures

### 2. Heavy Computation Filter

**Purpose**: Demonstrates CPU-intensive operations

**Flow**:
```
Request → Input Data → Observable.fromCallable() → Custom CPU Pool
    ↓
SHA-256 Hash Loop (10,000 iterations) → Result Hash
    ↓
Timeout (10s) → Error Handling → Fallback ("COMPUTATION_FAILED")
```

**Thread Usage**: `computation-pool-{timestamp}` (Custom pool)

**Key Features**:
- Custom thread pool sized to available CPU cores
- Prevents blocking of Netty event loop
- Longer timeout for CPU-intensive work
- Proper thread pool lifecycle management

### 3. External Service Call Filter

**Purpose**: Demonstrates HTTP client operations

**Flow**:
```
Request → HTTP Request Builder → Observable.fromCallable() → Schedulers.io()
    ↓
OkHttp Call to JSONPlaceholder API → JSON Parsing → User Name Extract
    ↓
Timeout (8s) → Error Handling → Fallback ("Service unavailable")
```

**Thread Usage**: `RxCachedThreadScheduler-N` (I/O pool)

**Key Features**:
- Uses OkHttp for HTTP operations
- JSON parsing with Jackson
- Multiple timeout layers (connection + overall)
- Graceful handling of service unavailability

### 4. Batch Processing Filter

**Purpose**: Demonstrates background scheduled tasks

**Flow**:
```
Request Metrics Collection (Non-blocking) → ConcurrentHashMap
    ↓
Background Schedule (Every 5s) → Single Thread Executor
    ↓
Batch Processing → Metrics Aggregation → Alert Threshold Check
```

**Thread Usage**: `batch-processor` (Dedicated single thread)

**Key Features**:
- Non-blocking request processing
- Scheduled background aggregation
- Thread-safe metrics collection
- Configurable processing intervals

## Execution Flow

### Application Startup

1. **Initialization**: Create filter instances
2. **Background Tasks**: Start batch processor scheduling
3. **Resource Allocation**: Initialize thread pools and HTTP clients

### Test Execution Sequence

1. **Database Lookup Test**:
   - Executes on I/O thread pool
   - Simulates 100ms database latency
   - Returns user role based on userId

2. **Heavy Computation Test**:
   - Executes on custom CPU thread pool
   - Performs 10,000 SHA-256 hash iterations
   - Returns truncated hash result

3. **External Service Test**:
   - Executes on I/O thread pool
   - Makes real HTTP call to JSONPlaceholder API
   - Returns parsed user name from JSON response

4. **Batch Processing Test**:
   - Collects metrics for 5 simulated requests
   - Background thread processes metrics after 2-second delay
   - Demonstrates batch aggregation and alerting

### Application Shutdown

1. **Graceful Shutdown**: Stop batch processor
2. **Thread Pool Cleanup**: Shutdown custom computation pool
3. **Resource Release**: Clean up HTTP clients and connections

## Performance Characteristics

### Thread Pool Sizing

- **I/O Operations**: Unbounded cached thread pool (RxJava default)
- **CPU Operations**: Fixed pool = `Runtime.getRuntime().availableProcessors()`
- **Background Tasks**: Single thread for sequential processing

### Timeout Strategy

- **Database Operations**: 5 seconds (typical database SLA)
- **CPU Computations**: 10 seconds (allows for heavy processing)
- **External Services**: 8 seconds (network operations + processing)
- **HTTP Connections**: 2 seconds connect, 5 seconds read

### Memory Usage

- **Request Metrics**: `ConcurrentHashMap` with atomic counters
- **Thread Pool Queues**: 1000-item capacity with rejection policy
- **HTTP Client**: Connection pooling with automatic cleanup

## Integration with Real Zuul Servers

### Filter Registration Pattern

In a real Zuul 2 server, these patterns would be integrated as:

```java
@Filter(order = 10, type = FilterType.INBOUND)
public class DatabaseLookupFilter extends HttpInboundSyncFilter {

    @Override
    public HttpRequestMessage apply(HttpRequestMessage request) {
        // Use the async pattern internally
        String result = simulateDatabaseLookup(userId).blockingFirst();
        request.getContext().put("user_role", result);
        return request;
    }
}
```

### Server Configuration

```java
public class ZuulServer extends BaseServerStartup {
    @Override
    protected void addFilters(FilterRegistry registry) {
        registry.put("db-lookup", new DatabaseLookupFilter());
        registry.put("computation", new HeavyComputationFilter());
        registry.put("external-call", new ExternalServiceCallFilter());
        registry.put("batch-metrics", new BatchProcessingFilter());
    }
}
```

## Key Benefits

1. **Non-blocking**: Main event loop remains free for request handling
2. **Scalable**: Proper thread pool management prevents resource exhaustion
3. **Resilient**: Timeout and error handling prevent cascading failures
4. **Observable**: Detailed logging and metrics for monitoring
5. **Maintainable**: Clear separation of concerns and consistent patterns

## Dependencies & Technologies

- **Zuul Core 2.6.9**: Gateway framework
- **RxJava 2.2.21**: Reactive programming and async operations
- **OkHttp 4.12.0**: HTTP client for external service calls
- **Jackson 2.19.2**: JSON processing
- **Netty 4.1.100**: Underlying server framework
- **SLF4J**: Logging abstraction

## Monitoring & Observability

The sample includes comprehensive logging at key points:
- Filter execution start/completion
- Thread pool utilization
- Error conditions and fallback activation
- Batch processing metrics and alerts
- Request processing timing

This logging structure provides visibility into:
- Which thread pools are handling which operations
- Performance characteristics of each async pattern
- Error rates and fallback frequency
- Resource utilization and potential bottlenecks

## Conclusion

This architecture demonstrates production-ready patterns for implementing high-performance async operations in Zuul 2. The key insight is that while Zuul 2 filters are synchronous, they can leverage RxJava internally to offload blocking operations to appropriate thread pools, maintaining the responsiveness of the main event loop while providing robust error handling and observability.