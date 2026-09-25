FROM maven:3.9.16-eclipse-temurin-25-alpine AS builder

WORKDIR /app

COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package

FROM eclipse-temurin:25.0.3_9-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder --chown=spring:spring /app/target/toyoko-inn-room-alert-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
