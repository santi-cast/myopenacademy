# H10 — Instalación

Objetivo: que la academia se instale y arranque con un solo comando, configurada solo con variables de entorno, accesible en la red de casa y con su nombre y su logo en un solo sitio. Alcance: `PLAN.md`, apartado «H10. Instalación» (cambios 17 a 20 de la lista de `ESTADO.md`). Rama `feat/h10-instalacion`, desde `main` `3f856b7`. Commits locales; el autor revisa antes de integrar. Sin remotos.

Pruebas: `./mvnw -B verify` en `backend/`; `npm run test -- --run`, `npm run lint` y `npm run build` en `frontend/`; el recorrido aislado `npm run test:e2e:journey`; y la instalación de verdad con Compose en un proyecto de Compose desechable (nunca `myopenacademy` ni el original).

## Alcance

- A1. **Todo en variables de entorno:** dirección y puerto de escucha, máquina, puerto y nombre de la base, dirección pública y carpeta de contenido, documentadas en `.env.example`. Nada de `127.0.0.1` ni `localhost` escrito en el código fuera de los valores por defecto. El puerto de la base deja de ser el `5433` del original.
- A2. **Un solo comando:** el backend sirve el frontend compilado (un origen y un puerto, sin Vite) y `docker compose up` levanta la base y la aplicación. El servidor no toma `.algo` como extensión (identificadores con punto).
- A3. **Red local:** escuchar en la red con una variable; documentar la IP fija o el nombre `.local` y avisar de que sin HTTPS las contraseñas viajan sin cifrar.
- A4. **Nombre y logo en un solo sitio**, con el nombre que pone el administrador por encima.
- A5. **Guía de instalación:** crear el administrador (`--create-admin`), recuperar una contraseña por consola, instalar cursos, y **cerrar el registro si la academia se publica en internet** (decisión del autor sobre D4 de H9).

## Decisiones de diseño (propuestas; las revisa el autor con el resto de H10)

- D1. **Variables:** `APP_ADDRESS` y `APP_PORT` (por defecto `127.0.0.1` y `8080`), `DB_HOST`, `DB_PORT` (por defecto `5434`, para no chocar con el `5433` del Code Academy original), `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`, `PUBLIC_URL` (la dirección con la que se entra, para la documentación y los avisos) y `CONTENT_DIR`. Todas en `.env.example`, con su explicación.
- D2. **Compose:** una imagen de la aplicación construida en varias etapas (frontend con Node, backend con Maven y, para ejecutar, solo Java) y dos servicios, `db` y `app`; `content/` y `cursos-instalados.json` montados desde la carpeta de la academia. `docker compose up -d --build` y listo.
- D3. **Red:** dentro del contenedor la aplicación escucha en todas las interfaces y Compose publica el puerto en `APP_BIND` (por defecto `127.0.0.1`: solo este equipo; `0.0.0.0` para la red de casa).
- D4. **Marca:** el nombre (`MyOpenAcademy`) y el logo (de momento, la marca `{ }` que ya se ve) en un solo fichero del frontend, del que salen la cabecera, el título y el icono de la pestaña.

## Tareas

T1 | A1 | Variables de entorno y `.env.example` | hecho: L2.
T2 | A2 | El backend sirve el frontend compilado, con los identificadores con punto | pendiente.
T3 | A2, A3 | Imagen de la aplicación y Compose con un solo comando; red local | pendiente.
T4 | A4 | Nombre y logo en un solo sitio | pendiente.
T5 | A5 | Guía de instalación, prueba de verdad con Compose y cierre | pendiente.

## Registro

L1. 16 de noviembre de 2026: H9 integrada en `main` (`f042a55`) y rama borrada; el autor pide seguir con H10. Rama `feat/h10-instalacion` desde `3f856b7`. Decisiones de diseño D1 a D4 propuestas, para la revisión del autor.
L2. 16 de noviembre de 2026, T1. `application.properties` toma del entorno la dirección y el puerto de escucha (`APP_ADDRESS`, `APP_PORT`), la base (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), la carpeta de contenido (`CONTENT_DIR`) y la dirección pública (`PUBLIC_URL`, nueva: `code-academy.public-url`). Valores por defecto para usarla solo en este equipo: `127.0.0.1`, `8080`, la base en `127.0.0.1:5434` y `../content`. **El puerto de la base pasa de `5433` a `5434`** (en la aplicación, en `compose.yaml` con `DB_PORT` y en `docs/desarrollo-local.md`), así que ya no choca con el Code Academy original. `DB_NAME` y `DB_USERNAME` siguen por defecto en `code_academy`, para no romper el `.env` que ya hay. `.env.example` reescrito y explicado línea a línea (también `APP_BIND` para Compose, en T3). Vite toma el puerto del backend de `APP_PORT`. Pruebas: `LocalEnvironmentConfigurationTest.theInstallationIsConfiguredOnlyWithVariables` (todas las variables mandan) y las de valores por defecto, al `5434`. `./mvnw -B -o verify`: 794 pruebas en verde. **Para el autor:** el aviso de `CLAUDE.md` sobre el `5433` y el proyecto `code-academy` ya no es cierto (Compose se llama `myopenacademy` desde H6 y el puerto es el `5434`); propongo actualizarlo.

