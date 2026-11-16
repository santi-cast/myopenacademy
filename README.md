# Code Academy

Academia web para estudiar programación, empezando por Java, y proyecto de portfolio para aprender construyendo una aplicación real.

**Estado:** H4 cerrado en la rama `feat/h4-first-journey`, pendiente de merge a `main`. Ver el [plan](PLAN.md).

## Qué hace hoy

- Registro e inicio de sesión con usuario y contraseña.
- Mis cursos, con el avance y «Continuar» en la última lección abierta.
- Catálogo, para añadir el curso Java.
- Índice del curso con sus 21 fases; las lecciones pendientes se ven pero no se abren.
- Lección 02.01 «Tu primer programa», con sus ejercicios y soluciones desplegables, diagramas, código resaltado y archivos descargables.
- Estados manuales de lección y ejercicio, y avance guardado por persona: cada alumno ve solo su progreso.

## Tecnologías

- React, Vite y TypeScript; Vitest, React Testing Library y Playwright.
- Java 25, Spring Boot 4.1.2 y Maven mediante Wrapper.
- PostgreSQL 18.6 en Docker Compose; JPA, Flyway y Spring Security.

## Ejecutar en local

Requisitos, configuración privada (`.env`) y arranque en tres terminales (base, backend y frontend) en la [guía local](docs/desarrollo-local.md). Después, abrí http://127.0.0.1:5173. Todo escucha solo en este equipo.

Para cambiar una contraseña olvidada se usa la [herramienta de consola](docs/desarrollo-local.md#recuperar-una-contraseña); no hay recuperación por la web.

## Pruebas

- En `backend/`, con Docker activo: `./mvnw -B verify` (Testcontainers usa una base aislada).
- En `frontend/`: `npm run test -- --run`, `npm run lint` y `npm run build`.
- Recorrido de dos usuarios en navegador, en un entorno propio y desechable: `npm run test:e2e` desde `frontend/` ([requisitos](docs/desarrollo-local.md#ejecutar-pruebas)).

## Documentación

- [Plan y próximos hitos](PLAN.md).
- [Desarrollo local, configuración y parada segura](docs/desarrollo-local.md).
- [Contrato de API](docs/api.md) y [manifiesto de contenido](docs/manifiesto.md).
- [Frontend](frontend/README.md) y [directorio de contenido](content/README.md).

No hay instrucciones de despliegue: la publicación se abordará en H7.
