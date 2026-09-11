FROM eclipse-temurin:21-jre

WORKDIR /opt/regional-core-banking-api

COPY target/regional-core-banking-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/regional-core-banking-api/app.jar"]
