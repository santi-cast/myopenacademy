# Frontend de Code Academy

Pantallas de H4 sobre el [contrato de la API](../docs/api.md): acceso y registro, Mis cursos, catálogo e índice del curso. La vista de lección es todavía un marcador (solo el título) hasta la T9 parte b. Dependencia de ejecución añadida en H4: `react-router` 8.5.1, versión exacta aprobada.

## Comandos locales

Desde `frontend/`, con Node 24.22.1 y npm 11.19.1:

| Comando | Propósito |
| --- | --- |
| `npm ci` | Reproducir las dependencias del lockfile existente. |
| `npm run test -- --run` | Pruebas unitarias con fetch simulado, sin servidores ni red. |
| `npm run lint` | Oxlint del template oficial. |
| `npm run build` | Comprobar TypeScript y construir con Vite. |
| `npm run dev` | Servidor local en `127.0.0.1:5173`, sin cambiar de puerto si está ocupado. |
| `npm run preview` | Previsualización del build, solo loopback. |
| `npm run test:e2e` | Smoke Chromium con backend real ya iniciado en `127.0.0.1:8080`. |

Los resultados y pendientes se registran en [las tareas de H4](../odd/tasks/h4-first-journey.md).

El smoke comprueba que una visita anónima llega al backend real por el proxy (`GET /api/me` → 401 `AUTHENTICATION_REQUIRED`) y termina en la pantalla de acceso; el recorrido completo es la T10. Requiere Chromium de Playwright previamente instalado; no instala navegadores, no inicia el backend ni usa respuestas simuladas. Puede reutilizar el servidor Vite local existente. Detenerse si el navegador requiere paquetes del sistema.

## Pantallas y rutas

| Ruta | Pantalla |
| --- | --- |
| `/login`, `/register` | Acceso y registro; sin sesión. El registro no inicia sesión: vuelve al acceso con una confirmación. |
| `/` | Mis cursos: avance «X de Y lecciones disponibles completadas · P % · Z previstas», «Continuar» o índice. |
| `/courses` | Catálogo; «Añadir … a Mis cursos» se muestra como añadido solo tras el 200 del servidor. |
| `/courses/:courseId` | Índice del curso agrupado por fase; las lecciones pendientes no son enlaces. |
| `/lessons/:lessonId` | Marcador de la lección (parte b). |

Las demás rutas muestran «No encontrado». Sin sesión, toda ruta privada lleva al acceso.

## Cliente de la API

- `src/api/client.ts`: `fetch` al mismo origen con `Accept: application/json` y `credentials: 'same-origin'`. Los errores llegan como `{code, message}` y la interfaz decide solo por `code`.
- CSRF: justo antes de cada petición insegura se lee la cookie `XSRF-TOKEN` y se envía en `X-XSRF-TOKEN`. Login y logout caducan la cookie; si falta, se hace antes un único `GET /actuator/health` (público, sin sesión) para que el backend emita una nueva. Una escritura nunca se reintenta.
- Un 401 `AUTHENTICATION_REQUIRED` borra el estado privado y lleva al acceso. Logout hace lo mismo tras el 204.

## Estilo

Variables de diseño del boceto aprobado (`design/h4-visual-style/04-mezcla.html`) en `src/styles/tokens.css`: colores claro/oscuro, fuentes del sistema, tamaños, espaciado y radios. El resto del CSS usa solo `var(--…)`; `src/styles.test.ts` lo comprueba (regla detallada en el propio test). El tema parte de `prefers-color-scheme`, el botón nombra la acción («Modo oscuro» / «Modo claro») y la elección se recuerda en `localStorage` (`ca.theme`).

Vite conserva los paths de `/api` y del endpoint exacto `/actuator/health` al reenviarlos al backend loopback. La configuración del frontend no carga el entorno de la raíz; nunca colocar contraseñas en variables `VITE_*`.
