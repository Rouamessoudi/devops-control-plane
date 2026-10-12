FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S dcp \
    && adduser -S dcp -G dcp -h /home/dcp
WORKDIR /app
COPY --chown=dcp:dcp backend/cicd-service/target/service.jar /app/service.jar
USER dcp
EXPOSE 8086
HEALTHCHECK --interval=20s --timeout=3s --start-period=70s --retries=8 CMD curl -fsS http://localhost:8086/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/service.jar"]