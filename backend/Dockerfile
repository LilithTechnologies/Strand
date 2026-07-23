FROM gradle:8.14.3-jdk21 AS build
WORKDIR /app
COPY backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/src ./src
RUN gradle --no-daemon installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/strand-backend ./
EXPOSE 8080
ENTRYPOINT ["./bin/strand-backend"]
