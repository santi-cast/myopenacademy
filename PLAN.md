# Code Academy — Plan del proyecto

Academia web para estudiar programación, empezando por Java. El objetivo es usarla personalmente cuando esté funcionando y presentarla como proyecto de portfolio, con apoyo de agentes de IA para desarrollarla.

**Estado:** H1 y H2 cerrados en definición y diseño; H3 (base técnica) es el siguiente hito, todavía no iniciado. No se ha iniciado la implementación de la aplicación. El cierre de diseño no autoriza código, instalaciones ni servicios.
Este archivo es el plan principal versionado; Engram complementa su continuidad, no lo reemplaza.

## 1. Decisiones confirmadas

- Una sola plataforma, con estructura de cursos reutilizable para otros lenguajes. No se duplicará la aplicación por curso.
- Java será el primer curso y el propietario será el primer alumno.
- El contenido Java se utilizará desde una copia controlada dentro de Code Academy; el proyecto original permanecerá intacto y en solo lectura.
- La academia deberá servir a otras personas con distintos niveles de conocimientos, con múltiples usuarios desde la primera versión local.
- Avance persistente e independiente por persona y curso.
- Frontend separado y backend en Java con Spring Boot, para aprender construyendo un producto real.
- Desarrollo y ejecución inicial en local; publicación en internet como hito posterior explícito.
- GitHub y despliegue son decisiones independientes. Publicar código no publica la aplicación en funcionamiento.
- Planificar requisitos, experiencia y arquitectura antes de programar.

## 2. Fuente de contenido y límites

| Proyecto | Uso permitido |
|---|---|
| `Code Academy` | Código, planificación, documentación, contenido importado, complementos y memoria Engram de la academia |
| `/home/santiago/Proyectos 2/Curso Java Completo` | Solo lectura como fuente del curso Java; no modificar archivos ni su memoria Engram |

El curso Java ya tiene su temario completo definido en el índice, pero no todas las lecciones están escritas. Su crecimiento consiste en completar los módulos previstos. No se ha accedido al origen para este cierre documental.

**Terminología:** una lección de la academia equivale a un módulo del curso Java de origen; son dos nombres para la misma unidad de contenido. El mapa completo del curso se distingue del contenido realmente disponible: no se presentará un módulo pendiente como una lección terminada.

### Copia controlada y procedencia

- Copiar únicamente material educativo necesario: lecciones, ejemplos, ejercicios, imágenes y recursos del alumno; excluir historial Git, temporales y herramientas internas.
- Registrar procedencia y versión. `versionRef` será el hash completo del commit de Curso Java Completo desde el que se importa, no una referencia inventada ni abreviada.
- Actualizaciones manuales con comparación previa y conflictos explícitos, sin sobrescribir adaptaciones automáticamente. Separar contenido importado y complementos de la academia.
- IDs de lecciones y ejercicios permanentes, globales e independientes de rutas, títulos, números y posiciones del origen. Se asignan al importar y se conservan al reorganizar; el ordinal no identifica un ejercicio.

El [manifiesto mínimo aprobado para H4](docs/manifiesto.md) detalla el temario entero, la primera lección importada, `sourceAvailable` frente a `available`, las asociaciones explícitas y las validaciones de rutas, enlaces y fragmentos. El formato completo y la reconciliación se diseñarán en H5. Antes de implementar cada importación se concretará su mecanismo, incluida disponibilidad y tratamiento de cambios, retiradas o divisiones. Ninguna actualización borrará ni reasignará progreso silenciosamente.

## 3. Primera versión local

### Requisitos y recorrido confirmados

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

- No existe «leída». «No completada» permite haber estudiado parte sin terminarla. Se pueden corregir estados sin secuencia obligatoria.
- Completar lección no exige resolver ejercicios. El progreso representa declaraciones del alumno, no evaluación automática.
- Abrir lección no cambia su estado; solo actualiza continuación si el curso está añadido, mediante escritura explícita, nunca GET. Explorar y consultar soluciones no modifica estados.
- Avance: lecciones completadas entre disponibles, con cifras absolutas, porcentaje y total previsto. Ejemplo ilustrativo, no recuento real: «12 de 129 lecciones disponibles completadas · 9,3 % · 199 previstas».
- Nuevas lecciones disponibles pueden reducir el porcentaje sin borrar trabajo. Si no hay disponibles, mostrar «sin lecciones disponibles» y no dividir por cero. Las fechas corresponden a cambios guardados de estado, no visitas.
- Sin registro personal, lección no completada y ejercicio pendiente; no precargar estados al añadir un curso. H5 diseñará tratamiento de cambios, retiradas o divisiones sin pérdida ni reasignación silenciosa de progreso.

H1 queda cerrado con estas reglas. La seguridad está acordada en diseño en H2, pero todavía no implementada ni verificada.

## 4. Arquitectura y tecnologías

**Diseño confirmado por el propietario.** La compatibilidad y disponibilidad de versiones se comprobarán en H3 antes de generar el proyecto; cualquier sustitución se justificará y revisará, no será automática. Confirmar diseño no autoriza instalar ni implementar.

| Responsabilidad | Decisión o estado |
|---|---|
| Frontend | React + Vite + TypeScript; no Next.js. |
| Componentes y estilos | Tailwind CSS + shadcn/ui siguen como propuesta opcional independiente del cierre funcional. |
| Backend | Java 25 + Spring Boot 4.1.2, Maven y Maven Wrapper. Compatibilidad por verificar. Una aplicación modular, no microservicios. |
| Comunicación | API REST; contrato documental aprobado. |
| Base de datos | PostgreSQL con imagen postgres:18.6 en Docker Compose, entorno y datos propios. |
| Persistencia | Spring Data JPA y migraciones versionadas Flyway con soporte PostgreSQL. |
| Seguridad | Spring Security, sesión de servidor, CSRF y Argon2id con Argon2PasswordEncoder y Bouncy Castle; implementación pendiente. |
| Contenido | Markdown y recursos versionados en copia controlada. |
| Pruebas | Vitest y React Testing Library; JUnit y Testcontainers con PostgreSQL real; Playwright para recorridos completos. |

Monorepo con `frontend/`, `backend/`, `content/` (copia y manifiesto) y `docs/`. Backend por responsabilidades de cuentas/acceso, catálogo/contenido y estudio/progreso; responsable de reglas y datos. La interfaz consume la API. No se incorpora Prisma ni se da por elegido Auth.js.

### Herramientas y entorno

**Evidencia aportada por el asesor, no verificada nuevamente aquí:** Java 25.0.4, Maven 3.10.0, Gradle 9.8.2 y Spring Boot CLI 4.1.2 mediante SDKMAN; imágenes postgres:18.6, MySQL, MongoDB, Redis, Tomcat y Testcontainers/Ryuk descargadas; ningún contenedor creado en su revisión; Node.js no instalado. PostgreSQL disponible como imagen, no nativo. «Mi Biblioteca» usaría Java 25, Spring Boot 4.1.2, Maven, Flyway, postgres:18.6 y springdoc. No es garantía de estado actual ni compatibilidad.

- Alinear backend con el curso reduce herramientas nuevas. Maven, sin mantener además Gradle; Wrapper reproducible. El CLI instalado no determina dependencias de la aplicación.
- En H3 verificar disponibilidad, soporte y compatibilidad conjunta de versiones, Flyway, Testcontainers y bibliotecas añadidas. No instalar ni descargar basándose solo en el inventario.
- Node.js mediante fnm, LTS vigente al instalar en H3 y compatible con Vite. Fijar versión, npm y lockfile; no instalar también nvm. Toda instalación requiere autorización explícita independiente.
- Springdoc/OpenAPI sigue opcional; evaluar versión compatible en H3/H4. Swagger no sustituye contrato ni pruebas. No añadir MapStruct sin necesidad concreta.
- JUnit/Testcontainers con PostgreSQL de la versión propuesta y datos desechables por ejecución; nunca volumen de estudio ni de prácticas del curso.

**Docker compartido, datos separados**

- Reutilizar motor e imagen postgres:18.6 si está disponible, sin compartir datos. Etiqueta fija no sustituye política de actualizaciones ni digest inmutable.
- Compose propio con proyecto `code-academy`; contenedor, red, credenciales y volumen exclusivos. No leer ni reutilizar configuración o datos de las prácticas.
- Volumen persistente con clave lógica y nombre físico prefijado por proyecto: no anónimo, externo ni `name` global fijo; evitar `container_name`. Pruebas e instancias adicionales con aislamiento propio.
- Host `127.0.0.1:5433` hacia `5432` del contenedor, previa comprobación en H3. No ocupar por defecto 5432 de prácticas ni exponer base a red local. Todos los servicios ligados a loopback.
- Healthcheck con `pg_isready`. Arranque propuesto: base en Compose, backend con Wrapper, frontend Vite con proxy `/api`; no Tomcat independiente ni MySQL, MongoDB o Redis.
- Reiniciar o bajar no borra datos. Eliminar volúmenes, por ejemplo `down -v`, es destructivo y no forma parte del flujo normal. Aislamiento no protege de limpiezas globales; definir respaldo y recuperación antes de depender de datos valiosos.

### Modelo de datos aprobado

Modelo conceptual, no código ni esquema físico definitivo. Catálogo compartido entre autenticados; inscripciones, estados y continuación privados.

| Entidad | Datos y relaciones |
|---|---|
| Usuario | ID permanente, username único sin distinguir mayúsculas, hash seguro y version_credenciales; nunca contraseña en claro. «Santiago» y «santiago» no coexisten. |
| Curso | ID permanente, título y lecciones. |
| Lección | ID permanente, curso_id, título, posición y disponibilidad; equivale a módulo del origen. |
| Ejercicio | ID permanente, curso_id y lección de pertenencia del mismo curso. |
| Inscripción | Pareja usuario–curso única; referencia opcional a última lección abierta de ese curso, inicialmente vacía. |
| Estado de lección | Inscripción, curso_id, lección, estado manual y fecha; único por inscripción y lección. |
| Estado de ejercicio | Inscripción, curso_id, ejercicio, estado manual y fecha; único por inscripción y ejercicio. |

- Base de datos guarda catálogo y referencias, alimentados por manifiesto; cuerpos, ejemplos, soluciones y recursos permanecen en archivos versionados, no texto educativo en base de datos. Procesamiento y entrega segura se implementarán después.
- Temario completo con pendientes sin cuerpo. La cifra de 199 módulos aportada por el asesor no se ha recontado ni será constante; contrastar índice completo en importación. No inventar ejercicios de lecciones pendientes.
- IDs de lección y ejercicio únicos en toda la academia, con prefijo permanente del curso (por ejemplo `java-02.01`), sin renombrar ni renumerar por título, ruta u orden. Manifiesto conserva identidad; futuras retiradas con progreso requieren H5.
- Crear inscripción al marcar estado, guardar estado y establecer continuación inicial en la lección correspondiente es una sola transacción. Si ya existe inscripción, marcar no cambia continuación. Añadir explícitamente deja continuación vacía hasta abrir una lección.
- Estados con curso_id y claves foráneas compuestas a `(inscripción_id, curso_id)` y `(lección_id, curso_id)` o `(ejercicio_id, curso_id)`; entidades referidas con restricciones únicas necesarias. Ejercicio/lección del mismo curso y continuación validada por `(última_lección_id, curso_id)`.
- Sin cascadas destructoras de progreso. Porcentaje calculado, no almacenado. No habrá quitar curso ni borrar inscripción en primera versión; archivar sin pérdida sería propuesta futura con aprobación propia.
- Backend obtiene identidad de sesión: navegador no elige propietario. Toda lectura/escritura personal se limita al autenticado, incluso acceso directo por ID; rechazar acceso cruzado sin devolver ni modificar datos. Claves foráneas no sustituyen autorización ni ocultar controles constituye seguridad.
- H4 comprobará con dos usuarios independencia, persistencia tras reiniciar y rechazo sin cambios de lecturas/escrituras cruzadas.

### Pantallas y entrega segura

Recorrido aprobado, no diseño visual ni implementación:

| Pantalla | Función |
|---|---|
| Acceso y registro | Cuenta o login; explicar recuperación local por consola. |
| Mis cursos | Destino tras login; avance y continuación o índice si no existe; ofrecer catálogo si vacío. |
| Catálogo | Explorar índice y añadir explícitamente. |
| Índice | Temario completo, disponibilidad y estados; pendientes no se abren. |
| Lección | Contenido, recursos, soluciones desplegables y estados manuales; anterior/siguiente saltan pendientes, sin destino en extremos. |

No hay pantallas separadas para ejercicios, soluciones o progreso. Tras confirmar adición automática, aviso como «Java se añadió a Mis cursos» con nombre correspondiente. Cambios solo como guardados tras confirmación; si fallan, informar sin simular persistencia. Backend rechaza abrir pendientes por URL; UI muestra «todavía no disponible». Logout retira datos privados y rechaza nuevas solicitudes con esa sesión.

La [política completa de contenido](docs/api.md#contenido-ejercicios-y-recursos) exige HTML saneado, extracción separada de ejercicios sin duplicación en `bodyHtml`, enlaces reescritos y fragmentos finales válidos. Mantiene `<details>`/`<summary>` seguros, Mermaid `strict` y fallback de fuente original escapada; el SVG generado no elude controles. Recursos autenticados por ID de manifiesto, contención canónica con symlinks y sin rutas del cliente/origen; SVG con tipo correcto, `nosniff` y CSP `sandbox` sin permisos; HTML educativo solo descarga o texto escapado, nunca ejecución del mismo origen. Elegir y comprobar bibliotecas en H3/H4.

El asesor informó 1731 anclas, 67 lecciones con Mermaid, 60 SVG y 2662 HTML: evidencia aportada no verificada aquí, no mediciones nuevas ni constantes.

### Contratos de H2 cerrados

- [API: contrato completo de las 14 rutas, JSON, validaciones, errores y seguridad](docs/api.md). Usuario de 3–32 ASCII `[A-Za-z0-9_]` aprobado con el cierre. No GET de aprendizaje con efectos de escritura. Frontend condiciona continuación a progreso `added: true`; backend conserva 409 sin inscripción.
- [Manifiesto mínimo para H4: estructura, ejemplo y validación](docs/manifiesto.md). Temario entero y primera lección importada; `sourceAvailable` no equivale a `available`. `versionRef` es hash completo del commit fuente, con marcador del ejemplo inválido en importación real. H5 conserva reconciliación completa.

### Sesión y contraseña confirmadas

- Sesión de servidor en memoria, ID rotado al login, caducidad tras 2 horas sin solicitudes autenticadas; reiniciar cierra sesión, no borra progreso. Sin remember-me ni credenciales en localStorage. Logout invalida sesión, limpia cookie y estado privado.
- Cookie HttpOnly, SameSite=Lax, sin Domain; Secure en HTTPS, excepción solo HTTP local. Sin cachés compartidas en autenticación/progreso ni secretos en registros.
- Comparar versión de credenciales en CADA solicitud autenticada contra base de datos, sin caché obsoleta. Rechazar sesiones viejas en siguiente solicitud, no cancelar retroactivamente solicitudes en vuelo.
- Recuperación local sin endpoint web: contraseña oculta dos veces, no argumentos. Hash nuevo e incremento de version_credenciales en una transacción, conservando ID y progreso; sin estado parcial que deje sesiones previas válidas.
- CSRF en TODAS las operaciones inseguras, incluidos registro, login y logout. Token antes de login y renovado tras login/logout. SameSite y proxy no sustituyen CSRF ni autorización. No reintentar escrituras inciertas.
- Preferir integración SPA oficial documentada: `csrf.spa()` sigue **SIN VERIFICAR**, comprobar en H3. `/api/auth/csrf` solo condicional si integración nativa no basta, no decimoquinta ruta fija; sesión técnica nunca progreso.
- Desarrollo de mismo origen con proxy Vite hacia `/api` Spring Boot, incluidos recursos, sin CORS abierto. Publicación definirá esquema de origen equivalente.
- Registro y consola: 15–128 puntos de código Unicode, admitiendo espacios/frases, sin composición obligatoria ni recorte, normalización o truncamiento. Login no aplica mínimo de política, ni exige no vacío por longitud: solo rechaza por longitud más de 128 puntos de código antes del hash, siempre con 401 genérico `AUTHENTICATION_FAILED`, nunca 400 `INVALID_PASSWORD`. Errores de esquema/tipo pueden ser 400. Evita bloquear usuarios existentes si cambia el mínimo.
- El máximo 128 protege operativamente el uso de Argon2, no es límite inherente del algoritmo. Argon2id con Argon2PasswordEncoder, Bouncy Castle y sal por contraseña; suelo inicial OWASP de 19 MiB, 2 iteraciones y paralelismo 1. Medir coste local en H3 sin bajar suelo; no asumir cumplimiento de predeterminados.
- No bcrypt: límite de 72 bytes, no caracteres; tampoco 64 Unicode garantizan caber. Sin truncamiento ni prehash improvisado. Validar límites explícitamente.
- Revisar en H7 intentos de acceso/recuperación y enumeración de registro (`USERNAME_TAKEN`), antes si cambia el entorno de uso fuera de local.

### Cierre de H2 y verificaciones de H3

**H2 cerrado por el propietario:** recorrido, modelo, arquitectura, contrato JSON (incluido username) y manifiesto mínimo aprobados con los ajustes finales de login, extracción, continuación y procedencia. No quedan propuestas de estos contratos pendientes de aprobación ni hay evidencia de implementación.

Compatibilidad/disponibilidad de versiones, documentación CSRF y benchmark Argon2id son verificaciones de H3 antes de implementar sus mecanismos, no bloqueos de aprobación de H2. Estilos y springdoc siguen opcionales. Importador completo de H5, despliegue H7 y maquetas visuales no son necesarios para cerrar H2.

## 5. Hitos

Secuencia de trabajo con estado explícito; los hitos futuros se detallarán en tareas al acordar su alcance. El cierre documental de H2 no implica que existan entregables ejecutables.

| Hito | Estado | Entregable | Criterio de cierre |
|---|---|---|---|
| H1. Definición | Cerrado | Alcance, progreso y exclusiones | Requisitos y decisiones del primer recorrido resueltos |
| H2. Diseño | Cerrado | Pantallas, modelo, API, arquitectura y manifiesto mínimo | Recorrido, separación de datos y contratos aprobados en diseño |
| H3. Base técnica | Próximo; no iniciado | Frontend, backend, entorno local y pruebas iniciales | Arranque reproducible documentado y comprobaciones básicas ejecutables |
| H4. Primer recorrido | No completado; pendiente | Una lección real importada, cuentas y avance persistente | Dos usuarios conservan avances distintos al reiniciar, sin acceso cruzado |
| H5. Contenido disponible | No completado; pendiente | Importación de material escrito y actualización incremental | Enlaces/recursos funcionan; nuevo módulo sin borrar avances ni complementos |
| H6. Validación de estudio | No completado; pendiente | Uso real por el propietario | Recorrido probado y problemas registrados/priorizados |
| H7. Publicación | No completado; pendiente | Alojamiento, seguridad, respaldos y despliegue | Preparación revisada y autorización explícita antes de publicar |

## 6. Ampliaciones posibles, no comprometidas para la primera versión

Se evaluarán por su beneficio para aprender, no por imitar otras academias.

- Evaluación inicial opcional y recomendaciones de ruta.
- Evaluaciones cortas, ejercicios adicionales y pistas progresivas.
- Proyectos complementarios y recursos visuales o interactivos.
- Búsqueda y herramientas de consulta.
- Cursos de otros lenguajes, reutilizando plataforma y adaptando pedagogía.

Pagos, certificados, comunidad, tutor con IA, ejecución remota de código y aplicaciones móviles nativas quedan fuera de la primera versión.

## 7. Git, GitHub y portfolio

- Git inicializado en rama `main`; el primer commit `29399a1` ya registra `PLAN.md` y `.gitignore` mínimo.
- Sin remotos configurados ni contenido publicado en GitHub. No se crean remotos en este cierre.
- El segundo commit documental permanece pendiente: mostrar antes el mensaje propuesto y esperar aprobación expresa del propietario. No se ha ejecutado ni se le asigna un hash ficticio.
- Cambios pequeños, con pruebas/documentación junto al comportamiento correspondiente. No versionar secretos, datos personales ni temporales.
- Antes de publicar, decidir visibilidad, licencias y material del curso distribuible. Portfolio distinguirá autoría educativa y contribuciones de plataforma.
- Desarrollo con agentes de IA, sin exigir dominar previamente React, TypeScript o Spring ni convertir cada implementación en una clase. Aprendizaje gradual sin bloquear entregas.
- Otro modelo podrá actuar como asesor; sus recomendaciones se evaluarán, sin otorgarle escritura ni autoridad para cambiar alcance por sí solo.
- Priorizar aplicación útil, mantenible y verificable, con decisiones que el propietario pueda explicar.

## 8. Próximo paso

**H3: preparación de la base técnica, todavía no iniciada.** Preparar su alcance y las verificaciones de disponibilidad/compatibilidad, documentación oficial de CSRF y coste Argon2id. Este cierre solo modifica documentación: no autoriza generar código, instalar dependencias, arrancar servicios, acceder al curso fuente, configurar remotos ni publicar.

Antes de cualquier commit, mostrar al propietario el mensaje propuesto del segundo commit documental y esperar autorización. La aprobación de H2 no sustituye ese punto de control ni permisos independientes para implementar o instalar en H3.

**Última confirmación:** H1/H2 cerrados; login sin mínimo de política y con máximo operativo de 128 puntos de código; registro/consola mantienen 15–128; ejercicios extraídos sin duplicación, continuación condicionada y procedencia por hash completo de commit fuente. Los contratos enlazados son diseño aprobado, no pruebas de salvaguardas implementadas. El inventario y recuentos del asesor no se verificaron aquí.

Este documento se actualizará cuando se acuerden cambios. Una propuesta opcional no pasa a ser requisito confirmado por aparecer aquí.
