# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a demonstration project showcasing Zuul 2 filter patterns for offloading work from the Netty event loop. The project uses Java 11 with Gradle and demonstrates four key async patterns: database lookups, heavy computation, external service calls, and batch processing using a real Zuul 2 server.

## Development Commands

### Build and Run
- `./gradlew build` - Build the project
- `./gradlew run` - Run the Zuul 2 server with demonstration filters (starts on port 8080)
- `./gradlew test` - Run tests using JUnit 5

### Testing the Filters
Once the server is running, test the filters with:
- Database lookup: `curl -H 'X-User-ID: user123' http://localhost:8080/api/test`
- Heavy computation: `curl -H 'X-Compute-Hash: true' -d 'test data' http://localhost:8080/api/compute`
- External service: `curl -H 'X-Enrich-Data: true' http://localhost:8080/api/enrich`
- Batch processing runs on all requests automatically

### Project Structure
```
src/main/java/com/netflix/zuul/sample/
├── OffloadingSampleServer.java          # Zuul 2 server with filter registration
└── filters/                             # Zuul 2 sync filter demonstrations
    ├── BatchProcessingFilter.java       # Background batch processing
    ├── DatabaseLookupFilter.java        # I/O thread pool usage
    ├── ExternalServiceCallFilter.java   # HTTP client with timeouts
    └── HeavyComputationFilter.java      # CPU-bound custom thread pools
```

## Architecture and Patterns

### Zuul 2 Filter Pattern
All filters extend `HttpInboundSyncFilter` but use RxJava 2 internally for async operations:
- Filters implement `apply(HttpRequestMessage)`, `shouldFilter()`, and `filterOrder()`
- Async work is done internally using `Observable.blockingGet()` to maintain sync interface
- Results are stored in request context for downstream use

### Key Dependencies
- **Zuul Core 2.3.3**: Zuul 2 framework
- **Zuul Netty 2.3.3**: Netty server integration
- **RxJava 2.2.21**: Core async processing (required by Zuul 2)
- **Netty 4.1.100**: Server framework
- **OkHttp 4.12.0**: HTTP client operations
- **Jackson 2.19.2**: JSON processing

### Thread Pool Management
The filters demonstrate proper resource management with dedicated pools for different workload types. CPU-bound pools are sized based on available processors, while I/O pools use RxJava's built-in schedulers.

### Error Handling
All async operations include timeout protection and fallback behavior using RxJava 2's `timeout()` and `onErrorReturn()` operators.