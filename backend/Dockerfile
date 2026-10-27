FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

# 1. Copier uniquement le pom.xml et télécharger les dépendances.
#    Cette couche est mise en cache tant que pom.xml ne change pas.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 2. Copier les sources et compiler. Le cache Maven local est déjà chaud.
COPY src ./src
RUN mvn package -DskipTests -o


FROM eclipse-temurin:17-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/api/auth/ping || exit 1

CMD ["java", "-jar", "app.jar"]
