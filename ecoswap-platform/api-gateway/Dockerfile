# Build stage
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app

# Copy shared-service first (dependency)
COPY ecoswap-shared/shared-service/pom.xml ./shared-service/pom.xml
COPY ecoswap-shared/shared-service/src ./shared-service/src

# Install shared-service to local Maven repo
WORKDIR /app/shared-service
RUN mvn clean install -DskipTests

# Copy and build api-gateway
WORKDIR /app/api-gateway
COPY ecoswap-platform/api-gateway/pom.xml ./pom.xml
COPY ecoswap-platform/api-gateway/src ./src

# Build the application
RUN mvn clean package -DskipTests

# Runtime stage - using JRE-only image for smaller size
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the builder stage
COPY --from=builder /app/api-gateway/target/*.jar app.jar

# Environment variables with defaults (override in docker-compose or k8s)
ENV SERVER_PORT=9000 \
    KEYCLOAK_JWK_URI=http://keycloak:8080/realms/ecoswap/protocol/openid-connect/certs \
    KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/ecoswap \
    EUREKA_URL=http://service-discovery:8761/eureka/ \
    CONFIG_SERVER_URL=http://config-server:8888 \
    FRONTEND_URL=http://localhost:5173

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1

EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java", "-jar", "app.jar"]

