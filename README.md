# Code Academy

Academia web para estudiar programación, empezando por Java, y proyecto de portfolio para aprender construyendo una aplicación real.

**Estado:** base técnica H3 validada y completada. Hay una interfaz técnica y un backend con comprobación de salud, pero todavía no hay cuentas, cursos para estudiar ni progreso guardado. H4 no está iniciado y requiere aprobación del propietario.

## Tecnologías

- React, Vite y TypeScript; Vitest, React Testing Library y Playwright.
- Java 25, Spring Boot 4.1.2 y Maven mediante Wrapper.
- PostgreSQL 18.6 en Docker Compose; JPA, Flyway y Spring Security.

## Ejecutar en local

Necesitás Java 25, Docker con Compose y su motor activo, fnm con Node 24.22.1 y npm 11.19.1. No hace falta Maven global: el Wrapper usa Maven 3.10.0.

Primero seguí la [preparación de configuración privada](docs/desarrollo-local.md). La persona crea `.env` en la raíz; no se incluye una contraseña ni se necesita copiar una plantilla no versionada.

Abrí tres terminales desde la raíz del proyecto, en este orden:

**1. Base de datos**

```sh
docker compose up -d --wait db
```

**2. Backend**

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**3. Frontend**

```sh
cd frontend
fnm use 24.22.1
npm ci
npm run dev
```

Abrí http://127.0.0.1:5173. El backend expone salud técnica en http://127.0.0.1:8080/actuator/health. Todo escucha solo en este equipo.

## Pruebas

En `backend/`, con Docker activo: `./mvnw -B verify` (Testcontainers usa una base aislada).

En `frontend/`: `npm run test -- --run`, `npm run lint` y `npm run build`.
Para Chromium y pruebas de navegador, seguí la [guía local](docs/desarrollo-local.md).
Los resultados y pendientes se registran solo en [las tareas de H3](odd/tasks/h3-base.md).

## Documentación

- [Plan y próximos hitos](PLAN.md).
- [Desarrollo local, configuración y parada segura](docs/desarrollo-local.md).
- [Contrato de API](docs/api.md) y [manifiesto de contenido](docs/manifiesto.md): diseño del recorrido futuro, no funciones ya disponibles.
- [Directorio de contenido](content/README.md).

No hay instrucciones de despliegue: la publicación se abordará en H7.
