# Build stage
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /app

# Copy just the config-server module
COPY ecoswap-platform/service-discovery/pom.xml ./pom.xml
COPY ecoswap-platform/service-discovery/src ./src

# Build the application (standalone, not as a module)
RUN mvn clean package -DskipTests

# Runtime stage - using JRE-only image for smaller size (~50% smaller)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]