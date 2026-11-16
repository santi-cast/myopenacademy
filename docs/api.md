# API — contrato de H2 cerrado

Contrato aprobado en H2 e implementado en H4, que añadió fases, `fileName` de recursos, `ACCESS_DENIED` y 406. Mantiene las 14 rutas base y desarrolla el [plan principal](../PLAN.md). La procedencia y las asociaciones de contenido se definen en el [manifiesto](manifiesto.md). H5 añade cinco rutas de etapas, implementadas en el backend y verificadas con contenido sintético; la interfaz de etapas ya tiene índice, contenido, estado manual y continuación independiente; la copia activa ya importa 199 lecciones y 14 etapas disponibles desde el commit fijado del origen. Los recursos ZIP y de etapas disponibles usan la ruta de recursos existente. Las imágenes Markdown seguras y el tachado amplían también la carga v1, sin rutas nuevas.

## Acceso, propiedad y efectos

Todas las rutas de catálogo, contenido, recursos y progreso requieren sesión. Registro e inicio de sesión son accesibles sin autenticar. Las rutas `/me` obtienen el propietario de la sesión; no aceptan `userId`/`usuario_id` para elegir de quién son los datos. Toda lectura y escritura personal se limita al usuario autenticado, también por identificador directo. Un dato personal ajeno no se devuelve ni se modifica.

Ningún GET modifica estados, inscripción o continuación. Obtener un token CSRF podrá crear una sesión técnica, nunca progreso de aprendizaje. No hay DELETE de inscripción ni endpoint de recuperación web. H4 tampoco tiene borrado de usuarios: las claves foráneas del progreso no tienen cascada, así que borrar un usuario con progreso no está soportado y la base de datos lo rechaza.

Los nombres de formas siguientes son abreviaturas documentales, no envoltorios adicionales. Fechas `updatedAt` en UTC ISO 8601 (por ejemplo, `2026-01-01T12:00:00Z`); null si no existe estado guardado. IDs globales y permanentes, opacos para el cliente, con namespace permanente de curso para contenido. No devolver contraseñas, hashes ni versiones de credenciales.

## Formas reutilizables de salida

| Forma | Campos JSON y significado |
|---|---|
| User | `id`, `username` (nombre visible conservado). |
| Counts | `completedLessons`, `availableLessons`, `plannedLessons`, `percentage`: cifras calculadas dinámicamente desde el catálogo y los estados, nunca constantes. Porcentaje 0–100 sin redondeo contractual; null si no hay disponibles. |
| Course | `id`, `title`. En el catálogo (`GET /api/courses` y `GET /api/courses/{courseId}`), además `description` (la `descripcion` de `curso.json`, o `null`) y `recommended`: los cursos de su `conviene_saber_antes`, en orden, como `{ "id", "title" }`, con `title` solo si ese curso está instalado (si no, `null`). Son recomendaciones: nada se bloquea (H7, T6). |
| Phase | `id`, `title`, `position` (campos añadidos en H4): fase del curso con ID permanente y título literal del índice del curso de origen. `position` empieza en 1 y ordena las fases. |
| LessonEntry | `id`, `courseId`, `title`, `position`, `phaseId`, `available` (disponibilidad importada, no promesa de que todo lo escrito en origen ya se importó). `phaseId` (campo añadido en H4) es el ID de la fase del mismo curso a la que pertenece la lección; el cliente agrupa por él y nunca deduce la fase del ID de la lección. |
| Exercise | `id`, `lessonId`, `courseId`, `title`, `statementHtml`, `solutionHtml` (campos saneados por separado; solución libremente desplegable). |
| Resource | `resourceId`, `url`, `mediaType`, `fileName`; URL controlada `/api/resources/{resourceId}`, sin ruta física. `fileName` (campo añadido en H4) es el nombre original del archivo, por ejemplo `Hello.java`: el que se muestra y con el que se descarga, nunca el nombre interno basado en el ID. |
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
| GET /api/courses | — | 200, `{ "courses": Course[] }`, en el mismo orden que «Mis cursos»: primero el curso inicial (`code-academy.first-course`) y después por título (decisión del autor, 16 de noviembre de 2026). |
| GET /api/courses/{courseId} | — | 200, `{ "course": Course, "phases": Phase[], "lessons": LessonEntry[] }`, temario completo ordenado: fases por `position` y lecciones por `position`; las lecciones de cada fase son consecutivas y siguen el orden de las fases. |
| GET /api/lessons/{lessonId} | — | 200, `{ "lesson": LessonEntry, "bodyHtml": string, "exercises": Exercise[], "resources": Resource[], "previousLessonId": ID o null, "nextLessonId": ID o null }`; vecinos disponibles del mismo curso. |
| GET /api/resources/{resourceId} | — | 200, bytes con tipo seguro del recurso, no JSON; HTML solo descarga/texto escapado y SVG con sandbox según política de contenido. Solo recursos del manifiesto cuyo único propietario (lección o etapa) está disponible; el resto, 404. El encabezado `Accept` no la bloquea. |
| GET /api/me/courses | — | 200, `{ "courses": [{ "course": Course, "enrollment": Enrollment }] }`; solo los cursos añadidos, primero el curso inicial (`code-academy.first-course`, variable `CODE_ACADEMY_FIRST_COURSE`, por defecto `inicial`) y después los demás por título según el orden del español (la «Á» con la «A»), y por ID si dos títulos son iguales; se ordena en el backend, igual con cualquier intercalación de la base (decisión del autor, 16 de noviembre de 2026); lista vacía si no hay ninguno. |
| PUT /api/me/courses/{courseId} | Sin cuerpo; cualquier cuerpo, incluso `{}`, es 400 `INVALID_REQUEST` | 200, Enrollment con `added: true`; repetición no reinicia continuación ni estados. |
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
- En los cuerpos de estado, un error de forma (tipo, campo, null, cuerpo ausente o malformado) es 400 `INVALID_REQUEST`; una cadena que no es un estado de ese tipo es 400 `INVALID_STATE`, también un estado del otro tipo (`solved` para una lección, `completed` para un ejercicio) o con otra grafía (`Completed`). Un segmento de ruta no normalizado (`..`, `%2e%2e`) se rechaza con 400 `INVALID_REQUEST` antes de llegar a ninguna ruta; un ID que no existe tal cual, sin recortar ni ignorar mayúsculas, es 404 `NOT_FOUND`.
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
- El login y el logout expiran la cookie `XSRF-TOKEN`; el token nuevo llega con la siguiente respuesta. Antes de cada operación insegura, si falta la cookie, el frontend la obtiene con `GET /actuator/health`, la única ruta pública sin sesión que la crea (nunca con `/api/me`, que sin sesión responde 401). **Dependencia para H7:** restringir, mover o cambiar de prefijo el actuator al publicar rompe el login y el registro, salvo que se mantenga esa ruta pública o se añada la ruta condicional `/api/auth/csrf`.
- En local, frontend y `/api` comparten origen mediante proxy de Vite a Spring Boot. No requiere CORS abierto ni sustituye CSRF o autorización. Todos los servicios ligados exclusivamente a loopback; en publicación se definirá el esquema de origen correspondiente.
- Fallos de autenticación genéricos, sin registrar secretos. Respuestas de autenticación y progreso sin cachés compartidas. Antes de exposición fuera de local se revisarán limitación de intentos y enumeración mediante `USERNAME_TAKEN` en H7, o antes si cambia el entorno.

## Contenido, ejercicios y recursos

Política de extracción y entrega implementada en H4 para la lección importada; no es una medición del origen. H5 añade el importador offline completo; véase el [manifiesto](actualizacion-contenido.md).

La importación extrae los ejercicios de la sección exacta «Ejercicios prácticos resueltos» de cada lección: el enunciado pasa a `Exercise.statementHtml` y la solución en `<details>` pasa a `Exercise.solutionHtml`, conservando el desplegable seguro. Ambos campos se sanean por separado. `bodyHtml` no incluye esa sección, para que los ejercicios no aparezcan dos veces. La asociación explícita del manifiesto conserva IDs permanentes de ejercicios; un ordinal transitorio no identifica el ejercicio. Cada ejercicio se asocia por su título exacto ([regla del manifiesto](manifiesto.md#cuerpos-ejercicios-y-enlaces)), y `Exercise.title` es ese mismo texto plano, idéntico al guardado en el catálogo. Un ID de elemento repetido en cualquier campo HTML de la lección rechaza la importación.

- Convertir Markdown a HTML con lista permitida de elementos, atributos y protocolos antes de representarlo. Conservar `<details>`/`<summary>` seguros; excluir scripts, manejadores de eventos y enlaces ejecutables. Se usa commonmark 0.31.0 (tablas y extensión GFM de tachado de la misma versión) y OWASP Java HTML Sanitizer 20261011.1. `~~texto~~` produce `<del>` también en enunciados y soluciones; dentro de código sigue siendo texto literal.
- Reescribir enlaces relativos hacia rutas de lecciones por ID permanente y `/api/resources/{resourceId}`. La reescritura, el saneamiento y la validación final de enlaces, recursos y fragmentos se aplican a `bodyHtml`, `statementHtml` y `solutionHtml`, también a enlaces entre esos campos. No exponer rutas del origen.
- Las imágenes Markdown se resuelven desde el documento fuente mediante `sourceToId`, exclusivamente a recursos registrados de tipo `image/png`, `image/jpeg` o `image/svg+xml`. Solo sobreviven `img` con `src` generado `/api/resources/{resourceId}` y `alt` textual escapado. Se rechazan imágenes externas, `data:`, rutas absolutas, consultas, fragmentos, recursos no gráficos y referencias sin resolver. Una URL `/api/resources/...` escrita directamente no sustituye la resolución registrada. El HTML crudo no puede introducir imágenes, ni siquiera reutilizando la URL de una imagen Markdown válida del mismo campo. Títulos y otros atributos de imagen no están permitidos. Estas comprobaciones también rigen en enunciados y soluciones.
- Generar IDs de encabezados compatibles con GitHub, incluidos duplicados, acentos y puntuación; permitir IDs solo para esos destinos, no arbitrarios. Conservar/mapear fragmentos y validar que sus destinos finales existan tras separar, limpiar y reescribir el contenido. Un enlace a lección pendiente lleva a «todavía no disponible»; una referencia no resuelta se señala en la validación, sin reasignarla silenciosamente ni fingir un fragmento validado sin cuerpo.
- Un fragmento que apunta al encabezado de un ejercicio extraído se reescribe al ID del ejercicio (por ejemplo, `#java-02.01-e08`, o `/lessons/{lessonId}#java-02.01-e08` desde otra lección). El frontend representa cada ejercicio en un contenedor cuyo `id` es el ID del ejercicio.
- Bloques Mermaid con clases de lenguaje controladas. Frontend en modo `strict`, sin HTML arbitrario ni interacciones. Ante fallo, mostrar código fuente original escapado, nunca como HTML; el resto de la lección sigue funcionando. El SVG generado no elude controles de seguridad. Un diagrama con directivas (`%%{…}%%`), frontmatter, interacciones (`click`, `link`, `links`, `callback`, `href`) o imágenes e iconos (`@{ img/icon }`) no se dibuja: se muestra como código fuente escapado. El SVG dibujado se revisa en un documento aparte antes de insertarlo (sin enlaces, `foreignObject`, imágenes, scripts, manejadores de eventos ni `url()` externas); si no pasa, también se muestra el código fuente.
- Servir recursos autenticados exclusivamente por `resourceId` registrado en manifiesto. Resolver rutas locales con comprobación canónica de contención, incluidos symlinks; rechazar IDs desconocidos y escapes. No aceptar rutas del cliente ni leer el origen al servir.
- SVG educativos con tipo correcto, `X-Content-Type-Options: nosniff` y CSP `sandbox` sin permisos adicionales. HTML educativo solo como descarga o texto escapado, nunca ejecutado en el mismo origen.
- Cada recurso se entrega con el `mediaType` del manifiesto (`text/plain; charset=utf-8` para `.java`) y el nombre original codificado de forma segura. PNG/JPEG se entregan con `Content-Disposition: inline`; los demás conservan `attachment`, por ejemplo `attachment; filename="Hello.java"; filename*=UTF-8''Hello.java`. Todos mantienen `X-Content-Type-Options: nosniff`, `Content-Security-Policy: sandbox` y `Cache-Control: private, no-store`. El archivo se relee desde la ruta validada, comprobando de nuevo la contención canónica y la ausencia de symlinks.
- Los enlaces absolutos `http`/`https` del HTML de lección llevan `target="_blank"` junto con `rel="noopener noreferrer"`, fijados por la política de saneamiento. Los enlaces internos, de fragmento y a `/api/resources` no llevan `target`.

## Errores estables

Los errores incluyen `code` y `message`; la UI decide por `code`, no interpreta el texto. Sin eco de credenciales, detalles internos ni rutas.

| HTTP | `code` y condición |
|---|---|
| 400 | `INVALID_REQUEST` (JSON/campos/tipos), `INVALID_USERNAME` y `INVALID_PASSWORD` en registro, `INVALID_STATE`, `COURSE_MISMATCH`. Login inválido usa 401 genérico, salvo errores de esquema/tipo. |
| 401 | `AUTHENTICATION_FAILED` (login), `AUTHENTICATION_REQUIRED` (sesión ausente, caducada o versión de credenciales inválida). |
| 403 | `CSRF_REJECTED`, incluidas operaciones públicas inseguras. `ACCESS_DENIED`: solicitud autenticada a una ruta o acción denegada, distinta de un fallo de CSRF. |
| 404 | `NOT_FOUND`: curso/lección/etapa/ejercicio/recurso inexistente, recurso no autorizado o dato personal ajeno; sin revelar existencia ni rutas. |
| 406 | `NOT_ACCEPTABLE`: el encabezado `Accept` no admite JSON. Se rechaza antes de ejecutar la operación: sin escrituras ni sesión. Sin `Accept`, `*/*` o `application/json` son válidos. Se evalúan los tipos de medio, no los valores de calidad: `application/json;q=0` se trata como aceptable. |
| 409 | `USERNAME_TAKEN`, `LESSON_UNAVAILABLE`, `STAGE_UNAVAILABLE`, `COURSE_NOT_ADDED`. |
| 500 | `INTERNAL_ERROR`: fallo inesperado sin detalles internos ni secretos; escrituras transaccionales sin efectos parciales. |

Formato único de error:

```json
{
  "code": "LESSON_UNAVAILABLE",
  "message": "Lesson is not available."
}
```

## H5: contrato aditivo de etapas pendiente de implementar

Las cinco rutas y su persistencia ya están implementadas en el backend: 57 pruebas aisladas verifican catálogo, estados, rechazos sin cambios, migración poblada V5→V6, reinicio y concurrencia. El encabezado conserva el ancla existente; la interfaz consume esas rutas y la copia activa ya trae las 14 etapas reales disponibles. La migración V6 es aditiva: crea catálogo y estados de etapas y añade una continuación nullable a la inscripción existente. Las pruebas usan PostgreSQL desechable, no la base del propietario.

Proyecto evolutivo tiene una sección propia dentro de Java, con un inventario aprobado de 14 etapas escritas en `c8e29163d59f41830eff2d449bf8b02a19905f61`. Las 14 están importadas y disponibles en la copia activa; la UI sigue mostrando la disponibilidad efectiva que devuelve la API, no la deduce del inventario. No es una fase ni amplía las 199 lecciones previstas. Las 14 rutas H4, sus respuestas y los enums de lecciones/ejercicios conservan su significado; las etapas se consultan por rutas nuevas. No se añade progreso de etapas a `Counts` ni se cambia el denominador de lecciones.

### Formas y rutas nuevas

| Forma | Campos JSON y significado |
|---|---|
| StageEntry | `id`, `courseId`, `title`, `position`, `available`; ID permanente, posición desde 1 dentro del proyecto. |
| StageState | `stageId`, `state`, `updatedAt`; estado JSON `not_completed`/`completed`, igual que lecciones. Equivalentes conceptuales `NOT_COMPLETED`/`COMPLETED`; conservar la representación persistida de lecciones (`not_completed`/`completed`) y no aceptar mayúsculas en JSON. |
| StageContinuation | `lastOpenedStageId`: ID o null, independiente de `Continuation.lastOpenedLessonId`. |
| StageCounts | `completedStages`, `availableStages`, `plannedStages`, `percentage`; solo etapas disponibles en numerador/denominador del porcentaje, null si no hay disponibles. |
| StageProgress | `courseId`, `added`, `stageStates` (StageState[] por posición), `continuation` (StageContinuation), `progress` (StageCounts). |
| StageStateResult | `courseId`, `stageId`, `state`, `updatedAt`, `courseAutoAdded`, `continuation` (StageContinuation), `progress` (StageCounts). |

| Ruta nueva | Entrada JSON | HTTP y salida |
|---|---|---|
| GET /api/courses/{courseId}/stages | — | 200, `{ "courseId": ID, "stages": StageEntry[] }`, ordenadas por posición, incluidas pendientes; array vacío válido para curso sin proyecto. |
| GET /api/stages/{stageId} | — | 200, `{ "stage": StageEntry, "bodyHtml": string, "resources": Resource[], "previousStageId": ID o null, "nextStageId": ID o null }`; vecinos disponibles del mismo curso. Sin ejercicios evaluados ni estados implícitos. |
| GET /api/me/courses/{courseId}/stage-progress | — | 200, StageProgress, también sin inscripción; 404 si no existe el curso. |
| PUT /api/me/stages/{stageId}/state | `{ "state": "completed" }` | 200, StageStateResult; admite también `not_completed`. |
| PUT /api/me/courses/{courseId}/stage-continuation | `{ "stageId": "java-stage-initial" }` | 200, StageProgress confirmada; no crea inscripción. |

La interfaz ofrece la sección desde el índice de Java en `/courses/{courseId}/project` y navega a `/stages/{stageId}`, separada de `/lessons/{lessonId}`. Tras cargar correctamente una etapa, solo si `added: true`, llama al PUT de continuación de etapas; no al de lecciones ni durante precarga/exploración. «Retomar proyecto» usa la última etapa; la continuación H4 sigue retomando la última lección. Pendientes visibles, sin apertura.

### Inscripción, estados y aislamiento

- Reutilizar la inscripción usuario–curso existente y la identidad de sesión. No crear otra inscripción de proyecto ni aceptar propietario por cuerpo/ruta/query. Estado único por inscripción y etapa, con pertenencia al mismo curso; continuación de etapa opcional en esa inscripción, con restricción compuesta al curso como en lecciones. Sin cascadas que borren progreso.
- Sin inscripción: `added: false`, `stageStates: []`, `continuation: { "lastOpenedStageId": null }`, completadas 0. Estado ausente significa no completada, sin insertar filas. Añadir explícitamente el curso conserva el comportamiento H4 y deja también vacía la continuación de etapas.
- Marcar una etapa disponible crea inscripción si falta, guarda estado e inicializa última etapa en una sola transacción; deja última lección vacía. Reutilizar/ampliar la operación de inscripción existente (`ProgressService.enroll`), con su unicidad y manejo de concurrencia, no duplicar su SQL por otra vía. Una inscripción creada por lección/ejercicio deja última etapa vacía. Si ya existe, marcar no cambia ninguna continuación.
- Abrir contenido, consultar recursos o soluciones nunca completa etapas. Los dos estados son siempre manuales, sin corrección automática, dependencias de lecciones ni orden obligatorio. Repetir el estado conserva `updatedAt` del último cambio real; repetir una adición no reinicia estados ni continuaciones.
- PUT de continuación valida curso, etapa y disponibilidad antes de escribir. Sin inscripción: 409 `COURSE_NOT_ADDED`. Una etapa de otro curso: 400 `COURSE_MISMATCH`. Abrir, marcar o continuar una pendiente: 409 `STAGE_UNAVAILABLE`, mensaje estable `Stage is not available.`; su entrada de índice sí se devuelve. IDs inexistentes o personales ajenos: 404 `NOT_FOUND`, sin revelar datos.
- Las rutas nuevas heredan autenticación, CSRF en cada PUT, JSON estricto, 400 `INVALID_REQUEST` por forma y 400 `INVALID_STATE` por enum desconocido (`pending`, `solved`, `COMPLETED` o `Completed` incluidos). Heredan negociación JSON, errores y caché privada de H4. Todo rechazo deja intactos inscripción, estados, fechas, ambas continuaciones y contadores de ambos recorridos.

Ejemplo ilustrativo de StageProgress sin inscripción y con una etapa disponible de dos previstas; no es el inventario real:

```json
{
  "courseId": "java",
  "added": false,
  "stageStates": [],
  "continuation": { "lastOpenedStageId": null },
  "progress": {
    "completedStages": 0,
    "availableStages": 1,
    "plannedStages": 2,
    "percentage": 0
  }
}
```

## H5: extensión de recursos pendiente de implementar

La entrega de ZIP y recursos de etapas de esta sección ya está implementada; se conserva el ancla documental para enlaces existentes. Las rutas nuevas de etapas están implementadas en el backend y consumidas por la interfaz; el contenido real (199 lecciones y 14 etapas, con sus recursos y ZIP) ya está importado.

Estado de T2: imágenes Markdown y tachado implementados sobre v1/v2; ZIP v2 y recursos de etapas disponibles implementados con pruebas aisladas de carga y HTTP para dos sesiones sin inscripción. La carga rechaza ZIP no canónicos aunque su hash declarado coincida, sin descomprimirlos ni consultar el origen. La verificación independiente comprobó en Chromium la carga de PNG/JPEG y SVG desde el backend real con PostgreSQL desechable, para dos usuarios autenticados, sin interceptar ni relajar encabezados. Las solicitudes anónimas devolvieron 401 y las imágenes no cargaron. Esto verifica la entrega de recursos, no el recorrido completo de lección en React ni otros navegadores.

Se reutiliza `GET /api/resources/{resourceId}`, no se acepta una ruta de archivo ni una URL del cliente. `Resource` conserva sus cuatro campos; ZIP añade un recurso de `mediaType: "application/zip"` con nombre original de carpeta más `.zip`. En v2, el propietario registrado puede ser lección o etapa disponible; 404 para propietario pendiente, recurso desconocido o archivo que falla contención. No se exige inscripción para explorar, sí sesión para cada descarga o imagen.

La resolución parte del documento fuente y de `sourceToId`, como en H4; imágenes y ZIP amplían ese resolutor, no incorporan uno paralelo. El [manifiesto v2](manifiesto.md#recursos-zip-y-procedencia) fija miembros explícitos, procedencia y reescritura de carpetas. Se validan enlaces/fragmentos e imágenes en todos los campos HTML antes de ofrecer el contenido. Se conserva la política de saneamiento y Mermaid; el tachado GFM ya genera el elemento permitido `<del>`.

PNG/JPEG registrados para imagen admiten `Content-Disposition: inline` con nombres seguros y tipo validado; los demás recursos mantienen `attachment`, incluidos ZIP y SVG con CSP `sandbox`. Las imágenes SVG solo se usan en contexto `img`, nunca como HTML/SVG insertado en el DOM ni `object`/`iframe`; su visualización autenticada se comprobó en Chromium sin relajar el sandbox. Sin imágenes externas, `data:`, scripts ni manejadores. Todos conservan `nosniff`, `Cache-Control: private, no-store`, comprobación canónica de archivo sin symlinks al servir y acceso por ID. Los ZIP se entregan como `application/zip`, `attachment`, con el nombre original de carpeta más `.zip` codificado por el mismo helper seguro de los otros archivos. Se entregan ya generados y validados desde la copia controlada, nunca desde el origen ni por recorrido dinámico de directorios.

Importar/actualizar no es una operación HTTP ni de estudio: véase el [límite offline de comparación y aplicación](actualizacion-contenido.md).
