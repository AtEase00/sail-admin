FROM eclipse-temurin:21-jre-jammy

WORKDIR /opt/sailadmin
COPY sail-server-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/sailadmin/app.jar"]
