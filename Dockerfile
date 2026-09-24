# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app --shell /usr/sbin/nologin spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/ms-pedidos360-catalog-*.jar app.jar
USER spring:spring
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
