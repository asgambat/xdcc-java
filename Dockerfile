# Build stage (JVM mode with Maven)
# For GraalVM native image, replace eclipse-temurin:21-jdk-alpine with ghcr.io/graalvm/native-image-community:21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/xdcc-java-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
