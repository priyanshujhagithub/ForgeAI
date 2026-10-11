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
USER forgeai
WORKDIR /app
COPY --from=build /build/target/project-service-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar"]
