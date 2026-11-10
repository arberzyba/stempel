# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar (has the full JDK and Maven, about 500 MB) ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app

# Copy only the build files first and download dependencies. Docker caches this
# layer, so as long as pom.xml doesn't change, later builds skip the download.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Files from Windows lose the "executable" flag, so set it here.
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline

COPY src/ src/
# Tests run in CI, not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B package -DskipTests \
    && cp target/stempel-*.jar app.jar

# ---- Stage 2: run the jar (only the Java runtime, no build tools) ----
FROM eclipse-temurin:21-jre-noble

# Run as a normal user, not as root: if someone broke in through a bad file,
# they would have no admin rights in the container.
RUN useradd --system --uid 10001 --no-create-home stempel
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER 10001

EXPOSE 8080
# Use up to 75 % of the container's memory limit; restart instead of hanging on out-of-memory.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
