# Build the Spring Boot fat jar with the Maven wrapper (JDK 25), then run it on a JRE-only image as a non-root user.
# OS pinned to Ubuntu 24.04 (-noble): the unsuffixed Temurin tags already point at a newer Ubuntu.
FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /build
# Dependencies in their own layer: a change to src/ alone does not download them again.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI (`./mvnw -B verify`), not in the image build.
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN useradd --system --no-create-home --shell /bin/sh app
WORKDIR /app
# Fixed path (pom.xml <finalName>): Railway pre-deploy and `railway ssh` run
# `java -jar target/szczur-radar-api.jar <command>` from /app.
COPY --from=build /build/target/szczur-radar-api.jar target/szczur-radar-api.jar
USER app

# No arguments = HTTP server on $PORT; `docker run <image> migrate` = Flyway migrations.
# The JVM defaults to 25% of the container memory for the heap; 75% suits a single-process container.
# Extra JVM flags: JAVA_TOOL_OPTIONS (read by the JVM itself).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/target/szczur-radar-api.jar"]
