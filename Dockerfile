# MyOpenAcademy in one image (H10): the frontend compiled with Node, the backend packaged with Maven, and only Java to
# run them. The backend serves the frontend itself (FRONTEND_DIR): one origin, one port. Built by `docker compose up`.

FROM node:24.22.1-bookworm-slim AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.10.0-eclipse-temurin-25 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -o -DskipTests package

FROM eclipse-temurin:25.0.5_8-jre
# Git reads the courses (only their objects, never a working tree), for the installation tools; a course folder may
# belong to another user than the one in the container.
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/* \
    && git config --system --add safe.directory '*'
RUN groupadd --system academia && useradd --system --gid academia --home /app academia
# The installation tools (content-import, content-update, content-courses), as on a machine with Java.
RUN for tool in content-import:ContentImportCli content-update:ContentUpdateCli content-courses:ContentCoursesCli; do \
      printf '#!/bin/sh\nexec java -Dloader.main=dev.codeacademy.content.%s -cp /app/myopenacademy.jar org.springframework.boot.loader.launch.PropertiesLauncher "$@"\n' "${tool#*:}" > "/usr/local/bin/${tool%%:*}"; \
      chmod 755 "/usr/local/bin/${tool%%:*}"; \
    done
WORKDIR /app
COPY --from=backend /build/backend/target/backend.jar /app/myopenacademy.jar
COPY --from=frontend /build/frontend/dist /app/frontend
# Inside the container: every interface (who may reach it is decided by APP_BIND in compose.yaml), the frontend it
# serves, and the courses mounted at /content.
ENV APP_ADDRESS=0.0.0.0 \
    APP_PORT=8080 \
    FRONTEND_DIR=/app/frontend \
    CONTENT_DIR=/content
USER academia
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/myopenacademy.jar"]
