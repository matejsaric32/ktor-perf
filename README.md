# ktor-perf

Benchmark implementation of **Ktor** used in the empirical study:
*"Empirical Static and Infrastructure Evaluation of Microservice Frameworks Across JVM, GraalVM Native Image, and Rust
in Containerized Environments"*

---

## Versions

| Component           | Version                       |
|---------------------|-------------------------------|
| Ktor                | 3.3.0                         |
| Kotlin              | 2.2.20                        |
| Java                | 21                            |
| Base image          | eclipse-temurin:21-jre-alpine |
| kafka-clients       | 4.1.0                         |
| postgresql (driver) | 42.7.8                        |
| jedis               | 7.0.0                         |
| HikariCP            | 6.2.1                         |
| koin-ktor           | 3.5.6                         |
| logback-classic     | 1.4.14                        |

---

## Build

### JAR

```bash
./gradlew :server:build -x test
```

### Podman image

```bash
podman build -t ktor-perf:latest .
```