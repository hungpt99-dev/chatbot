# ---- Build stage: no local JDK required ----
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean bootJar --no-daemon

# ---- Run stage ----
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
# H2 file DB lives in /data (mount a volume to persist). Flyway migrates on boot.
VOLUME /data
ENV SPRING_DATASOURCE_URL="jdbc:h2:file:/data/helpdesk"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
