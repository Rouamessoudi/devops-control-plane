FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY backend/api-gateway/target/service.jar /app/service.jar
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=3s --start-period=70s --retries=8 CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/service.jar"]
