# Code Academy — Plan del proyecto

Academia web para estudiar programación, empezando por Java. El objetivo es usarla personalmente cuando esté funcionando y presentarla como proyecto de portfolio, con apoyo de agentes de IA para desarrollarla.

**Estado:** planificación. No se ha iniciado la implementación de la aplicación.
Este archivo es el plan principal versionado; Engram complementa su continuidad, no lo reemplaza.

## 1. Decisiones confirmadas

- Una sola plataforma, con una estructura de cursos reutilizable para otros lenguajes. No se duplicará la aplicación por curso.
- Java será el primer curso y el propietario será el primer alumno.
- El contenido Java se utilizará a partir de una copia controlada dentro de Code Academy; el proyecto original permanecerá intacto y en solo lectura.
- La academia deberá servir a otras personas con distintos niveles de conocimientos.
- Múltiples usuarios desde la primera versión, aunque funcione en local.
- Avance persistente e independiente por persona y curso.
- Frontend separado y backend en Java con Spring Boot, para aprender construyendo un producto real.
- Desarrollo y ejecución inicial en local; publicación en internet como hito posterior explícito.
- GitHub y el despliegue son decisiones independientes. Publicar código no publica la aplicación en funcionamiento.
- Planificar requisitos, experiencia y arquitectura antes de programar.

## 2. Fuente de contenido y límites

| Proyecto | Uso permitido |
|---|---|
| `Code Academy` | Código, planificación, documentación, contenido importado, complementos y memoria Engram de la academia |
| `/home/santiago/Proyectos 2/Curso Java Completo` | Solo lectura como fuente del curso Java; no modificar archivos ni su memoria Engram |

El curso Java ya tiene su temario completo definido en el índice, pero no todas las lecciones están escritas. Su crecimiento consiste en completar los módulos previstos.

**Terminología:** una lección de la academia equivale a un módulo del curso Java de origen. Son dos nombres para la misma unidad de contenido, no dos niveles distintos.

La academia distinguirá el mapa completo del curso del contenido realmente disponible para estudiar. No presentará un módulo pendiente como una lección terminada.

### Copia controlada confirmada; mecanismo de importación y actualización pendiente

Trabajar sobre una copia en Code Academy ya está acordado. Las siguientes propuestas describen cómo hacerlo y todavía deben diseñarse y validarse:

- Copiar únicamente material necesario: lecciones, ejemplos, ejercicios, imágenes y recursos del alumno.
- Excluir el historial Git, temporales y herramientas internas que no formen parte del material educativo.
- Registrar la procedencia y versión del contenido importado.
- Incorporar nuevos módulos y actualizaciones mediante una importación manual con comparación previa.
- Separar el contenido importado de los complementos de la academia.
- Diseñar el manifiesto que relacione los identificadores permanentes de la academia con las rutas y referencias del origen.
- Resolver conflictos explícitamente, sin sobrescribir adaptaciones automáticamente.

**Principio confirmado:** los identificadores de lecciones y ejercicios pertenecen a la academia y son permanentes, independientes de las rutas, números y posiciones del curso original. Se asignan al importar y se conservan aunque el origen se reorganice. La propuesta mínima del manifiesto para H4 figura en la sección 4; el formato completo y la reconciliación se diseñarán en H5. El número del origen no será la fuente de verdad de la identidad.

Antes de implementar la importación correspondiente, definir cómo se detecta disponibilidad, cómo se trasladan enlaces y recursos y qué sucede con lecciones modificadas, retiradas o divididas. Ninguna actualización borrará ni reasignará progreso silenciosamente.

## 3. Primera versión local

### Requisitos confirmados

- Curso Java como primer contenido.
- Usuarios diferenciados con avance guardado por persona y curso.
- Ejecución local, sin requerir un despliegue público.
- Organización que permita incorporar otros cursos más adelante.

### Recorrido confirmado por el propietario

1. **Cuenta y acceso:** registro abierto en local con usuario y contraseña. Catálogo y contenido requieren sesión iniciada; registro e inicio de sesión permanecen accesibles sin autenticar.
2. **Recuperación:** una herramienta de consola permitirá restablecer la contraseña conservando el progreso. Este estará asociado a un identificador interno estable del usuario, no a su nombre o contraseña. No se creará una cuenta administradora automáticamente al arrancar. El mecanismo se revisará antes de publicar en internet.
3. **Mis cursos:** el alumno puede añadir Java explícitamente. También se añade automáticamente si marca el estado de una lección o ejercicio de un curso todavía no añadido.
4. **Exploración:** tras iniciar sesión, puede consultar libremente lecciones disponibles de cursos no añadidos. Esas visitas no guardan progreso de estudio ni cambian el punto de continuación. Esto no excluye registros técnicos de diagnóstico, que no se usarán como seguimiento del estudio.
5. **Índice:** se muestra el temario completo, con navegación libre. Los módulos pendientes son visibles como pendientes, pero no se pueden abrir.
6. **Lecciones y soluciones:** incluyen ejemplos y enlaces a recursos de la copia importada, sin depender de rutas del proyecto original. Las soluciones se abren libremente en desplegables, sin confirmación obligatoria. Consultarlas no completa ejercicios ni registra un estado de «solución consultada».
7. **Práctica:** los ejercicios se resuelven en el IDE local y el alumno marca su estado. No se ejecutará código del alumno en el servidor o navegador en esta versión. Abrir una lección no equivale a completarla, ni consultar una solución equivale a resolver un ejercicio.
8. **Continuación:** solo los cursos añadidos recuerdan la última lección abierta y permiten «continuar donde lo dejaste». Si marcar un estado añade automáticamente el curso, la continuación se establece en esa lección, también si se marcó un ejercicio suyo. Cada persona mantiene su avance independiente por curso.

### Reglas de progreso confirmadas por el propietario

| Elemento | Estados | Quién los cambia |
|---|---|---|
| Lección | no completada / completada | El alumno, manualmente |
| Ejercicio | pendiente / en curso / resuelto | El alumno, manualmente |

- No existe un estado «leída». «No completada» permite haber estudiado parte sin terminarla.
- El alumno puede desmarcar o corregir cualquier estado, sin una secuencia obligatoria.
- Completar una lección no exige resolver sus ejercicios. El progreso representa declaraciones del alumno, no una evaluación automática.
- Abrir una lección no cambia su estado; solo actualiza la última lección abierta si el curso está añadido. Explorar un curso no añadido no guarda progreso ni continuación. Consultar soluciones tampoco modifica estados.
- El avance del curso se calcula como lecciones completadas entre lecciones disponibles. Se muestran las cifras absolutas, el porcentaje y el total previsto del temario. Ejemplo ilustrativo, no recuento actual del curso: «12 de 129 lecciones disponibles completadas · 9,3 % · 199 previstas».
- Incorporar nuevas lecciones disponibles puede reducir el porcentaje sin borrar el trabajo realizado; las cifras permiten distinguir ambos efectos.
- Ninguna actualización borra ni reasigna progreso silenciosamente. El tratamiento concreto de lecciones corregidas, retiradas o divididas se diseñará en H5, respetando este principio.

Con esta confirmación quedan cerradas las reglas de progreso de H1 y se puede pasar al diseño de H2. La implementación segura del acceso, recuperación y autorización se diseñará antes de programar.

## 4. Arquitectura y tecnologías

**Confirmado por el propietario:** el stack y entorno descritos a continuación, junto con la política de contraseña de 15–128 caracteres con Argon2id y Bouncy Castle. La compatibilidad y disponibilidad de las versiones elegidas se comprobarán antes de generar el proyecto; cualquier sustitución se justificará y revisará, no será automática. No se autoriza instalar ni implementar por confirmar el diseño.

| Responsabilidad | Decisión o estado |
|---|---|
| Frontend | Confirmado: React + Vite + TypeScript. Next.js no se incorpora. |
| Componentes y estilos | Tailwind CSS + shadcn/ui siguen como propuesta independiente, no necesaria para cerrar el recorrido funcional. |
| Backend | Confirmado: Java 25 + Spring Boot 4.1.2, Maven y Maven Wrapper. Compatibilidad por verificar. Una sola aplicación modular. |
| Comunicación | API REST |
| Base de datos | Confirmado: PostgreSQL con imagen postgres:18.6 en Docker Compose, entorno y datos propios de Code Academy. |
| Persistencia | Spring Data JPA y migraciones versionadas mediante Flyway con soporte PostgreSQL. |
| Seguridad | Confirmado: Spring Security, sesión de servidor y CSRF; Argon2id con Argon2PasswordEncoder y Bouncy Castle. Diseño de sesión y suelo inicial del hash confirmados abajo; implementación y verificación pendientes. |
| Contenido | Markdown y recursos versionados |
| Pruebas | Confirmado: Vitest y React Testing Library para frontend; JUnit y Testcontainers con PostgreSQL real para backend; Playwright para recorridos completos. |

El backend será responsable de las reglas de negocio y del acceso a los datos. La interfaz presentará el contenido y consumirá la API. El cuerpo de las lecciones residirá en archivos Markdown versionados de la copia controlada; falta definir su procesamiento y entrega segura, no su ubicación.

No se incorpora Prisma ni se da por elegido Auth.js: la separación del backend obliga a revisar esas propuestas anteriores.

Se confirma un monorepo con frontend/, backend/, content/ (copia importada y manifiesto) y docs/. El backend se organiza por responsabilidades de cuentas/acceso, catálogo/contenido y estudio/progreso, dentro de una sola aplicación; no se proponen microservicios.

### Herramientas y entorno confirmados

**Evidencia aportada por el asesor, no verificada nuevamente en esta sesión:** Java 25.0.4, Maven 3.10.0, Gradle 9.8.2 y Spring Boot CLI 4.1.2 disponibles mediante SDKMAN; imágenes postgres:18.6, MySQL, MongoDB, Redis, Tomcat y Testcontainers/Ryuk descargadas; ningún contenedor creado en el momento de su revisión; Node.js no instalado. PostgreSQL está disponible como imagen Docker, no como instalación nativa. El asesor informa que «Mi Biblioteca» usa Java 25, Spring Boot 4.1.2, Maven, Flyway, postgres:18.6 y springdoc. Son datos de esa revisión, no una garantía del estado actual ni de compatibilidad.

**Decisiones de herramientas y límites de la confirmación**

- Alinear el backend con el curso reduce herramientas nuevas y permite reutilizar conocimientos. Usar Maven, no mantener también una compilación Gradle; incorporar Maven Wrapper para reproducibilidad. El CLI de Spring instalado no determina la versión de las dependencias de la aplicación.
- Java 25, Spring Boot 4.1.2 y postgres:18.6 son las versiones base confirmadas a partir de la propuesta del asesor. Antes de generar el proyecto se verificará su disponibilidad, soporte y compatibilidad conjunta, incluidos Flyway, Testcontainers y cualquier biblioteca añadida. No instalar ni descargar versiones basándose solo en ese inventario.
- React + Vite cubre el cliente de la API sin necesitar un servidor Next.js adicional. Usar Vitest y React Testing Library para lógica/componentes y Playwright para recorridos reales del navegador.
- Usar JUnit y Testcontainers con PostgreSQL de la misma versión propuesta para la aplicación, con datos desechables propios de cada ejecución de integración. Las pruebas nunca usarán el volumen de estudio ni el de las prácticas del curso.
- Node.js mediante fnm: se seleccionará la LTS vigente al momento de instalar en H3 y se comprobará su compatibilidad con Vite. Se fijará la versión elegida junto con el gestor npm y su lockfile. No se instalará nvm además de fnm. La instalación requiere autorización explícita independiente; no se instala nada durante H2.
- Springdoc/OpenAPI es útil para documentar y explorar la API, pero opcional. No hace falta añadir Swagger ahora: primero cerrar el contrato; en H3/H4 se evaluará una versión compatible de springdoc. Swagger no sustituye pruebas ni decisiones sobre el contrato. No añadir MapStruct solo porque lo use el curso; se evaluará si aparece una necesidad concreta.

**Docker compartido, entorno y datos separados**

- Reutilizar el motor Docker y la imagen postgres:18.6 ya descargada si está disponible. Compartir una imagen no comparte los datos de las bases. La etiqueta fija evita seguir automáticamente la rama 18, aunque no sustituye una futura política de actualizaciones ni un digest inmutable.
- Compose propio dentro de Code Academy, con nombre de proyecto code-academy. Contenedor, red, credenciales y volumen exclusivos de la academia; no leer ni reutilizar datos o configuración de las prácticas.
- Usar un volumen persistente con clave lógica en Compose, cuyo nombre físico quede prefijado por el proyecto: no volumen anónimo, externo ni con name global fijo. Evitar container_name. El nombre code-academy identifica una instancia local; pruebas e instancias adicionales tendrán aislamiento propio.
- PostgreSQL accesible desde el host en 127.0.0.1:5433 hacia el puerto 5432 del contenedor, previa comprobación de disponibilidad en H3. La academia no ocupará por defecto el 5432 de las prácticas ni expondrá la base a la red local.
- Incluir healthcheck con pg_isready. Arranque local propuesto: base de datos en Compose, backend con Maven Wrapper y frontend con Vite y proxy /api. No se necesita un Tomcat independiente ni los motores MySQL, MongoDB o Redis para este alcance.
- Reiniciar o bajar el servicio no debe borrar sus datos. Eliminar el volumen de la academia, por ejemplo mediante down -v sobre su proyecto Compose, sigue siendo destructivo y no formará parte del flujo normal. El aislamiento evita mezclar prácticas y academia, pero no protege de limpiezas globales de Docker; el respaldo y recuperación de datos valiosos deberán definirse antes de depender de ellos.

Antes de programar se definirán el modelo de datos, el contrato de API, la autorización entre usuarios, las migraciones y el entorno de ejecución local.

### Modelo de datos aprobado como base de H2

El contenido es compartido entre usuarios autenticados; las inscripciones, los estados y la continuación son privados de cada usuario. Este es un modelo conceptual aprobado como base, no código ni un esquema físico definitivo.

| Entidad | Datos y relaciones principales |
|---|---|
| Usuario | ID permanente, nombre de usuario, hash seguro de contraseña y version_credenciales. Nunca contraseña en texto plano. Nombre único sin distinguir mayúsculas de minúsculas: «Santiago» y «santiago» no pueden coexistir. La versión permite invalidar sesiones tras recuperar la contraseña. |
| Curso | ID permanente y título. Tiene muchas lecciones. |
| Lección | ID permanente de la academia, curso_id, título, posición y disponibilidad. Equivale a un módulo del origen. Tiene ejercicios cuando su contenido está disponible. |
| Ejercicio | ID permanente, curso_id y lección de pertenencia, que debe ser del mismo curso. |
| Inscripción | Relaciona un usuario con un curso y representa «mis cursos». Pareja usuario–curso única. Guarda una referencia opcional a la última lección abierta de ese curso, inicialmente vacía. |
| Estado de lección | Relaciona inscripción, curso_id y lección. Estado manual no completada/completada y fecha de actualización. Un único registro por inscripción y lección. |
| Estado de ejercicio | Relaciona inscripción, curso_id y ejercicio. Estado manual pendiente/en curso/resuelto y fecha de actualización. Un único registro por inscripción y ejercicio. |

**Contenido y catálogo**

- La base de datos guarda el catálogo y las referencias al contenido: IDs, títulos, posición, disponibilidad y relación con la copia importada. El manifiesto alimenta ese catálogo al importar. El cuerpo educativo, ejemplos, soluciones y recursos permanecen en archivos versionados de la copia, no como texto de lecciones en la base de datos.
- El catálogo representa el temario completo, incluidas las lecciones pendientes sin cuerpo disponible. La cifra de 199 módulos fue aportada por el asesor; no se ha vuelto a contar en esta revisión y no será una constante del sistema. La importación debe contrastar el catálogo con el índice completo de la versión de origen elegida.
- Solo se incorporan ejercicios de lecciones con contenido disponible; no se inventan ejercicios para las pendientes. Una futura retirada de contenido con progreso existente exige el tratamiento de H5, no borrar sus ejercicios o estados automáticamente.
- Los IDs de lección y ejercicio son únicos en toda la academia. Se usará un prefijo permanente del curso; por ejemplo, java-02.01. No se deriva del título visible ni cambia al renombrar el curso. La parte restante del ID tampoco se renumera al reorganizar el origen; los ejercicios reciben identidades permanentes, no posiciones recalculadas.
- El manifiesto conserva esas identidades aunque cambien números, rutas o posiciones del origen. El formato detallado y la reconciliación siguen pendientes de H5; la primera importación necesitará una versión mínima de ese diseño.

**Integridad y escritura**

- Sin registro personal, la lección se considera no completada y el ejercicio pendiente. No hace falta precargar todos los estados al añadir un curso.
- Si marcar un estado crea la inscripción, la inscripción, el estado y la continuación en esa lección se guardan en una sola transacción. Para un ejercicio se utiliza su lección de pertenencia. Si el curso ya estaba añadido, marcar un estado no cambia por sí solo la continuación; abrir una lección sí la actualiza. Añadir explícitamente un curso mantiene la continuación vacía hasta abrir una lección.
- Abrir una lección disponible solo actualiza la continuación si el curso ya está añadido. No modifica estados ni crea una inscripción al explorar.
- Los estados incluyen curso_id y claves foráneas compuestas hacia (inscripción_id, curso_id) y (lección_id, curso_id) o (ejercicio_id, curso_id). Las entidades referidas tendrán las restricciones únicas necesarias para esas claves.
- La relación del ejercicio con su lección también exige el mismo curso. La referencia opcional de continuación se valida mediante (última_lección_id, curso_id). Así la consistencia entre cursos no depende solamente del código.
- No se configuran borrados en cascada que destruyan progreso al quitar una inscripción o retirar contenido.
- El porcentaje se calcula sobre las lecciones disponibles, no se almacena. Si no hay ninguna disponible, se muestra «sin lecciones disponibles» y no se divide por cero. Las fechas de actualización corresponden a cambios guardados del estado, no a visitas.

**Aislamiento por usuario**

- Spring Boot obtiene la identidad de la sesión autenticada. El navegador no elige el propietario del progreso.
- Toda lectura y escritura de inscripciones, estados y continuación se limita al usuario autenticado, también cuando se accede directamente por identificador.
- Cambiar un identificador para acceder a una inscripción ajena se rechaza sin devolver su progreso ni modificarlo. Ocultar controles en la interfaz no constituye autorización.
- Las claves foráneas garantizan consistencia de relaciones, no permisos. La autorización se aplica en el backend y se prueba por separado.
- Las comprobaciones de H4 usarán dos usuarios: progreso independiente, persistencia tras reiniciar e intentos de lectura y escritura cruzadas rechazados sin cambios en los datos.

**Quitar cursos: fuera de la primera versión**

No se ofrecerá quitar cursos de «mis cursos» en la primera versión. No habrá operación de borrado de inscripción. Si se incorpora después, se propone archivar sin borrar inscripción, estados ni continuación; ese comportamiento requerirá aprobación propia.

### Recorrido funcional de pantallas aprobado

No es todavía un diseño visual ni una implementación.

| Pantalla | Función |
|---|---|
| Acceso y registro | Crear cuenta o iniciar sesión. Explicar la recuperación mediante la herramienta local de consola. |
| Mis cursos | Destino tras iniciar sesión. Cursos añadidos, cifras y porcentaje de avance; continuar desde la última lección abierta o abrir el índice si no existe. Si está vacío, ofrecer el catálogo. |
| Catálogo | Consultar cursos, abrir su índice para explorar y añadir un curso explícitamente. |
| Índice del curso | Temario completo, disponibilidad y estados personales. Las lecciones pendientes son visibles pero no se abren. |
| Lección | Contenido, recursos, soluciones desplegables y controles manuales de estado de lección y ejercicios. Navegación anterior/siguiente según el orden del curso, saltando pendientes; sin destino en los extremos. |

- No se necesitan pantallas separadas para ejercicios, soluciones o progreso.
- Explorar no guarda progreso ni continuación; marcar un estado deja de ser una simple visita y añade el curso si falta, con continuación en esa lección.
- Tras confirmar el backend la adición automática, mostrar un aviso como «Java se añadió a Mis cursos», utilizando el nombre del curso correspondiente.
- La interfaz solo presenta cambios como guardados tras confirmación del backend. Si la operación falla, informa el error sin simular persistencia.
- El backend rechaza abrir por URL una lección pendiente y la interfaz muestra «todavía no disponible», no un error genérico. Consultar su entrada del índice no equivale a abrir su contenido.
- Cerrar sesión deja de mostrar datos privados y el backend rechaza nuevas solicitudes autenticadas con esa sesión.

### Entrega de contenido: condición para el diseño de API

**Diseño confirmado; no implementado ni verificado.** Convertir Markdown a HTML con una lista permitida de elementos, atributos y protocolos antes de representarlo. Conservar `<details>`/`<summary>` seguros; excluir scripts, manejadores de eventos y enlaces ejecutables. Elegir y comprobar la biblioteca en H3/H4.

- Generar IDs de encabezados compatibles con GitHub, incluidos duplicados, acentos y puntuación. Permitir IDs solo para esos destinos generados, no atributos `id` arbitrarios del contenido. Conservar enlaces a fragmentos locales y validar que sus destinos finales existan después de limpiar y reescribir.
- Marcar bloques Mermaid con clases de lenguaje controladas. Mermaid en el frontend usará modo `strict`, sin HTML arbitrario ni interacciones. Si falla un diagrama, mostrar su código fuente original escapado, nunca interpretado como HTML; el resto de la lección seguirá funcionando. La limpieza del Markdown no autoriza que el SVG generado por Mermaid eluda controles de seguridad.
- Servir recursos autenticados exclusivamente por `resourceId` registrado en el manifiesto. Resolver rutas locales con comprobación canónica de contención, incluidos enlaces simbólicos; rechazar IDs desconocidos y escapes. No aceptar rutas del cliente ni exponer las del origen.
- Los SVG educativos tendrán tipo correcto, `X-Content-Type-Options: nosniff` y CSP `sandbox` sin permisos adicionales. El HTML educativo solo se ofrecerá como descarga o texto escapado, nunca como ejecución del mismo origen.
- El asesor informó 1731 anclas, 67 lecciones con Mermaid, 60 SVG y 2662 HTML. Son evidencias aportadas no verificadas aquí, no mediciones nuevas ni constantes del sistema.

### Contrato de API aprobado como base de H2

Todas las rutas de catálogo, contenido, recursos y progreso requieren sesión. Registro e inicio de sesión son accesibles sin autenticar. Las rutas /me toman el propietario de la sesión y no aceptan un usuario_id para elegir de quién son los datos. Ningún GET modifica estados, inscripción o continuación.

| Método y ruta | Función |
|---|---|
| POST /api/auth/register | Crear cuenta con usuario y contraseña. Tras registrarse, pasar al inicio de sesión. |
| POST /api/auth/login | Iniciar sesión y devolver datos básicos del usuario. |
| POST /api/auth/logout | Invalidar la sesión. |
| GET /api/me | Identidad autenticada, sin contraseña ni hash. |
| GET /api/courses | Catálogo de cursos. |
| GET /api/courses/{courseId} | Información e índice completo, con IDs, títulos, orden y disponibilidad. |
| GET /api/lessons/{lessonId} | HTML limpio, ejercicios, referencias de recursos y anterior/siguiente disponibles. No registra la apertura. |
| GET /api/resources/{resourceId} | Recurso de la copia controlada, sin rutas arbitrarias del sistema. |
| GET /api/me/courses | Mis cursos con cifras de avance y continuación. |
| PUT /api/me/courses/{courseId} | Añadir explícitamente; repetir no duplica ni reinicia datos. |
| GET /api/me/courses/{courseId}/progress | Inscripción, estados personales, cifras de avance y continuación. |
| PUT /api/me/lessons/{lessonId}/state | Guardar state: not_completed o completed. |
| PUT /api/me/exercises/{exerciseId}/state | Guardar state: pending, in_progress o solved. |
| PUT /api/me/courses/{courseId}/continuation | Recibir lessonId y registrar apertura efectiva solo si el curso ya está añadido. No crea inscripción. |

**Respuestas y efectos**

- Para un curso existente no añadido, la consulta de progreso devuelve 200 con added: false, lessonStates: [], exerciseStates: [] y lastOpenedLessonId: null. Incluye las cifras compartidas de disponibles y previstas; completedLessons es 0 y percentage es 0 si hay disponibles, o null si no las hay. No crea registros personales. La interfaz interpreta los estados ausentes según sus valores por defecto.
- Para un curso añadido, devuelve added: true, los estados guardados y la continuación, junto a las cifras calculadas. Un curso inexistente devuelve 404, no una respuesta vacía.
- Guardar un estado devuelve el estado persistido, updatedAt, courseAutoAdded, continuación y avance resultantes. courseAutoAdded solo es true si esa operación creó la inscripción. Inscripción, estado y continuación inicial se guardan o rechazan juntos.
- La interfaz registra la apertura tras cargar correctamente la lección, no al precargar enlaces. Si se solicita guardar continuación sin inscripción, se rechaza con 409 y COURSE_NOT_ADDED, sin crear datos.
- Se rechazan la apertura del contenido, el cambio de estado y la continuación de lecciones pendientes con 409 y LESSON_UNAVAILABLE. Consultar su entrada del índice sí está permitido. Tampoco se cambian estados de ejercicios cuyo contenido no esté disponible.
- No existe ruta para quitar cursos. Repetir una adición explícita o asignar el mismo estado no duplica registros ni reinicia progreso.

| HTTP | Uso y códigos de error estables |
|---|---|
| 400 | Datos o estados inválidos; referencia a contenido de otro curso. |
| 401 | Sesión ausente o caducada; credenciales de acceso incorrectas sin revelar si existe el usuario. |
| 403 | Protección CSRF rechazada. |
| 404 | Recurso inexistente o dato personal ajeno, sin exponer su existencia. |
| 409 | USERNAME_TAKEN, LESSON_UNAVAILABLE o COURSE_NOT_ADDED, según la operación. |

Los errores incluirán code y message; la interfaz usa code para elegir el mensaje, no interpreta el texto del servidor. La siguiente propuesta completa los cuerpos y validaciones para revisión; no sustituye la aprobación pendiente.

**Enlaces de la copia importada**

La importación reescribirá enlaces relativos del origen hacia rutas de lecciones identificadas por sus IDs permanentes y hacia /api/resources/{resourceId}. Conservará los fragmentos internos válidos y validará cada destino resultante, incluido el encabezado final tras la limpieza; permitir el atributo id no basta. No expondrá rutas del proyecto original. Los enlaces a lecciones pendientes llevarán a la vista que informa «todavía no disponible», nunca a contenido inexistente. Una referencia no resuelta se señalará al validar la importación, sin asignarla silenciosamente a otra lección. El mecanismo concreto se diseña en H5, con una versión mínima para H4.

### Sesión, recuperación y entorno local confirmados

**Aprobados en diseño; no implementados ni verificados.**

| Tema | Regla confirmada |
|---|---|
| Sesión | En memoria del servidor; reiniciarlo cierra sesiones, no borra progreso persistido. Rotar el ID al iniciar sesión. Sin remember-me ni credenciales de sesión en localStorage. |
| Cookie | HttpOnly, SameSite=Lax, sin Domain; Secure con HTTPS. Única excepción: HTTP exclusivamente local. |
| Caducidad y salida | Caducar tras 2 horas sin solicitudes autenticadas. Logout invalida en servidor y limpia la cookie; el cliente borra todo estado privado de la interfaz. |
| Versión de credenciales | La sesión conserva la versión al autenticar. En CADA solicitud autenticada compararla con version_credenciales actual de la base de datos, sin caché obsoleta. Rechazar sesiones viejas en su siguiente solicitud; no cancela retroactivamente solicitudes ya en vuelo. |
| Recuperación | Consola local autorizada, sin endpoint web. Solicitar contraseña nueva oculta y dos veces, no por argumentos de proceso. Guardar hash nuevo e incremento de version_credenciales en una sola transacción; conservar identidad y progreso. Una caída no debe dejar contraseña nueva con sesiones anteriores válidas. |
| Errores y caché | Fallos de autenticación genéricos; no registrar secretos. Respuestas de autenticación y progreso sin cachés compartidas. |
| Red local | Todos los servicios de desarrollo, incluidos frontend, backend y base, ligados exclusivamente a loopback. |

CSRF se valida en el backend para TODAS las operaciones inseguras, incluidos registro, login y logout. Preferir la integración SPA oficial de Spring si está documentada para la versión elegida: `csrf.spa()` sigue **SIN VERIFICAR**. Comprobar documentación en H3 antes de usarla. El token debe estar disponible antes del login y renovarse después de login y logout. SameSite no sustituye CSRF. No reintentar automáticamente escrituras cuyo resultado sea incierto.

`/api/auth/csrf` es solo una ruta condicional si la integración nativa soportada no basta; no es una decimoquinta ruta fijada. Obtener el token podrá crear una sesión técnica, nunca progreso de aprendizaje.

En desarrollo, el navegador accede al frontend y a /api desde el mismo origen mediante el proxy de Vite hacia Spring Boot, aunque ambos procesos tengan puertos distintos. El cliente usa rutas relativas /api, incluidas las de recursos. El proxy no sustituye CSRF ni la autorización y no requiere habilitar CORS abierto. Esta configuración acompaña la adopción confirmada de Vite. En publicación se definirá el mismo esquema de origen mediante el servidor o proxy correspondiente.

### Política mínima de contraseña confirmada

- Entre 15 y 128 caracteres Unicode, contados como puntos de código, admitiendo espacios y frases de contraseña.
- Sin exigir mezclas de mayúsculas, números o símbolos; sin recortar espacios ni truncar silenciosamente. La misma interpretación se aplica al registro, acceso y recuperación por consola.
- Usar Argon2id con Argon2PasswordEncoder de Spring Security y su dependencia Bouncy Castle, con sal por contraseña. Suelo inicial confirmado de referencia OWASP: **19 MiB**, 2 iteraciones y paralelismo 1. Es un mínimo, no un óptimo universal. Verificar versiones y medir coste local en H3 para fijar parámetros adecuados sin bajar ese suelo; no asumir que los valores predeterminados cumplen la política. No registrar contraseñas ni devolverlas en respuestas.
- No usar bcrypt para esta política de 128 puntos de código: su límite de entrada es de 72 bytes, no caracteres. Bajar a 64 caracteres Unicode tampoco garantiza caber en 72 bytes. No se aplicará truncamiento ni prehash improvisado como solución. Argon2id permite mantener el límite propuesto sin esa restricción de bcrypt; los límites de entrada deben validarse explícitamente.
- Anotar en H7 la limitación de intentos de acceso y recuperación y la enumeración de usuarios mediante el registro (USERNAME_TAKEN), con revisión antes de cualquier exposición fuera del entorno local. Si cambia antes el entorno de uso, esa revisión se adelanta.

### Propuesta de contrato JSON — pendiente de aprobación

**PROPUESTO, no aprobado ni implementado.** Mantiene las 14 rutas base. Los nombres de formas siguientes son abreviaturas documentales, no envoltorios adicionales en la respuesta. Fechas `updatedAt` en UTC ISO 8601 (por ejemplo, `2026-01-01T12:00:00Z`); null si no existe estado guardado. IDs globales y permanentes, opacos para el cliente, con namespace permanente de curso para contenido. No aceptar `userId`/`usuario_id` como propietario ni devolver contraseñas, hashes o versiones de credenciales.

**Formas reutilizables de salida**

| Forma | Campos JSON y significado |
|---|---|
| User | `id`, `username` (nombre visible conservado). |
| Counts | `completedLessons`, `availableLessons`, `plannedLessons`, `percentage`: cifras calculadas dinámicamente desde el catálogo y los estados, nunca constantes. Porcentaje 0–100 sin redondeo contractual; null si no hay disponibles. |
| Course | `id`, `title`. |
| LessonEntry | `id`, `courseId`, `title`, `position`, `available` (disponibilidad importada, no promesa de que todo lo escrito en origen ya se importó). |
| Exercise | `id`, `lessonId`, `courseId`, `title`, `statementHtml`, `solutionHtml` (HTML limpio; solución libremente desplegable). |
| Resource | `resourceId`, `url`, `mediaType`; URL controlada `/api/resources/{resourceId}`, sin ruta física. |
| Continuation | `lastOpenedLessonId`: ID o null. |
| Enrollment | `courseId`, `added`, `continuation`, `progress` (Counts). |
| LessonState / ExerciseState | `lessonId` o `exerciseId`, `state`, `updatedAt`. Lección: `not_completed`/`completed`; ejercicio: `pending`/`in_progress`/`solved`. |
| Progress | `courseId`, `added`, `lessonStates` (LessonState[]), `exerciseStates` (ExerciseState[]), `continuation`, `progress` (Counts). |
| StateResult | `courseId`, `lessonId` o `exerciseId`, `state`, `updatedAt`, `courseAutoAdded`, `continuation`, `progress` (Counts). |

**Entrada y éxito de cada ruta** — sin cuerpo de entrada salvo el indicado; arrays vacíos válidos. Todos los GET son solo lectura de aprendizaje, sin inscripción, estados ni continuación; una sesión técnica CSRF no contradice esta regla.

| Ruta base | Entrada JSON | HTTP y salida |
|---|---|---|
| POST /api/auth/register | `{ "username": "Alumno", "password": "una frase extensa" }` | 201, User; no inicia sesión, UI pasa a login. |
| POST /api/auth/login | Mismos campos de registro | 200, User y cookie de sesión rotada. |
| POST /api/auth/logout | Sin cuerpo | 204, sin cuerpo; invalida sesión y limpia cookie. |
| GET /api/me | — | 200, User. |
| GET /api/courses | — | 200, `{ "courses": Course[] }`. |
| GET /api/courses/{courseId} | — | 200, `{ "course": Course, "lessons": LessonEntry[] }`, temario completo ordenado. |
| GET /api/lessons/{lessonId} | — | 200, `{ "lesson": LessonEntry, "bodyHtml": string, "exercises": Exercise[], "resources": Resource[], "previousLessonId": ID o null, "nextLessonId": ID o null }`; vecinos disponibles del mismo curso. |
| GET /api/resources/{resourceId} | — | 200, bytes con tipo seguro del recurso, no JSON; HTML solo descarga/texto escapado y SVG con sandbox según política confirmada. |
| GET /api/me/courses | — | 200, `{ "courses": [{ "course": Course, "enrollment": Enrollment }] }`. |
| PUT /api/me/courses/{courseId} | Sin cuerpo | 200, Enrollment con `added: true`; repetición no reinicia continuación ni estados. |
| GET /api/me/courses/{courseId}/progress | — | 200, Progress incluso sin inscripción; 404 si el curso no existe. |
| PUT /api/me/lessons/{lessonId}/state | `{ "state": "completed" }` | 200, StateResult de lección. |
| PUT /api/me/exercises/{exerciseId}/state | `{ "state": "in_progress" }` | 200, StateResult de ejercicio. |
| PUT /api/me/courses/{courseId}/continuation | `{ "lessonId": "java-lesson-intro" }` | 200, Enrollment con continuación confirmada; sin inscripción: 409 COURSE_NOT_ADDED. |

Ejemplo ilustrativo de progreso de un curso existente no añadido, con dos entradas y una disponible; no es un recuento del curso real. La forma JSON usa `added: false` como en la regla aprobada:

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

**Validaciones y efectos propuestos**

- JSON estricto: rechazar tipos incorrectos, campos desconocidos, obligatorios ausentes, null no permitido, enums desconocidos y JSON malformado. IDs deben resolver al catálogo; no aceptar rutas como IDs. Cuerpos ausentes donde se exige objeto son inválidos; escrituras sin cuerpo no aceptan campos extra.
- Usuario: propuesta pendiente de límite de 3–32 caracteres ASCII `[A-Za-z0-9_]`, sin recortar espacios. Conservar presentación y comparar unicidad/acceso sin distinguir mayúsculas mediante representación canónica en minúsculas; no normalizar la contraseña. Esta restricción adicional requiere aprobación, no cambia por sí sola la política confirmada.
- Contraseña: aplicar los 15–128 puntos de código Unicode confirmados en registro, login y consola, sin recortar, truncar ni exigir composición. Login con credenciales inválidas devuelve error genérico, también para usuario inexistente; no filtrar por mensajes de validación la existencia de cuentas.
- Toda referencia debe pertenecer al curso esperado. Continuación solo a lección disponible del mismo curso. Rechazar también estados de ejercicios de lecciones no disponibles. Recurso desconocido o fuera de contención no se entrega.
- Inscripción automática, estado y continuación inicial son atómicos. `courseAutoAdded` solo es true para la operación que crea la inscripción. Un reintento no duplica datos ni altera la continuación de una inscripción existente. Propuesta: repetir exactamente el estado mantiene `updatedAt` del último cambio real. No reintentar automáticamente escrituras inciertas.
- Todas las operaciones rechazadas dejan intactos los datos personales y los contadores de progreso. La UI solo muestra cada cambio de estado, inscripción o continuación como guardado tras confirmación del backend; una respuesta fallida no simula éxito. No hay DELETE ni endpoint de recuperación web.

| HTTP | `code` propuesto y condición |
|---|---|
| 400 | `INVALID_REQUEST` (JSON/campos/tipos), `INVALID_USERNAME`, `INVALID_PASSWORD`, `INVALID_STATE`, `COURSE_MISMATCH`. Login inválido usa 401 genérico. |
| 401 | `AUTHENTICATION_FAILED` (login), `AUTHENTICATION_REQUIRED` (sesión ausente, caducada o versión de credenciales inválida). |
| 403 | `CSRF_REJECTED`, incluidas operaciones públicas inseguras. |
| 404 | `NOT_FOUND`: curso/lección/ejercicio/recurso inexistente, recurso no autorizado o dato personal ajeno; sin revelar existencia ni rutas. |
| 409 | `USERNAME_TAKEN`, `LESSON_UNAVAILABLE`, `COURSE_NOT_ADDED`. |
| 500 | `INTERNAL_ERROR`: fallo inesperado sin detalles internos ni secretos; escrituras transaccionales sin efectos parciales. |

Formato único propuesto de error, sin eco de credenciales; `message` es informativo y la UI decide por `code`:

```json
{
  "code": "LESSON_UNAVAILABLE",
  "message": "Lesson is not available."
}
```

### Propuesta de manifiesto mínimo para H4 — pendiente de aprobación

**PROPUESTO, no aprobado ni creado.** Contrato documental de primera carga, no implementación de esquema de base de datos ni importador completo de H5. El conjunto mínimo tendrá metadatos del temario entero, incluidas entradas no disponibles, y una primera lección disponible con su cuerpo controlado. Distinguir `sourceAvailable` (material escrito en la versión de origen) de `available` (importado y utilizable aquí). Otras entradas, incluso escritas en origen, pueden seguir sin importar en H4; no se afirmará que ya se importó todo el material escrito.

El ejemplo es un extracto diminuto e ilustrativo, no el manifiesto completo ni una medición. Números de capítulos y rutas son ficticios. `versionRef` es un marcador que deberá sustituirse por la referencia real verificada, no un hash inventado. `schemaVersion` versiona el formato; `namespace` y todos los IDs permanecen aunque cambien las rutas y el orden.

```json
{
  "schemaVersion": 1,
  "source": {
    "name": "Curso Java Completo",
    "versionRef": "PENDIENTE_REFERENCIA_VERIFICADA"
  },
  "course": { "id": "java", "namespace": "java", "title": "Java" },
  "lessons": [
    {
      "id": "java-lesson-intro",
      "title": "Introducción",
      "position": 1,
      "sourcePath": "01/introduccion.md",
      "sourceAvailable": true,
      "available": true,
      "bodyPath": "java/lessons/intro.md",
      "exercises": [
        { "id": "java-exercise-first", "lessonId": "java-lesson-intro", "title": "Primer ejercicio" }
      ]
    },
    {
      "id": "java-lesson-next",
      "title": "Siguiente módulo",
      "position": 2,
      "sourcePath": "02/siguiente.md",
      "sourceAvailable": true,
      "available": false,
      "bodyPath": null,
      "exercises": []
    }
  ],
  "resources": [
    {
      "resourceId": "java-resource-example",
      "lessonId": "java-lesson-intro",
      "sourcePath": "01/ejemplo.txt",
      "localPath": "java/resources/ejemplo.txt",
      "mediaType": "text/plain"
    }
  ],
  "sourceToId": [
    {
      "sourcePath": "01/introduccion.md",
      "lessonId": "java-lesson-intro",
      "fragments": { "introduccion": "introduccion" }
    },
    { "sourcePath": "02/siguiente.md", "lessonId": "java-lesson-next", "fragments": {} },
    { "sourcePath": "01/ejemplo.txt", "resourceId": "java-resource-example" }
  ]
}
```

- En el manifiesto real, incluir cada entrada del índice de la versión elegida con título, posición, identidad y disponibilidad; no deducir el total de este extracto. `available: true` exige `sourceAvailable: true`, cuerpo existente y ejercicios/recursos válidos; las pendientes no tienen cuerpo ni ejercicios importados. El cuerpo contiene los enunciados/soluciones asociados explícitamente a los IDs de ejercicio de su lección, sin usar el ordinal como identidad.
- Rutas relativas a la raíz de copia controlada para `bodyPath`/`localPath`; `sourcePath` solo aporta procedencia y resolución relativa desde el documento de origen. Ninguna ruta habilita lecturas del origen al servir contenido. IDs de curso, lección, ejercicio y recurso globalmente únicos, permanentes y coherentes con el namespace y la pertenencia.
- `sourceToId` cubre documentos, recursos y fragmentos referenciados. Resolver cada enlace relativo respecto a su documento de origen, convertirlo a destino por ID y conservar/mapear su fragmento al ID final generado compatible con GitHub. Validar enlaces locales, cruzados y recursos tras reescritura y saneamiento. Un enlace sin fragmento a entrada pendiente lleva a «todavía no disponible»; no fingir un fragmento validado si falta el cuerpo de destino.
- Validación fail-fast de versión/esquema, referencia de procedencia real (rechazar el marcador), temario incompleto, IDs duplicados o cruzados entre cursos, archivos requeridos ausentes, traversal/escape canónico con symlinks y enlaces o fragmentos rotos. Abortarlo todo sin importación parcial ni alteración de progreso. No sustituir referencias no resueltas por destinos adivinados.
- H4 comprobará la primera lección, enlaces y fragmentos resultantes, recursos seguros y fallback Mermaid escapado sin romper la lección, además del aislamiento y persistencia entre dos usuarios. H5 conserva la importación completa, comparación, actualización y reconciliación; esta propuesta no resuelve retiradas ni divisiones.

### Pendiente para cerrar H2

El stack, la organización local, la seguridad y la política de contraseña están confirmados en diseño, no implementados ni verificados. Falta **revisar y aprobar** las propuestas anteriores de contrato JSON (incluido el límite de usuario) y manifiesto mínimo de H4. H2 sigue abierto hasta esa aprobación. Comprobar compatibilidad, documentación CSRF y coste Argon2id corresponde a H3 antes de implementar sus mecanismos.

No hace falta cerrar en H2 el importador completo de H5, el despliegue de H7 ni producir maquetas visuales para validar el recorrido funcional ya aprobado.

## 5. Hitos propuestos

Los hitos son una secuencia de trabajo, no entregas ya realizadas. Se detallarán en tareas cuando cerremos su alcance.

| Hito | Entregable | Criterio de cierre |
|---|---|---|
| H1. Definición | Alcance inicial, reglas de progreso y exclusiones | Requisitos revisados y decisiones necesarias para el primer recorrido resueltas |
| H2. Diseño | Recorrido de pantallas, modelo de datos, API y arquitectura | Poder explicar el recorrido completo y cómo se separan los datos de cada usuario |
| H3. Base técnica | Frontend, backend, entorno local y pruebas iniciales | Arranque reproducible documentado y comprobaciones básicas ejecutables |
| H4. Primer recorrido | Una lección real importada, cuentas y avance persistente | Dos usuarios conservan avances distintos al reiniciar y no pueden modificar el avance ajeno |
| H5. Contenido disponible | Importación del material escrito y actualización incremental | Enlaces y recursos funcionan; incorporar un módulo no borra avances ni complementos |
| H6. Validación de estudio | Uso real de la academia por el propietario | Recorrido de estudio probado y problemas detectados registrados y priorizados |
| H7. Publicación | Plan de alojamiento, seguridad, respaldos y despliegue | Revisión de preparación y autorización explícita antes de publicar |

## 6. Ampliaciones posibles, no comprometidas para la primera versión

Se evaluarán por su beneficio para aprender, no por imitar todas las funciones de otras academias.

- Evaluación inicial opcional y recomendaciones de ruta.
- Evaluaciones cortas, ejercicios adicionales y pistas progresivas.
- Proyectos complementarios y recursos visuales o interactivos.
- Búsqueda y herramientas de consulta.
- Cursos de otros lenguajes, reutilizando la plataforma y adaptando la pedagogía.

La propuesta es dejar pagos, certificados, comunidad, tutor con IA, ejecución remota de código y aplicaciones móviles nativas fuera de la primera versión.

## 7. Git, GitHub y portfolio

- Git ya está inicializado en Code Academy, con rama `main` y sin commits.
- No hay un remoto configurado ni se ha publicado contenido en GitHub.
- Próximo paso de preparación propuesto: `.gitignore`, revisión del plan y primer commit autorizado.
- Mantener cambios pequeños, con pruebas y documentación junto al comportamiento correspondiente.
- No versionar secretos ni datos personales o temporales.
- Antes de publicar, decidir visibilidad, licencias y qué material del curso puede distribuirse.
- El portfolio distinguirá la autoría del contenido educativo de las contribuciones al desarrollo de la plataforma.
- El desarrollo contará con apoyo de agentes de IA. No requiere dominar previamente React, TypeScript o Spring ni convertir cada implementación en una clase.
- En ocasiones participará otro modelo como asesor para aportar propuestas y revisar decisiones durante el desarrollo. Sus recomendaciones se evaluarán; su rol de asesor no le otorga permisos de escritura ni autoridad para cambiar el alcance por sí solo.
- Se prioriza entregar una aplicación útil, mantenible y verificable, con decisiones documentadas que el propietario pueda comprender y explicar en su portfolio.
- El aprendizaje técnico puede acompañar al desarrollo de forma gradual, sin convertirse en un requisito previo ni bloquear las entregas.

## 8. Próximo paso

Revisar las propuestas de contrato JSON y manifiesto mínimo de la sección 4, incluido el límite de nombre de usuario. No pasar a implementación: H2 sigue abierto hasta su aprobación. Modelo, recorrido funcional y 14 rutas están aprobados como base; las formas JSON y el manifiesto son propuestas, no decisiones confirmadas.

**Última confirmación:** el propietario confirmó la seguridad con los ajustes de transacción atómica de recuperación, validación de enlaces resultantes, fallback Mermaid escapado, verificación documental de CSRF y suelo Argon2id a medir en H3. Es aprobación de diseño, no evidencia de salvaguardas implementadas. Stack, contraseña 15–128 puntos de código con Argon2id/Bouncy Castle y Node LTS mediante fnm en H3 siguen confirmados, con instalación sujeta a autorización. El inventario y los recuentos del asesor no se han verificado aquí. H1 está cerrado y H2 sigue abierto. No hay autorización para código, commits, instalaciones ni publicación. El primer commit continúa pendiente de autorización independiente.

Este documento se actualizará cuando se acuerden cambios. Una propuesta no pasa a ser un requisito confirmado por aparecer aquí.
