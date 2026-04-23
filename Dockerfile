# ================================================================
#  ConnectSphere — ONE Dockerfile for ALL services
#
#  This single file builds any service.
#  docker-compose.yml passes SERVICE_NAME as a build argument,
#  so you NEVER need to touch this file manually.
#
#  How it works:
#    Stage 1 (builder) → goes into the correct service folder,
#                         runs Maven, produces the JAR
#    Stage 2 (runner)  → copies only the JAR into a tiny JRE image
# ================================================================

# ── Stage 1 : BUILD ──────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

# SERVICE_NAME is passed from docker-compose.yml
# Example values: auth-service, post-service, eureka-server ...
ARG SERVICE_NAME

WORKDIR /app

# Copy pom.xml of that specific service first
# (Docker caches this layer → Maven re-downloads deps only when pom.xml changes)
COPY ${SERVICE_NAME}/pom.xml .

RUN mvn dependency:go-offline -B

# Copy that service's source code
COPY ${SERVICE_NAME}/src ./src

# Build the JAR (skip tests for faster Docker build)
RUN mvn clean package -DskipTests -B

# ── Stage 2 : RUN ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy only the final JAR from the build stage
COPY --from=builder /app/target/*.jar app.jar

# Spring Boot will read SERVER_PORT from environment (set in docker-compose)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]