FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1000 -S finsentinel && adduser -u 1000 -S finsentinel -G finsentinel
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data/reports && chown -R 1000:1000 /app
USER finsentinel
ENV PORT=7860
EXPOSE 7860
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
