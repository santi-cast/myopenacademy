# MyOpenAcademy — Plan de la academia

> Plan de la plataforma de **MyOpenAcademy**, reescrito con el autor el 9 de noviembre de 2026 tras la prueba de importación de la fase 1 del curso inicial. Se suma al plan común (`../PLAN.md`, sobre todo los apartados 7, 8.5 y 8.6) y al contrato de los cursos (`../Cursos/kit-cursos/FORMATO.md`). Dónde estamos: [`ESTADO.md`](ESTADO.md).

**Estado:** H1 a H5 cerrados (de Code Academy). **Siguiente: H7, cursos como DLC.** H6 (uso real) va en paralelo, de forma continua. Sin remotos hasta publicar.

## 1. Qué es

La plataforma de una academia de programación **libre, gratuita y de código abierto** que funciona como **un juego con DLC**: la academia es el juego y cada curso es una expansión que se instala con su versión, se actualiza sin perder el progreso y que cualquiera puede crear. El **pack base 1.0** es la academia más el DLC del curso inicial («Fundamentos para programar»), publicado en `santi-cast/myopenacademy`.

Viene de **Code Academy**, clonado con su historial el 8 de noviembre de 2026. El original (`../../Code Academy/`) no se toca y sigue funcionando: **el autor estudia en él el curso de Java** mientras se construye esta academia (no se arrancan los dos a la vez, porque comparten el puerto `5433` hasta H10).

## 2. Decisiones confirmadas

**Heredadas de Code Academy, que siguen valiendo:**

- Una sola plataforma para todos los cursos; varios usuarios con cuentas y avance propio desde la primera versión.
- Frontend con React, Vite y TypeScript; backend con Java 25 y Spring Boot 4.1.2; PostgreSQL 18.6 en Compose. Detalle en el apartado 8.
- Los estados de lección, ejercicio y etapa los marca el alumno a mano; nada se bloquea por orden (apartado 7).
- El contenido se instala desde un commit fijo, sin conexión y sin leer nunca el curso en directo; comparar antes de aplicar.
- Todo el aspecto sale de `frontend/src/styles/tokens.css`, y el test que lo comprueba no se desactiva.

**De MyOpenAcademy** (`../PLAN.md` y `CLAUDE.md`):

- **Ningún curso escrito en el código:** catálogo, recomendaciones y roadmaps salen del contenido instalado.
- **La identidad la guarda el curso** (`curso.json`): el importador lee sus identificadores **tal cual, con punto** (`inicial.que-es-programar`), sin traducirlos ni inventarlos. Comprobado el 9 de noviembre de 2026 que el punto no da problemas en la base, en Spring ni en el frontend (`ESTADO.md`).
- **Manda la academia, pero el contrato vive en el kit:** si la academia necesita cambiar `FORMATO.md`, se propone al autor y lo aplica la sesión del kit.
- **Diseñada como si fuera a desplegarse en internet:** todo lo que cambia entre niveles, en variables de entorno.
- **Registro abierto o cerrado en el 1.0;** por invitación, después.
- **El idioma del README** se decide antes de H11 (`../PLAN.md` 10, decisión 11).

## 3. Hitos

Los números de la columna «Cambios» son los de la lista de `ESTADO.md` («Lista de cambios hasta el pack base 1.0»). Estimación orientativa, con pruebas y sin las revisiones del autor: **unos 20 días** hasta el pack base 1.0.

| Hito | Estado | Qué | Cambios | Estimación | Se cierra cuando… |
|---|---|---|---|---|---|
| H1. Definición | Cerrado | Alcance, progreso y exclusiones | — | — | (Code Academy) |
| H2. Diseño | Cerrado | Pantallas, modelo, API, arquitectura y manifiesto | — | — | (Code Academy) |
| H3. Base técnica | Cerrado | Frontend, backend, entorno local y pruebas | — | — | (Code Academy) |
| H4. Primer recorrido | Cerrado (`a3d3976`) | Cuentas, una lección y avance persistente | — | — | (Code Academy) |
| H5. Contenido disponible | Cerrado (`1333ed8`) | Curso de Java entero, etapas, recursos y actualización comparada | — | — | (Code Academy) |
| **H6. Uso real** | Continuo | El autor estudia y anota lo que encuentra; se revisa al empezar cada hito y tiene prioridad sobre lo nuevo | 23 | Tiempo del autor | No queda ningún problema que bloquee el 1.0 |
| **H7. Cursos como DLC** | Siguiente | Importador nuevo que lee `curso.json` | 1, 2, 3, 4, 5, 8, 13 | ~7 días | La fase 1 del inicial y el curso de ejemplo se instalan desde `../Cursos/…` con su etiqueta; `content/java/` sale del repositorio; las pruebas pasan |
| **H8. Contenido según el formato** | Pendiente | Lo que el formato pide mostrar | 6, 7, 9, 10, 11 | ~3,5 días | Retos, glosario, enlaces entre cursos, proyecto y «Mejorar esta lección» se ven en el navegador y el autor da el visto bueno |
| **H9. Cuentas y administración** | Pendiente | Roles, panel de administración y perfil | 14, 15, 16 | ~4 días | Un administrador creado por consola gestiona el registro, los usuarios y el nombre de la instancia; un usuario cambia su contraseña |
| **H10. Instalación** | Pendiente | Variables de entorno, un comando, red local y marca | 17, 18, 19, 20 | ~4,5 días | Con un comando arranca en la máquina del autor y se usa desde otro dispositivo de casa; nada de la máquina escrito en el código |
| **H11. Pack base 1.0** | Pendiente | Publicación en GitHub | 21, 22 y el curso inicial completo con su etiqueta | ~2 días | `santi-cast/myopenacademy` publicado con su versión y *release* |

**Por qué este orden:** H7 va primero porque el curso inicial lo necesita para probarse fase a fase mientras se escribe. H9 y H10 no dependen del contenido: si el curso va por detrás, se adelantan. H11 espera al curso inicial completo, lo único del calendario que no depende de esta academia.

## 4. Qué lleva cada hito

### H6. Uso real

- El autor estudia con la academia (el curso inicial según se vaya instalando; Java, en el Code Academy original) y anota lo que encuentra.
- Al empezar cada hito se revisan las notas: lo que bloquee el 1.0 entra en el hito; lo demás, a la lista de después del 1.0.

### H7. Cursos como DLC

- **Curso de ejemplo pequeño:** un solo curso de ejemplo para el kit y la academia (decisión del autor, 9 de noviembre de 2026). Es el curso «bueno» de las pruebas del kit (`../Cursos/kit-cursos/herramientas/pruebas/cursos/bueno/`), que vive allí; la academia guarda en el repositorio **una copia sacada de un commit fijo del kit** (solo comandos de Git de lectura), con ese commit anotado, y la renueva cuando haga falta. Lo que le falte se pide al kit. Las pruebas dejan de usar el curso de Java real.
- **Importador que lee `curso.json`** y da de alta cursos nuevos. El manifiesto interno se genera desde `curso.json` (punto de partida: `tools/curso_a_manifiesto.py`, más el cálculo de las anclas); no lee las filas del índice ni inventa identificadores. Los módulos con `"disponible": false` entran como pendientes.
- **Identificadores tal cual**, con el prefijo `<curso>.` y la forma de `FORMATO.md` 3.3.
- **`--source` con rutas relativas y `..`** (`../Cursos/curso-inicial`), conservando las comprobaciones de enlaces simbólicos, `.env` y solapamiento.
- **Versión:** instalar desde una etiqueta, resolverla al commit y guardar las dos.
- **Varios cursos instalados**, catálogo con descripción y recomendaciones, y **`content/` fuera de Git**: `content/java/` sale del repositorio, sin mantener la lectura del manifiesto antiguo.
- **Lista de cursos instalados** en el repositorio (curso, versión y commit) y un comando que la reinstala.

### H8. Contenido según el formato

- **Retos de fase:** en el índice, al final de cada fase, «Retos de la fase» (opcionales, sin seguimiento), como hoy las etapas; el «Siguiente» del último módulo lleva a ellos; ocultos mientras `"retos_disponibles": false`.
- **Glosario** como documento del curso sin seguimiento.
- **Enlaces entre cursos:** la dirección de GitHub se convierte en enlace a la lección si ese curso está instalado; si no, se queda.
- **Proyecto** desde `curso.json`, de tipo «modulo» o por etapas.
- **Botón «Mejorar esta lección»** con el `repositorio` del curso y la ruta del fichero.

### H9. Cuentas y administración

- **Roles** de administrador y usuario; el administrador se crea **por consola** al instalar, nunca con usuario y contraseña por defecto.
- **Panel:** registro abierto o cerrado; usuarios (listar, desactivar, restablecer contraseña); cursos instalados y su versión (solo verlos); nombre de la instancia.
- **Perfil:** cambiar la contraseña.

### H10. Instalación

- **Todo en variables de entorno:** dirección y puerto de escucha, puerto y nombre de la base (deja de chocar el `5433` con el original), dirección pública y carpeta de contenido, documentadas en `.env.example`.
- **Un solo comando:** el backend sirve el frontend compilado (un origen y un puerto) y Compose levanta la base y la aplicación. El servidor no toma `.algo` como extensión, por los identificadores con punto.
- **Red local:** escuchar en la red con una variable, documentar la IP fija o el nombre `.local` y avisar de que sin HTTPS las contraseñas viajan sin cifrar.
- **Nombre y logo en un solo sitio**, con el nombre de la instancia del panel por encima.

### H11. Pack base 1.0

- **README del portfolio** (`../PLAN.md` 8.5), en el idioma que decida el autor.
- **Revisión antes de publicar** (`../PLAN.md` 8.6): `gitleaks` sobre todo el historial, rutas absolutas en `odd/tasks/`, licencias de las dependencias (elkjs, dompurify), `LICENSE` (MIT), `CONTRIBUTING.md`, `CHANGELOG.md`, documentación sin voseo, `npm audit`.
- **El curso inicial completo**, instalado desde su etiqueta.
- Versión etiquetada, primer `push` y *release*, con la autorización del autor.

## 5. Después del 1.0

Sin orden fijo (`../PLAN.md` 8.5): partes de los cursos y su avance (las necesita el curso de Java, primer DLC aparte) · equivalencias entre versiones · preguntas de repaso como test · datos de la cabecera (tiempo, nivel, «Conviene saber antes») como datos y duración de cada curso · registro por invitación · roadmaps · instalar cursos desde GitHub y desde el panel · guía «Crea tu propio curso» · búsqueda, notas, tamaño de letra, atajos, retomar en la sección exacta · corrección automática · **publicación en internet** (dominio, HTTPS, copias de seguridad, límite de intentos, CSP y lo del apartado 9).

**Fuera:** certificados, pagos, mentorías, ejecutar el código del alumno en el servidor y aplicaciones móviles nativas.

## 6. Forma de trabajar

Se mantiene la de Code Academy, salvo que el autor diga otra cosa:

- **Una rama por hito** (`feat/h7-…`), con commits pequeños y revisables; **el autor revisa antes de integrar** en `main` con `--no-ff`. Los cambios sueltos de documentación o mantenimiento pueden ir directos a `main`.
- **Tareas y evidencias** de cada hito en `odd/tasks/`; las pruebas acompañan al comportamiento.
- **Pruebas:** `./mvnw -B verify` (Testcontainers, base aislada), `npm run test -- --run`, `npm run lint`, `npm run build` y el recorrido `npm run test:e2e:journey`. Las pruebas de importación y de navegador con contenido provisional, siempre contra una **base desechable**, nunca contra la de `myopenacademy`: el backend no arranca si desaparece un identificador ya guardado.
- **Nunca** se usan el `.env`, los contenedores ni el volumen del Code Academy original. No se borran volúmenes (`down -v`).
- **Coordinación** con las sesiones del kit y del curso inicial a través del autor, como dice `CLAUDE.md`.
- **Sin remotos ni `push`** hasta H11.

## 7. Base heredada: recorrido y progreso (H1 a H5)

> De Code Academy. Sigue valiendo; donde dice «Java», léase «el curso». Lo que cambia con los hitos nuevos se dice en cada uno.

### Requisitos y recorrido confirmados

Implementado en H4 y probado de principio a fin con dos usuarios.

1. **Cuenta y acceso:** registro abierto en local con usuario y contraseña. Catálogo y contenido requieren sesión; registro y login son accesibles sin autenticar.
2. **Recuperación:** herramienta de consola local para restablecer contraseña conservando ID interno y progreso. No se creará cuenta administradora automática. Se revisará el mecanismo antes de publicar en internet.
3. **Mis cursos:** añadir Java explícitamente o automáticamente al marcar estado de una lección o ejercicio de un curso todavía no añadido.
4. **Exploración:** consultar lecciones disponibles sin añadir el curso no guarda progreso ni continuación. Los registros técnicos de diagnóstico no se usarán como seguimiento de estudio.
5. **Índice:** temario completo y navegación libre. Módulos pendientes visibles pero no se pueden abrir.
6. **Lecciones y soluciones:** ejemplos y recursos de la copia importada, sin depender del origen. Soluciones en desplegables libres, sin confirmación obligatoria; abrirlas no completa ejercicios ni registra «solución consultada».
7. **Práctica:** resolver ejercicios en IDE local y marcar estado manualmente. No se ejecutará código del alumno en servidor ni navegador. Abrir una lección no equivale a completarla ni consultar solución a resolver un ejercicio.
8. **Continuación:** solo cursos añadidos recuerdan la última lección abierta. Si marcar un estado añade el curso, se establece atómicamente la continuación en esa lección, también para un ejercicio suyo. El frontend solo llama al PUT de continuación si el progreso indica `added: true`; durante exploración no lo llama.

### Reglas de progreso

| Elemento | Estados | Quién los cambia |
|---|---|---|
| Lección | no completada / completada | El alumno, manualmente |
| Ejercicio | pendiente / en curso / resuelto | El alumno, manualmente |
| Etapa del Proyecto evolutivo (H5) | no completada / completada | El alumno, manualmente |

- No existe «leída». «No completada» permite haber estudiado parte sin terminarla. Se pueden corregir estados sin secuencia obligatoria.
- Completar lección no exige resolver ejercicios. El progreso representa declaraciones del alumno, no evaluación automática.
- Abrir lección no cambia su estado; solo actualiza continuación si el curso está añadido, mediante escritura explícita, nunca GET. Explorar y consultar soluciones no modifica estados.
- Avance: lecciones completadas entre disponibles, con cifras absolutas, porcentaje y total previsto. Ejemplo ilustrativo, no recuento real: «12 de 129 lecciones disponibles completadas · 9,3 % · 199 previstas».
- Nuevas lecciones disponibles pueden reducir el porcentaje sin borrar trabajo. Si no hay disponibles, mostrar «sin lecciones disponibles» y no dividir por cero. Las fechas corresponden a cambios guardados de estado, no visitas.
- Sin registro personal, lección y etapa no completadas, ejercicio pendiente; no precargar estados al añadir un curso.
- H5 incorpora Proyecto evolutivo como sección propia de Java, no como fase ni lecciones adicionales: mantiene las 199 lecciones previstas. Estados y última etapa abierta se guardan separados de lecciones y ejercicios, con la misma inscripción y propietario; véase el [contrato aditivo de etapas](docs/api.md#h5-contrato-aditivo-de-etapas-pendiente-de-implementar).

H1 quedó cerrado con estas reglas, H2 definió su seguridad, H3 validó la base técnica y H4 las implementa.

## 8. Base heredada: arquitectura, datos y seguridad (H2 a H5)

**Diseño confirmado por el propietario; base técnica validada en H3 y funciones de negocio implementadas en H4.** Las versiones se fijan exactas tras aprobación del propietario; cualquier sustitución futura requiere justificación y revisión.

| Responsabilidad | Decisión o estado |
|---|---|
| Frontend | React + Vite + TypeScript; no Next.js. `react-router` 8.5.1; `mermaid` 12.2.0 y `highlight.js` 11.12.0 cargados solo en la vista de lección. |
| Componentes y estilos | CSS propio, sin Tailwind, shadcn/ui ni librería de componentes. Colores, fuentes y espaciados salen solo de `frontend/src/styles/tokens.css`, según el diseño aprobado [`04-mezcla.html`](design/h4-visual-style/04-mezcla.html); un test lo comprueba. |
| Backend | Java 25 + Spring Boot 4.1.2, Maven 3.10.0 mediante Wrapper; base compatible y validada. Una aplicación modular, no microservicios. |
| Comunicación | API REST; contrato documental aprobado. |
| Base de datos | PostgreSQL con imagen postgres:18.6 en Docker Compose, entorno y datos propios. |
| Persistencia | Spring Data JPA y migraciones versionadas Flyway con soporte PostgreSQL. |
| Seguridad | Spring Security con denegación por defecto, CSRF SPA y Argon2id con Bouncy Castle. Cuentas (registro, login, logout, versión de credenciales en cada solicitud, `ACCESS_DENIED` distinto de `CSRF_REJECTED`) y recuperación por consola implementadas en H4. |
| Contenido | **Hasta H7:** copia controlada en `content/java/` (manifiesto v2) desde el commit fijado `c8e29163d59f41830eff2d449bf8b02a19905f61`: 199 lecciones disponibles en 21 fases y 14 etapas del Proyecto evolutivo, con recursos y ZIP de carpetas. La copia de H4 (`0c2df357c6628784df6b537df8950da540096a8c`, solo la lección 02.01) quedó retenida en `content.previous`. Markdown procesado en el backend al arrancar con commonmark 0.31.0 (+ tablas GFM 0.31.0) y OWASP Java HTML Sanitizer 20261011.1. |
| Estudio y progreso | Inscripción, estados de lección y ejercicio, continuación y avance por usuario (migraciones V2–V5). |
| Pruebas | Vitest y React Testing Library; JUnit y Testcontainers con PostgreSQL real; Playwright para recorridos completos. |

Monorepo con `frontend/`, `backend/`, `content/` (copia controlada y manifiesto) y `docs/`. El backend separa cuentas/acceso, catálogo/contenido y estudio/progreso. No se incorpora Prisma ni Auth.js.

### Herramientas y entorno

**Base comprobada en H3:** Java 25.0.4.1, Maven 3.10.0 mediante Wrapper, Spring Boot 4.1.2, Node 24.22.1 mediante fnm, npm 11.19.1 y PostgreSQL 18.6 en Compose. React, Vite y TypeScript tienen versiones fijadas y lockfile. Los detalles de comprobación están en [las tareas de H3](odd/tasks/h3-base.md) y [de H4](odd/tasks/h4-first-journey.md); el arranque se explica en la [guía local](docs/desarrollo-local.md).

- Maven es la única herramienta de construcción Java del proyecto; no se mantiene además Gradle. El CLI instalado no determina dependencias de la aplicación.
- Flyway y Testcontainers forman parte de la base validada; las pruebas usan datos desechables separados del volumen local y de las prácticas del curso.
- Node y npm están fijados; no se instala también nvm. Cambiar herramientas o añadir instalaciones requiere autorización propia.
- Springdoc/OpenAPI sigue opcional y no se incorporó en H4. Swagger no sustituye contrato ni pruebas. No añadir MapStruct sin necesidad concreta.

**Docker compartido, datos separados**

- Reutilizar motor e imagen postgres:18.6 si está disponible, sin compartir datos. Etiqueta fija no sustituye política de actualizaciones ni digest inmutable.
- Compose propio con proyecto `myopenacademy` (renombrado el 8 de noviembre de 2026; el `code-academy` es el del original y no se usa nunca); contenedor, red, credenciales y volumen exclusivos. No leer ni reutilizar configuración o datos de las prácticas.
- Volumen persistente con clave lógica y nombre físico prefijado por proyecto: no anónimo, externo ni `name` global fijo; evitar `container_name`. Pruebas e instancias adicionales con aislamiento propio.
- Host `127.0.0.1:5433` hacia `5432` del contenedor, comprobado en H3. No ocupar por defecto 5432 de prácticas ni exponer base a red local. Todos los servicios ligados a loopback.
- Healthcheck con `pg_isready`. Arranque validado: base en Compose, backend con Wrapper y perfil `local`, frontend Vite con proxy `/api`; no Tomcat independiente ni MySQL, MongoDB o Redis. Los servicios quedaron detenidos normalmente y el volumen se conserva.
- Reiniciar o bajar no borra datos. Eliminar volúmenes, por ejemplo `down -v`, es destructivo y no forma parte del flujo normal. Aislamiento no protege de limpiezas globales; definir respaldo y recuperación antes de depender de datos valiosos.

### Modelo de datos aprobado

Implementado en H4 con las migraciones V2–V5; además, cada lección pertenece a una fase con ID permanente. Catálogo compartido entre autenticados; inscripciones, estados y continuación privados.

| Entidad | Datos y relaciones |
|---|---|
| Usuario | ID permanente, username único sin distinguir mayúsculas, hash seguro y version_credenciales; nunca contraseña en claro. «Santiago» y «santiago» no coexisten. |
| Curso | ID permanente, título y lecciones. |
| Lección | ID permanente, curso_id, título, posición y disponibilidad; equivale a módulo del origen. |
| Ejercicio | ID permanente, curso_id y lección de pertenencia del mismo curso. |
| Inscripción | Pareja usuario–curso única; referencia opcional a última lección abierta de ese curso, inicialmente vacía. |
| Estado de lección | Inscripción, curso_id, lección, estado manual y fecha; único por inscripción y lección. |
| Estado de ejercicio | Inscripción, curso_id, ejercicio, estado manual y fecha; único por inscripción y ejercicio. |

- Base de datos guarda catálogo y referencias, alimentados por manifiesto; cuerpos, ejemplos, soluciones y recursos permanecen en archivos versionados, no texto educativo en base de datos.
- Temario completo con pendientes sin cuerpo. El manifiesto de H4 tiene los 199 módulos del índice en el commit fijado; la cifra no es constante y se contrastará en cada importación. No inventar ejercicios de lecciones pendientes.
- IDs de lección y ejercicio únicos en toda la academia, con prefijo permanente del curso (por ejemplo `java-02.01`), sin renombrar ni renumerar por título, ruta u orden. Manifiesto conserva identidad; futuras retiradas con progreso requieren H5.
- Crear inscripción al marcar estado, guardar estado y establecer continuación inicial en la lección correspondiente es una sola transacción. Si ya existe inscripción, marcar no cambia continuación. Añadir explícitamente deja continuación vacía hasta abrir una lección.
- Estados con curso_id y claves foráneas compuestas a `(inscripción_id, curso_id)` y `(lección_id, curso_id)` o `(ejercicio_id, curso_id)`; entidades referidas con restricciones únicas necesarias. Ejercicio/lección del mismo curso y continuación validada por `(última_lección_id, curso_id)`.
- Sin cascadas destructoras de progreso. Porcentaje calculado, no almacenado. No habrá quitar curso ni borrar inscripción en primera versión; archivar sin pérdida sería propuesta futura con aprobación propia.
- Backend obtiene identidad de sesión: navegador no elige propietario. Toda lectura/escritura personal se limita al autenticado, incluso acceso directo por ID; rechazar acceso cruzado sin devolver ni modificar datos. Claves foráneas no sustituyen autorización ni ocultar controles constituye seguridad.
- H4 lo comprobó con dos usuarios: independencia, persistencia tras reiniciar y rechazo sin cambios de lecturas/escrituras cruzadas (`npm run test:e2e:journey`).

### Pantallas y entrega segura

Implementadas en H4 con el diseño aprobado `04-mezcla.html`:

| Pantalla | Función |
|---|---|
| Acceso y registro | Cuenta o login; explicar recuperación local por consola. |
| Mis cursos | Destino tras login; avance y continuación o índice si no existe; ofrecer catálogo si vacío. |
| Catálogo | Explorar índice y añadir explícitamente. |
| Índice | Temario completo, disponibilidad y estados; pendientes no se abren. |
| Lección | Contenido, recursos, soluciones desplegables y estados manuales; anterior/siguiente saltan pendientes, sin destino en extremos. |

No hay pantallas separadas para ejercicios, soluciones o progreso. Tras confirmar adición automática, aviso como «Java se añadió a Mis cursos» con nombre correspondiente. Cambios solo como guardados tras confirmación; si fallan, informar sin simular persistencia. Backend rechaza abrir pendientes por URL; UI muestra «todavía no disponible». Logout retira datos privados y rechaza nuevas solicitudes con esa sesión.

La [política completa de contenido](docs/api.md#contenido-ejercicios-y-recursos) exige HTML saneado, extracción separada de ejercicios sin duplicación en `bodyHtml`, enlaces reescritos y fragmentos finales válidos. Mantiene `<details>`/`<summary>` seguros, Mermaid `strict` y fallback de fuente original escapada; el SVG generado no elude controles. Recursos autenticados por ID de manifiesto, contención canónica con symlinks y sin rutas del cliente/origen; SVG con tipo correcto, `nosniff` y CSP `sandbox` sin permisos; HTML educativo solo descarga o texto escapado, nunca ejecución del mismo origen. H4 implementó esta política con las bibliotecas indicadas en la tabla anterior.

El asesor informó 1731 anclas, 67 lecciones con Mermaid, 60 SVG y 2662 HTML: evidencia aportada no verificada aquí, no mediciones nuevas ni constantes.

### Contratos de H2 cerrados

- [API: contrato completo de las 14 rutas, JSON, validaciones, errores y seguridad](docs/api.md), con los añadidos de H4 (`ACCESS_DENIED`, 406, fases y nombre original de recursos). Usuario de 3–32 ASCII `[A-Za-z0-9_]` aprobado con el cierre. No GET de aprendizaje con efectos de escritura. Frontend condiciona continuación a progreso `added: true`; backend conserva 409 sin inscripción.
- [Manifiesto mínimo para H4: estructura, ejemplo y validación](docs/manifiesto.md). Temario entero y primera lección importada; `sourceAvailable` no equivale a `available`. `versionRef` es hash completo del commit fuente, con marcador del ejemplo inválido en importación real. H5 conserva reconciliación completa.

### Sesión y contraseña confirmadas

Requisitos de negocio implementados en H4 sobre los mecanismos técnicos validados en H3.

- Sesión de servidor en memoria, ID rotado al login, caducidad tras 2 horas sin solicitudes autenticadas; reiniciar cierra sesión, no borra progreso. Sin remember-me ni credenciales en localStorage. Logout invalida sesión, limpia cookie y estado privado.
- Cookie HttpOnly, SameSite=Lax, sin Domain; Secure en HTTPS, excepción solo HTTP local. Sin cachés compartidas en autenticación/progreso ni secretos en registros.
- Comparar versión de credenciales en CADA solicitud autenticada contra base de datos, sin caché obsoleta. Rechazar sesiones viejas en siguiente solicitud, no cancelar retroactivamente solicitudes en vuelo.
- Recuperación local sin endpoint web: contraseña oculta dos veces, no argumentos. Hash nuevo e incremento de version_credenciales en una transacción, conservando ID y progreso; sin estado parcial que deje sesiones previas válidas.
- CSRF en TODAS las operaciones inseguras, incluidos registro, login y logout. Token antes de login y renovado tras login/logout. SameSite y proxy no sustituyen CSRF ni autorización. No reintentar escrituras inciertas.
- La integración SPA oficial `csrf.spa()` se verificó en H3 y H4 la usa en registro, login y logout. `/api/auth/csrf` solo condicional si integración nativa no basta, no decimoquinta ruta fija; sesión técnica nunca progreso.
- Desarrollo de mismo origen con proxy Vite hacia `/api` Spring Boot, incluidos recursos, sin CORS abierto. Publicación definirá esquema de origen equivalente.
- Registro y consola: 15–128 puntos de código Unicode, admitiendo espacios/frases, sin composición obligatoria ni recorte, normalización o truncamiento. Login no aplica mínimo de política, ni exige no vacío por longitud: solo rechaza por longitud más de 128 puntos de código antes del hash, siempre con 401 genérico `AUTHENTICATION_FAILED`, nunca 400 `INVALID_PASSWORD`. Errores de esquema/tipo pueden ser 400. Evita bloquear usuarios existentes si cambia el mínimo.
- El máximo 128 protege operativamente el uso de Argon2, no es límite inherente del algoritmo. Argon2id con Argon2PasswordEncoder, Bouncy Castle y sal por contraseña; suelo inicial OWASP de 19 MiB, 2 iteraciones y paralelismo 1. H3 comprobó el mecanismo y midió su coste local sin bajar ese suelo; H4 lo usa en las cuentas.
- No bcrypt: límite de 72 bytes, no caracteres; tampoco 64 Unicode garantizan caber. Sin truncamiento ni prehash improvisado. Validar límites explícitamente.
- Revisar antes de publicar en internet los intentos de acceso/recuperación y enumeración de registro (`USERNAME_TAKEN`), antes si cambia el entorno de uso fuera de local.

### Cierre de H2, H3 y H4

**H2 cerrado por el propietario:** recorrido, modelo, arquitectura, contrato JSON (incluido username) y manifiesto mínimo aprobados con los ajustes finales de login, extracción, continuación y procedencia.

H3 completó las comprobaciones técnicas de versiones, CSRF y Argon2id, junto con el arranque y la integración local ([tareas de H3](odd/tasks/h3-base.md)). H4 implementó cuentas, contenido, progreso y pantallas en cuatro partes, con verificaciones independientes; la evidencia está en [las tareas de H4](odd/tasks/h4-first-journey.md). El importador completo correspondió a H5; el importador por `curso.json`, a H7; la publicación en internet, a después del 1.0.

## 9. Pendiente para publicar en internet

Anotado desde Code Academy; no entra en el 1.0, que se usa en local y en la red de casa.

- El cebado del token CSRF depende de `GET /actuator/health` ([API](docs/api.md#sesión-csrf-y-entorno)).
- Tomcat registra el mensaje completo de una excepción que escapa a un filtro del framework.
- La cookie que borra logout no lleva `Secure` y fija el nombre `JSESSIONID`.
- Limitación de intentos y enumeración de usuarios (apartado 8).
- CSP para la aplicación.
- Publicar el código en GitHub (H11) no publica la aplicación en funcionamiento: desplegarla requiere autorización aparte.
