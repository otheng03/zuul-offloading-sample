# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a comprehensive demonstration project showcasing Zuul 2 async filter patterns for offloading blocking operations from the Netty event loop. The project uses Java 17 with Gradle and demonstrates four key async patterns: database lookups, heavy computation, external service calls, and batch processing. While it runs as a standalone demonstration, the patterns are directly applicable to production Zuul 2 implementations.

## Development Commands

### Build and Run
- `./gradlew build` - Build the project
- `./gradlew run` - Run the async pattern demonstration (console application)
- `./gradlew test` - Run tests using JUnit 5

The application demonstrates all four async patterns in sequence and shows the thread pool usage, timing, and error handling.

### Project Structure
```
src/main/java/com/netflix/zuul/sample/
├── OffloadingSampleServer.java          # Main demonstration orchestrator
└── filters/                             # Async pattern implementations
    ├── BatchProcessingFilter.java       # Background batch processing
    ├── DatabaseLookupFilter.java        # I/O thread pool usage
    ├── ExternalServiceCallFilter.java   # HTTP client with timeouts
    └── HeavyComputationFilter.java      # CPU-bound custom thread pools
```

### Documentation
- `ARCHITECTURE.md` - Comprehensive guide to system structure, flow, and design patterns
- `README.md` - User guide with integration examples and usage instructions

## Architecture and Patterns

### Core Async Pattern
All async operations follow the fundamental RxJava 2 pattern:
```java
Observable.fromCallable(() -> blockingOperation())
    .subscribeOn(appropriateScheduler())    // Offload from main thread
    .timeout(duration, TimeUnit.SECONDS)    // Prevent resource leaks
    .doOnError(errorHandler)               // Log errors
    .onErrorReturn(fallbackHandler);       // Graceful degradation
```

### Thread Pool Strategy
- **I/O Operations**: Use `Schedulers.io()` for database/HTTP calls (unbounded cached pool)
- **CPU Operations**: Use `Schedulers.from(customThreadPool)` sized to available processors
- **Background Tasks**: Use `ScheduledExecutorService` for sequential batch processing

### Key Dependencies
- **Zuul Core 2.6.9**: Zuul 2 framework (demonstration of patterns)
- **RxJava 2.2.21**: Core async processing and reactive patterns
- **OkHttp 4.12.0**: HTTP client for external service calls
- **Jackson 2.19.2**: JSON processing
- **Netty 4.1.100**: Server framework
- **Guava 32.1.3**: Utility libraries

### Filter Implementation Patterns
The filters demonstrate standalone async patterns that can be integrated into real Zuul filters:

1. **Database Lookup**: I/O scheduler with 5s timeout, fallback to "UNKNOWN"
2. **Heavy Computation**: Custom CPU pool, 10s timeout, SHA-256 hash simulation
3. **External Service**: I/O scheduler, real HTTP calls to JSONPlaceholder API
4. **Batch Processing**: Background scheduled metrics collection and processing

### Error Handling & Resilience
- **Timeout Protection**: All operations have appropriate timeouts
- **Fallback Values**: Graceful degradation when operations fail
- **Detailed Logging**: Comprehensive error reporting and operation tracking
- **Resource Cleanup**: Proper thread pool lifecycle management

### Integration with Real Zuul Servers
These patterns can be integrated into production Zuul 2 filters by:
- Using the async Observable patterns internally within synchronous filter methods
- Calling `.blockingFirst()` to bridge async operations to sync filter interfaces
- Storing results in request context for downstream processing
- Registering filters in the FilterRegistry with appropriate order values

### Performance Characteristics
- **Thread Pools**: CPU pool = available processors, I/O pool = cached/unbounded
- **Timeouts**: 5s (DB), 10s (CPU), 8s (HTTP) based on operation characteristics
- **Memory**: ConcurrentHashMap for metrics, bounded queues for thread pools
- **Observability**: Comprehensive logging showing thread usage and timing