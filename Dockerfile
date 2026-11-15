FROM eclipse-temurin:21-jre

WORKDIR /opt/regional-core-banking-api

ARG APP_JAR=target/regional-core-banking-api-1.0.0-SNAPSHOT.jar
COPY ${APP_JAR} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/regional-core-banking-api/app.jar"]
