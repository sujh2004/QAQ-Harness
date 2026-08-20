# Backend image: build with Maven, run on a JRE.
#
# The build stage keeps the Maven cache in a separate layer from the sources, so editing code does
# not re-download the dependency tree. The runtime stage carries no build tools — a smaller image
# with fewer things in it to attack.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -q -B dependency:go-offline

COPY src ./src
# Tests run in CI, not in the image build: a demo box should not need a database to build a jar.
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is here for the compose healthcheck; a health probe that depends on a tool the image may or
# may not ship is a probe that reports "unhealthy" for the wrong reason. python3 is the interpreter
# the demo skills declare — without it an installed skill fails at launch rather than at review.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl python3 \
    && ln -sf /usr/bin/python3 /usr/local/bin/python \
    && rm -rf /var/lib/apt/lists/*

# The agent reads the demo repository through app.repository.base-dir, which is mounted read-only by
# compose. Vector and skill data live under /app/data so they survive a container restart.
RUN useradd --system --create-home --uid 10001 devpilot \
    && mkdir -p /app/data \
    && chown -R devpilot:devpilot /app
USER devpilot

COPY --from=build --chown=devpilot:devpilot /build/target/*.jar /app/devpilot-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/devpilot-backend.jar"]
