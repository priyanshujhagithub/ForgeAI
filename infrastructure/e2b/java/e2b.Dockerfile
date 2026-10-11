# E2B sandbox template for Maven and Gradle projects.
# The default E2B image has Python and Node but no JDK.
FROM eclipse-temurin:21-jdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends git maven gradle \
    && rm -rf /var/lib/apt/lists/*
