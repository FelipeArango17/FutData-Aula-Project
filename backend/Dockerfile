# syntax=docker/dockerfile:1
# Imagen del backend de FutData. Construir desde la carpeta backend/:
#   docker build -t futdata-backend .
# Las pruebas no corren aquí (las corre `./mvnw verify` y el CI); esta imagen solo empaqueta.

# --- Etapa 1: compilar con Maven (JDK 21) ---
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /app

# mvnw descarga Maven como .zip solo si existe unzip; sin él baja el .tar.gz y la suma SHA-256 de
# .mvn/wrapper/maven-wrapper.properties (que es la del .zip) no coincide. Esta etapa no llega a la imagen final.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Primero solo el wrapper y el pom: las dependencias quedan en caché mientras no cambie el pom.xml.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests

# --- Etapa 2: ejecutar con JRE 21, sin Maven ni código fuente ---
FROM eclipse-temurin:21-jre-noble
WORKDIR /app

# Usuario sin privilegios: la aplicación nunca corre como root.
RUN groupadd --system futdata && useradd --system --gid futdata --no-create-home --shell /usr/sbin/nologin futdata

COPY --from=build --chown=futdata:futdata /app/target/futdata-backend.jar app.jar

USER futdata
EXPOSE 8080

# Perfil prod: exige DB_URL, DB_USER, DB_PASSWORD, CORS_ORIGINS, N8N_BASE_URL y N8N_WEBHOOK_TOKEN.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# "Healthy" = el puerto HTTP acepta conexiones (bash y /dev/tcp vienen en la imagen Ubuntu; no hace falta curl).
# No consulta /api/health a propósito: ese endpoint devuelve 503 sin base de datos aunque la aplicación esté viva.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/${PORT:-8080}"]

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
