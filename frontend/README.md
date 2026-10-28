# Frontend de Code Academy

Pantallas de H4 sobre el [contrato de la API](../docs/api.md): acceso y registro, Mis cursos, catálogo, índice del curso y vista de lección. Dependencias de ejecución añadidas en H4, en versiones exactas aprobadas: `react-router` 8.5.1, `mermaid` 12.2.0 y `highlight.js` 11.12.0 (estas dos se cargan solo en la vista de lección).

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
| `/lessons/:lessonId` | Lección: temario, texto, ejercicios con su estado, archivos y anterior/siguiente. Una lección pendiente (409 `LESSON_UNAVAILABLE`) muestra «todavía no disponible». |

Las demás rutas muestran «No encontrado». Sin sesión, toda ruta privada lleva al acceso.

## Vista de lección

- El HTML de la lección (`bodyHtml`, `statementHtml`, `solutionHtml`) se inserta tal como lo entrega el backend, ya saneado, sin volver a procesarlo. Alrededor solo se añaden controles marcados con `data-ui` (barra de código con «Copiar», «#» para copiar el enlace a una sección, la pregunta de recuperación dentro del `<summary>` de la solución) y tres atributos en los enlaces a lecciones pendientes (`class="link-pending"`, `aria-disabled`, `aria-describedby`).
- Enlaces: un único manejador delegado. `/lessons/…` y `/courses/…` navegan con el router y, tras cargar la lección, hacen scroll al fragmento; `#fragmento` hace scroll en la página. El destino se busca siempre con `document.getElementById` (los IDs pueden empezar por un dígito o llevar puntos). Los enlaces a `/api/resources/…` y los externos (`target="_blank"`) quedan para el navegador. Un enlace a una lección pendiente se ve apagado, se describe como «todavía no disponible» y no navega.
- Resaltado: `highlight.js` (núcleo y solo java, xml/html, sql, properties, bash, yaml, javascript, css, powershell, json y kotlin) en un chunk aparte que se importa al abrir una lección. Opera sobre el texto del elemento `<code>`; `text`, `mermaid` y lenguajes desconocidos quedan sin color y sin autodetección. Colores desde las variables `--hl-*` (claro y oscuro).
- Mermaid: chunk aparte, solo en la vista de lección, con `securityLevel: 'strict'`, sin etiquetas HTML y el tema del modo actual; se alimenta con el `textContent` del bloque. Si un diagrama falla, su código queda visible como texto con la nota «No se pudo dibujar el diagrama».
- Estados de lección y ejercicios: «Guardando…» y luego «✓ Guardado · hoy, HH:MM» (de `updatedAt`) solo tras la confirmación del servidor; un error muestra su mensaje y vuelve al estado guardado. Si el guardado añadió el curso, aviso «Java se añadió a Mis cursos». Abrir una solución no guarda nada.
- Continuación: `PUT /api/me/courses/{courseId}/continuation` solo si el progreso dice `added: true`, una vez cargada la lección.
- Modo enfoque «Ocultar temario» / «Mostrar temario» recordado en `localStorage` (`ca.temario`); sin almacenamiento, dura mientras la página esté abierta.
- Impresión: se imprime solo la lección; las soluciones abiertas salen completas y las cerradas solo con su resumen.

## Cliente de la API

- `src/api/client.ts`: `fetch` al mismo origen con `Accept: application/json` y `credentials: 'same-origin'`. Los errores llegan como `{code, message}` y la interfaz decide solo por `code`.
- CSRF: justo antes de cada petición insegura se lee la cookie `XSRF-TOKEN` y se envía en `X-XSRF-TOKEN`. Login y logout caducan la cookie; si falta, se hace antes un único `GET /actuator/health` (público, sin sesión) para que el backend emita una nueva. Una escritura nunca se reintenta.
- Un 401 `AUTHENTICATION_REQUIRED` borra el estado privado y lleva al acceso. Logout hace lo mismo tras el 204.

## Estilo

Variables de diseño del boceto aprobado (`design/h4-visual-style/04-mezcla.html`) en `src/styles/tokens.css`: colores claro/oscuro, fuentes del sistema, tamaños, espaciado y radios. El resto del CSS usa solo `var(--…)`; `src/styles.test.ts` lo comprueba (regla detallada en el propio test). El tema parte de `prefers-color-scheme`, el botón nombra la acción («Modo oscuro» / «Modo claro») y la elección se recuerda en `localStorage` (`ca.theme`).

Vite conserva los paths de `/api` y del endpoint exacto `/actuator/health` al reenviarlos al backend loopback. La configuración del frontend no carga el entorno de la raíz; nunca colocar contraseñas en variables `VITE_*`.
