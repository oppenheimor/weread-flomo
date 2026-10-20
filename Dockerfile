FROM node:24-bookworm-slim AS web
WORKDIR /build
COPY package*.json ./
RUN npm ci
COPY tsconfig.json vite.config.ts index.html ./
COPY web ./web
COPY public ./public
ARG APP_BASE_PATH=/
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS java
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /build/dist /build/dist
RUN mvn -B package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=java /build/backend/target/weread-flomo-0.2.0.jar /app/app.jar
USER 10001:10001
EXPOSE 18081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
