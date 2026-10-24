# Etapas: deps (caché de Maven) → test (ejecuta los tests) / build (jar) → runtime (imagen final, sin root)
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

FROM deps AS test
COPY src ./src
CMD ["mvn", "-B", "-ntp", "test"]

FROM deps AS build
COPY src ./src
RUN mvn -B -ntp -DskipTests package && mv target/studylab-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
