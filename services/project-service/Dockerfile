# Build with the Maven wrapper, run on a JRE-only image as a non-root user.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Dependencies first: this layer is reused until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home forgeai

# Sized for a small container (Render's free plan: 512 MB, 0.1 CPU). The heap
# takes about half the memory, leaving room for class metadata, threads and
# the JIT. Serial GC and C1-only compilation start faster and use less memory
# on a fraction of one CPU. Override JAVA_TOOL_OPTIONS for a larger machine.
# Set before the training run below: a class archive is only used when the
# JVM starts with the same options it was made with.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

# Unpacked, the application starts faster than as one nested jar.
COPY --from=build /build/target/project-service-*.jar /tmp/app.jar
RUN java -Djarmode=tools -jar /tmp/app.jar extract --destination /app && rm /tmp/app.jar
WORKDIR /app

# Class Data Sharing. On a tenth of a CPU, much of a cold start is spent
# loading and verifying classes. A training run starts the application once,
# here at build time, and saves those classes in an archive that every later
# start maps straight into memory.
#
# The training run stops as soon as the application context is built
# (spring.context.exit=onRefresh), before anything runs on start-up, and is
# told not to touch a database: there is none at build time. The values below
# are placeholders that only satisfy configuration; none is used for real.
RUN JWT_SECRET=training-run-placeholder-not-a-real-secret-0123456789 \
    FORGEAI_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE= \
    GITHUB_CLIENT_ID=training \
    GITHUB_CLIENT_SECRET=training \
    java -XX:ArchiveClassesAtExit=app.jsa \
         -Dspring.context.exit=onRefresh \
         -Dspring.flyway.enabled=false \
         -Dspring.jpa.hibernate.ddl-auto=none \
         -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
         -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
         -jar app.jar

USER forgeai

# The host may choose the port through PORT (see application.properties).
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-Duser.timezone=UTC", "-jar", "app.jar"]
