# ============================================================================
# Multi-stage build for the banking-platform backend.
#
# Stage 1 ("builder"): full Maven + JDK 21, builds the fat JAR.
# Stage 2 ("runtime"): JRE-only image, copies in the JAR. Smaller, faster.
#
# Multi-stage is important here because the Maven layer is ~600MB (it pulls
# the world); the runtime layer is ~250MB. Pushing only the runtime to Fly
# means faster deploys, less storage, smaller attack surface.
# ============================================================================

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# Copy pom.xml first and resolve dependencies — this layer caches across
# code changes. Re-bumping a Java file won't re-download Maven deps.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy source and build
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create non-root user and set up directories WHILE STILL ROOT
RUN groupadd --system app && useradd --system --gid app app && \
    mkdir -p /app/statements && \
    chown -R app:app /app

# Switch to non-root for the actual runtime
USER app

# Copy the JAR (chown so app user can read it)
COPY --from=builder --chown=app:app /build/target/*.jar /app/app.jar

VOLUME ["/app/statements"]
EXPOSE 8080

# Pass JVM flags via env. Default to a sensible heap for Fly's small VMs.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]