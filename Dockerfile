# ---- Build stage: compile and package with the full JDK ----
FROM amazoncorretto:25 AS build
WORKDIR /app

# Corretto's AL2023 base is minimal; the Gradle wrapper needs xargs
RUN dnf install -y findutils && dnf clean all

# Copy the build definition first so the dependency download layer is
# cached until build.gradle.kts / the wrapper actually change
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew dependencies --no-daemon > /dev/null || true

COPY src src
RUN ./gradlew bootJar --no-daemon

# Split the fat jar into layers (dependencies change rarely, application
# code changes every build) so image pulls only transfer what changed
RUN java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: minimal JDK on Alpine, non-root ----
FROM amazoncorretto:25-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/extracted/dependencies/ ./
COPY --from=build /app/extracted/spring-boot-loader/ ./
COPY --from=build /app/extracted/snapshot-dependencies/ ./
COPY --from=build /app/extracted/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
