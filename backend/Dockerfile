# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --create-home mla \
    && mkdir -p /app/data/media-storage \
    && chown -R mla:mla /app/data
COPY --from=build /app/target/mla-finance-backend-*.jar app.jar
USER mla
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
