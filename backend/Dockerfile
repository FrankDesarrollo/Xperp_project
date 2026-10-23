# Se construye desde la raíz del repo (necesita contracts/): docker compose build backend

# 1. Empaqueta el contrato OpenAPI en un solo archivo
FROM node:22-alpine AS contract
WORKDIR /workspace/contracts
COPY contracts ./
RUN npx --yes @redocly/cli@2.69.0 bundle openapi/openapi.yaml --output dist/openapi.yaml

# 2. Compila el backend contra el contrato empaquetado
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY --from=contract /workspace/contracts ./contracts
COPY backend/.mvn ./backend/.mvn
COPY backend/mvnw backend/pom.xml ./backend/
WORKDIR /workspace/backend
RUN ./mvnw -B -q dependency:go-offline -Dcontract.bundle.skip=true
COPY backend/src ./src
RUN ./mvnw -B -q package -DskipTests -Dspotless.check.skip=true -Dcontract.bundle.skip=true

# 3. Imagen final, solo con el JRE
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 xperp
COPY --from=build /workspace/backend/target/xperp-backend-*.jar app.jar
USER xperp
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
