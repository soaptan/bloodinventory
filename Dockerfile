FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY bloodinventory/pom.xml ./pom.xml
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY bloodinventory/src ./src
RUN mvn --batch-mode --no-transfer-progress clean package -DskipTests

FROM eclipse-temurin:21-jre

RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app spring \
    && mkdir -p /app/uploads /app/logs /app/backups \
    && chown -R spring:spring /app

WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/target/bloodinventory-*.jar /app/app.jar

USER spring

ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
