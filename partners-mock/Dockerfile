# Se construye desde la raíz del repo: docker compose build partners-mock
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY partners-mock/.mvn ./.mvn
COPY partners-mock/mvnw partners-mock/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY partners-mock/src ./src
RUN ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 xperp
COPY --from=build /workspace/target/xperp-partners-mock-*.jar app.jar
USER xperp
EXPOSE 8101
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
