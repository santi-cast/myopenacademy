# API — contrato de H2 cerrado

Contrato aprobado en diseño, no implementado ni verificado. Mantiene las 14 rutas base y desarrolla el [plan principal](../PLAN.md). La procedencia y las asociaciones de contenido se definen en el [manifiesto](manifiesto.md).

## Acceso, propiedad y efectos

Todas las rutas de catálogo, contenido, recursos y progreso requieren sesión. Registro e inicio de sesión son accesibles sin autenticar. Las rutas `/me` obtienen el propietario de la sesión; no aceptan `userId`/`usuario_id` para elegir de quién son los datos. Toda lectura y escritura personal se limita al usuario autenticado, también por identificador directo. Un dato personal ajeno no se devuelve ni se modifica.

Ningún GET modifica estados, inscripción o continuación. Obtener un token CSRF podrá crear una sesión técnica, nunca progreso de aprendizaje. No hay DELETE de inscripción ni endpoint de recuperación web.

Los nombres de formas siguientes son abreviaturas documentales, no envoltorios adicionales. Fechas `updatedAt` en UTC ISO 8601 (por ejemplo, `2026-01-01T12:00:00Z`); null si no existe estado guardado. IDs globales y permanentes, opacos para el cliente, con namespace permanente de curso para contenido. No devolver contraseñas, hashes ni versiones de credenciales.

## Formas reutilizables de salida

| Forma | Campos JSON y significado |
|---|---|
| User | `id`, `username` (nombre visible conservado). |
| Counts | `completedLessons`, `availableLessons`, `plannedLessons`, `percentage`: cifras calculadas dinámicamente desde el catálogo y los estados, nunca constantes. Porcentaje 0–100 sin redondeo contractual; null si no hay disponibles. |
| Course | `id`, `title`. |
| LessonEntry | `id`, `courseId`, `title`, `position`, `available` (disponibilidad importada, no promesa de que todo lo escrito en origen ya se importó). |
| Exercise | `id`, `lessonId`, `courseId`, `title`, `statementHtml`, `solutionHtml` (campos saneados por separado; solución libremente desplegable). |
| Resource | `resourceId`, `url`, `mediaType`; URL controlada `/api/resources/{resourceId}`, sin ruta física. |
| Continuation | `lastOpenedLessonId`: ID o null. |
| Enrollment | `courseId`, `added`, `continuation`, `progress` (Counts). |
| LessonState / ExerciseState | `lessonId` o `exerciseId`, `state`, `updatedAt`. Lección: `not_completed`/`completed`; ejercicio: `pending`/`in_progress`/`solved`. |
| Progress | `courseId`, `added`, `lessonStates` (LessonState[]), `exerciseStates` (ExerciseState[]), `continuation`, `progress` (Counts). |
| StateResult | `courseId`, `lessonId` o `exerciseId`, `state`, `updatedAt`, `courseAutoAdded`, `continuation`, `progress` (Counts). |

## Las 14 rutas base

Sin cuerpo de entrada salvo el indicado; arrays vacíos válidos. El cliente usa rutas relativas `/api`, también para recursos.

| Ruta base | Entrada JSON | HTTP y salida |
|---|---|---|
| POST /api/auth/register | `{ "username": "Alumno", "password": "una frase extensa" }` | 201, User; no inicia sesión, UI pasa a login. |
| POST /api/auth/login | Campos `username` y `password`; política de acceso indicada abajo, no el mínimo del registro | 200, User y cookie de sesión rotada. |
| POST /api/auth/logout | Sin cuerpo | 204, sin cuerpo; invalida sesión y limpia cookie. |
| GET /api/me | — | 200, User. |
| GET /api/courses | — | 200, `{ "courses": Course[] }`. |
| GET /api/courses/{courseId} | — | 200, `{ "course": Course, "lessons": LessonEntry[] }`, temario completo ordenado. |
| GET /api/lessons/{lessonId} | — | 200, `{ "lesson": LessonEntry, "bodyHtml": string, "exercises": Exercise[], "resources": Resource[], "previousLessonId": ID o null, "nextLessonId": ID o null }`; vecinos disponibles del mismo curso. |
| GET /api/resources/{resourceId} | — | 200, bytes con tipo seguro del recurso, no JSON; HTML solo descarga/texto escapado y SVG con sandbox según política de contenido. |
| GET /api/me/courses | — | 200, `{ "courses": [{ "course": Course, "enrollment": Enrollment }] }`. |
| PUT /api/me/courses/{courseId} | Sin cuerpo | 200, Enrollment con `added: true`; repetición no reinicia continuación ni estados. |
| GET /api/me/courses/{courseId}/progress | — | 200, Progress incluso sin inscripción; 404 si el curso no existe. |
| PUT /api/me/lessons/{lessonId}/state | `{ "state": "completed" }` | 200, StateResult de lección. |
| PUT /api/me/exercises/{exerciseId}/state | `{ "state": "in_progress" }` | 200, StateResult de ejercicio. |
| PUT /api/me/courses/{courseId}/continuation | `{ "lessonId": "java-lesson-intro" }` | 200, Enrollment con continuación confirmada; sin inscripción: 409 COURSE_NOT_ADDED. |

## Progreso y continuación

- Para un curso existente no añadido, progreso devuelve 200 con `added: false`, `lessonStates: []`, `exerciseStates: []` y `continuation.lastOpenedLessonId: null`. Incluye cifras compartidas de disponibles y previstas; `completedLessons` es 0 y `percentage` es 0 si hay disponibles, o null si no las hay. No crea registros personales; la interfaz interpreta los estados ausentes con sus valores por defecto: lección no completada y ejercicio pendiente.
- Para un curso añadido, devuelve `added: true`, estados guardados y continuación, junto a cifras calculadas. Un curso inexistente devuelve 404, no una respuesta vacía.
- El frontend solo llama a `PUT /api/me/courses/{courseId}/continuation` si el progreso indica `added: true`, después de cargar correctamente la lección, no al precargar enlaces. Durante la exploración no lo llama, para evitar errores 409 innecesarios. El backend conserva la defensa: sin inscripción devuelve 409 `COURSE_NOT_ADDED`, sin crear datos.
- Guardar un estado devuelve el estado persistido, `updatedAt`, `courseAutoAdded`, continuación y avance resultantes. `courseAutoAdded` solo es true si esa operación creó la inscripción. Inscripción, estado y continuación inicial en la lección correspondiente se guardan o rechazan en una sola transacción, también si se marcó un ejercicio suyo.
- Si la inscripción ya existe, marcar un estado no cambia por sí solo la continuación; abrir una lección sí la actualiza mediante el PUT condicionado. Añadir explícitamente mantiene la continuación vacía hasta abrir una lección. Reintentar no duplica datos ni altera la continuación existente. Repetir exactamente el estado mantiene `updatedAt` del último cambio real.
- Se rechazan apertura del contenido, estado y continuación de lecciones pendientes con 409 `LESSON_UNAVAILABLE`. Consultar su entrada del índice sí está permitido. Tampoco se cambian estados de ejercicios cuyo contenido no esté disponible.
- No existe ruta para quitar cursos. Repetir una adición explícita o asignar el mismo estado no duplica registros ni reinicia progreso. No se configuran borrados en cascada que destruyan progreso.
- Toda referencia pertenece al curso esperado: ejercicio y lección del mismo curso; continuación solo a lección disponible de ese curso. Las claves foráneas compuestas descritas en el plan garantizan consistencia, no sustituyen autorización.
- Todas las operaciones rechazadas dejan intactos datos personales y contadores. La UI muestra un cambio como guardado solo tras confirmación del backend; una respuesta fallida no simula éxito. No reintentar automáticamente escrituras de resultado incierto.

Ejemplo ilustrativo de un curso existente no añadido, con dos entradas y una disponible; no es un recuento del curso real:

```json
{
  "courseId": "java",
  "added": false,
  "lessonStates": [],
  "exerciseStates": [],
  "continuation": { "lastOpenedLessonId": null },
  "progress": {
    "completedLessons": 0,
    "availableLessons": 1,
    "plannedLessons": 2,
    "percentage": 0
  }
}
```

## Validaciones de entrada y credenciales

- JSON estricto: rechazar tipos incorrectos, campos desconocidos, claves duplicadas, obligatorios ausentes, null no permitido, enums desconocidos y JSON malformado. IDs deben resolver al catálogo; no aceptar rutas como IDs. Cuerpos ausentes donde se exige objeto son inválidos; escrituras sin cuerpo no aceptan campos extra.
- Usuario: 3–32 caracteres ASCII `[A-Za-z0-9_]`, sin recortar espacios. Conservar presentación y comparar unicidad/acceso sin distinguir mayúsculas mediante representación canónica en minúsculas; no normalizar la contraseña. Esta regla queda aprobada con el cierre de H2.
- Registro y recuperación por consola: contraseña de 15–128 puntos de código Unicode, admitiendo espacios y frases, sin exigir composición. No recortar espacios, normalizar ni truncar silenciosamente. Un sustituto UTF-16 sin pareja no es texto Unicode válido: registro y consola lo rechazan por política y login responde el 401 genérico, sin calcular su hash.
- Login: no se aplica ningún mínimo de política de contraseña, ni se exige contraseña no vacía como regla de longitud. Solo se rechaza por longitud lo que supere 128 puntos de código Unicode, contados antes del hash. Es una protección operativa del uso de Argon2, no un máximo inherente del algoritmo. Así, un futuro cambio del mínimo no bloquea cuentas existentes. No recortar, normalizar ni truncar la entrada.
- Toda credencial inválida de login, incluido usuario inexistente, formato de usuario inválido, contraseña incorrecta o superior a 128 puntos de código, devuelve siempre el mismo 401 `AUTHENTICATION_FAILED`, sin revelar la existencia del usuario. Login no devuelve 400 `INVALID_PASSWORD` ni aplica el mínimo de registro; errores de esquema/tipo JSON pueden devolver 400 `INVALID_REQUEST`.
- Usar Argon2id con Argon2PasswordEncoder y Bouncy Castle, sal por contraseña y suelo inicial de 19 MiB, 2 iteraciones y paralelismo 1. Verificar versiones y medir el coste local en H3 sin bajar ese suelo; no asumir que los valores predeterminados lo cumplen.
- Recuperación solo mediante consola local autorizada: contraseña oculta y solicitada dos veces, nunca por argumentos. Hash nuevo e incremento de `version_credenciales` en una sola transacción, conservando ID y progreso. Una caída no debe dejar contraseña nueva con sesiones anteriores válidas.

## Sesión, CSRF y entorno

- Sesión en memoria del servidor: reiniciar cierra sesiones, no borra progreso. Rotar ID al iniciar sesión; sin remember-me ni credenciales de sesión en localStorage.
- Cookie HttpOnly, SameSite=Lax, sin Domain; Secure con HTTPS, con excepción de HTTP exclusivamente local. Caducar tras 2 horas sin solicitudes autenticadas. Logout invalida en servidor, limpia cookie y elimina todo estado privado de la interfaz.
- Cada solicitud autenticada compara la versión de credenciales conservada en sesión con la actual en base de datos, sin caché obsoleta. Sesiones viejas se rechazan en su siguiente solicitud; no se cancelan retroactivamente solicitudes en vuelo.
- CSRF obligatorio para TODAS las operaciones inseguras, incluidos registro, login y logout. Token disponible antes del login y renovado después de login y logout. SameSite no sustituye CSRF.
- Preferir integración SPA oficial de Spring documentada para la versión elegida. `csrf.spa()` verificado en H3 y en uso. `/api/auth/csrf` es solo ruta condicional si la integración nativa soportada no basta, no una decimoquinta ruta fijada.
- En local, frontend y `/api` comparten origen mediante proxy de Vite a Spring Boot. No requiere CORS abierto ni sustituye CSRF o autorización. Todos los servicios ligados exclusivamente a loopback; en publicación se definirá el esquema de origen correspondiente.
- Fallos de autenticación genéricos, sin registrar secretos. Respuestas de autenticación y progreso sin cachés compartidas. Antes de exposición fuera de local se revisarán limitación de intentos y enumeración mediante `USERNAME_TAKEN` en H7, o antes si cambia el entorno.

## Contenido, ejercicios y recursos

Esta es una política de extracción y entrega aprobada, no una afirmación de importador implementado ni una medición del origen.

La importación extrae los ejercicios de la sección exacta «Ejercicios prácticos resueltos» de cada lección: el enunciado pasa a `Exercise.statementHtml` y la solución en `<details>` pasa a `Exercise.solutionHtml`, conservando el desplegable seguro. Ambos campos se sanean por separado. `bodyHtml` no incluye esa sección, para que los ejercicios no aparezcan dos veces. La asociación explícita del manifiesto conserva IDs permanentes de ejercicios; un ordinal transitorio no identifica el ejercicio.

- Convertir Markdown a HTML con lista permitida de elementos, atributos y protocolos antes de representarlo. Conservar `<details>`/`<summary>` seguros; excluir scripts, manejadores de eventos y enlaces ejecutables. Elegir y comprobar biblioteca en H3/H4.
- Reescribir enlaces relativos hacia rutas de lecciones por ID permanente y `/api/resources/{resourceId}`. La reescritura, el saneamiento y la validación final de enlaces, recursos y fragmentos se aplican a `bodyHtml`, `statementHtml` y `solutionHtml`, también a enlaces entre esos campos. No exponer rutas del origen.
- Generar IDs de encabezados compatibles con GitHub, incluidos duplicados, acentos y puntuación; permitir IDs solo para esos destinos, no arbitrarios. Conservar/mapear fragmentos y validar que sus destinos finales existan tras separar, limpiar y reescribir el contenido. Un enlace a lección pendiente lleva a «todavía no disponible»; una referencia no resuelta se señala en la validación, sin reasignarla silenciosamente ni fingir un fragmento validado sin cuerpo.
- Bloques Mermaid con clases de lenguaje controladas. Frontend en modo `strict`, sin HTML arbitrario ni interacciones. Ante fallo, mostrar código fuente original escapado, nunca como HTML; el resto de la lección sigue funcionando. El SVG generado no elude controles de seguridad.
- Servir recursos autenticados exclusivamente por `resourceId` registrado en manifiesto. Resolver rutas locales con comprobación canónica de contención, incluidos symlinks; rechazar IDs desconocidos y escapes. No aceptar rutas del cliente ni leer el origen al servir.
- SVG educativos con tipo correcto, `X-Content-Type-Options: nosniff` y CSP `sandbox` sin permisos adicionales. HTML educativo solo como descarga o texto escapado, nunca ejecutado en el mismo origen.

## Errores estables

Los errores incluyen `code` y `message`; la UI decide por `code`, no interpreta el texto. Sin eco de credenciales, detalles internos ni rutas.

| HTTP | `code` y condición |
|---|---|
| 400 | `INVALID_REQUEST` (JSON/campos/tipos), `INVALID_USERNAME` y `INVALID_PASSWORD` en registro, `INVALID_STATE`, `COURSE_MISMATCH`. Login inválido usa 401 genérico, salvo errores de esquema/tipo. |
| 401 | `AUTHENTICATION_FAILED` (login), `AUTHENTICATION_REQUIRED` (sesión ausente, caducada o versión de credenciales inválida). |
| 403 | `CSRF_REJECTED`, incluidas operaciones públicas inseguras. `ACCESS_DENIED`: solicitud autenticada a una ruta o acción denegada, distinta de un fallo de CSRF. |
| 404 | `NOT_FOUND`: curso/lección/ejercicio/recurso inexistente, recurso no autorizado o dato personal ajeno; sin revelar existencia ni rutas. |
| 406 | `NOT_ACCEPTABLE`: el encabezado `Accept` no admite JSON. Se rechaza antes de ejecutar la operación: sin escrituras ni sesión. Sin `Accept`, `*/*` o `application/json` son válidos. |
| 409 | `USERNAME_TAKEN`, `LESSON_UNAVAILABLE`, `COURSE_NOT_ADDED`. |
| 500 | `INTERNAL_ERROR`: fallo inesperado sin detalles internos ni secretos; escrituras transaccionales sin efectos parciales. |

Formato único de error:

```json
{
  "code": "LESSON_UNAVAILABLE",
  "message": "Lesson is not available."
}
```
