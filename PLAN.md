# Code Academy — Plan del proyecto

Academia web para estudiar programación, empezando por Java. El objetivo es usarla personalmente cuando esté funcionando y presentarla como proyecto de portfolio, con apoyo de agentes de IA para desarrollarla.

**Estado:** H1, H2 y H3 cerrados. La base técnica está validada y versionada en `feat/h3-base`. H4 no está iniciado: todavía no hay cuentas, cursos para estudiar ni progreso de alumnos.
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

Diseño aprobado para H4, todavía sin implementar; no describe funciones disponibles en la base técnica de H3.

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

H1 queda cerrado con estas reglas. H2 define la seguridad del futuro recorrido de estudio; H3 valida solo la base técnica de seguridad, no las cuentas ni la separación de progreso entre alumnos.

## 4. Arquitectura y tecnologías

**Diseño confirmado por el propietario; base técnica validada en H3.** Las versiones elegidas y su compatibilidad se comprobaron. Las funciones de negocio siguientes siguen siendo diseño para H4; cualquier sustitución futura requiere justificación y revisión.

| Responsabilidad | Decisión o estado |
|---|---|
| Frontend | React + Vite + TypeScript; no Next.js. |
| Componentes y estilos | Tailwind CSS + shadcn/ui siguen como propuesta opcional independiente del cierre funcional. |
| Backend | Java 25 + Spring Boot 4.1.2, Maven 3.10.0 mediante Wrapper; base compatible y validada. Una aplicación modular, no microservicios. |
| Comunicación | API REST; contrato documental aprobado. |
| Base de datos | PostgreSQL con imagen postgres:18.6 en Docker Compose, entorno y datos propios. |
| Persistencia | Spring Data JPA y migraciones versionadas Flyway con soporte PostgreSQL. |
| Seguridad | Base Spring Security con denegación por defecto, CSRF SPA y Argon2id con Bouncy Castle validada en H3; cuentas y reglas de sesión de negocio pendientes de H4. |
| Contenido | Futura copia controlada de Markdown y recursos; todavía sin importación. |
| Pruebas | Vitest y React Testing Library; JUnit y Testcontainers con PostgreSQL real; Playwright para recorridos completos. |

Monorepo con `frontend/`, `backend/`, `content/` (reservado para copia y manifiesto) y `docs/`. El diseño del backend separa cuentas/acceso, catálogo/contenido y estudio/progreso; estas funciones de negocio no están implementadas. La interfaz técnica consulta salud del backend. No se incorpora Prisma ni se da por elegido Auth.js.

### Herramientas y entorno

**Base comprobada en H3:** Java 25.0.4.1, Maven 3.10.0 mediante Wrapper, Spring Boot 4.1.2, Node 24.22.1 mediante fnm, npm 11.19.1 y PostgreSQL 18.6 en Compose. React, Vite y TypeScript tienen versiones fijadas y lockfile. Los detalles de comprobación están solo en [las tareas de H3](odd/tasks/h3-base.md); el arranque se explica en la [guía local](docs/desarrollo-local.md). No se ha inventariado el curso de origen.

- Maven es la única herramienta de construcción Java del proyecto; no se mantiene además Gradle. El CLI instalado no determina dependencias de la aplicación.
- Flyway y Testcontainers forman parte de la base validada; las pruebas usan datos desechables separados del volumen local y de las prácticas del curso.
- Node y npm están fijados; no se instala también nvm. Cambiar herramientas o añadir instalaciones requiere autorización propia.
- Springdoc/OpenAPI sigue opcional para evaluar en H4. Swagger no sustituye contrato ni pruebas. No añadir MapStruct sin necesidad concreta.

**Docker compartido, datos separados**

- Reutilizar motor e imagen postgres:18.6 si está disponible, sin compartir datos. Etiqueta fija no sustituye política de actualizaciones ni digest inmutable.
- Compose propio con proyecto `code-academy`; contenedor, red, credenciales y volumen exclusivos. No leer ni reutilizar configuración o datos de las prácticas.
- Volumen persistente con clave lógica y nombre físico prefijado por proyecto: no anónimo, externo ni `name` global fijo; evitar `container_name`. Pruebas e instancias adicionales con aislamiento propio.
- Host `127.0.0.1:5433` hacia `5432` del contenedor, comprobado en H3. No ocupar por defecto 5432 de prácticas ni exponer base a red local. Todos los servicios ligados a loopback.
- Healthcheck con `pg_isready`. Arranque validado: base en Compose, backend con Wrapper y perfil `local`, frontend Vite con proxy `/api`; no Tomcat independiente ni MySQL, MongoDB o Redis. Los servicios quedaron detenidos normalmente y el volumen se conserva.
- Reiniciar o bajar no borra datos. Eliminar volúmenes, por ejemplo `down -v`, es destructivo y no forma parte del flujo normal. Aislamiento no protege de limpiezas globales; definir respaldo y recuperación antes de depender de datos valiosos.

### Modelo de datos aprobado

Modelo conceptual para H4, no código ni esquema físico de negocio implementado. Catálogo compartido entre autenticados; inscripciones, estados y continuación privados.

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

La [política completa de contenido](docs/api.md#contenido-ejercicios-y-recursos) exige HTML saneado, extracción separada de ejercicios sin duplicación en `bodyHtml`, enlaces reescritos y fragmentos finales válidos. Mantiene `<details>`/`<summary>` seguros, Mermaid `strict` y fallback de fuente original escapada; el SVG generado no elude controles. Recursos autenticados por ID de manifiesto, contención canónica con symlinks y sin rutas del cliente/origen; SVG con tipo correcto, `nosniff` y CSP `sandbox` sin permisos; HTML educativo solo descarga o texto escapado, nunca ejecución del mismo origen. La elección y comprobación de bibliotecas de contenido queda para H4.

El asesor informó 1731 anclas, 67 lecciones con Mermaid, 60 SVG y 2662 HTML: evidencia aportada no verificada aquí, no mediciones nuevas ni constantes.

### Contratos de H2 cerrados

- [API: contrato completo de las 14 rutas, JSON, validaciones, errores y seguridad](docs/api.md). Usuario de 3–32 ASCII `[A-Za-z0-9_]` aprobado con el cierre. No GET de aprendizaje con efectos de escritura. Frontend condiciona continuación a progreso `added: true`; backend conserva 409 sin inscripción.
- [Manifiesto mínimo para H4: estructura, ejemplo y validación](docs/manifiesto.md). Temario entero y primera lección importada; `sourceAvailable` no equivale a `available`. `versionRef` es hash completo del commit fuente, con marcador del ejemplo inválido en importación real. H5 conserva reconciliación completa.

### Sesión y contraseña confirmadas

Requisitos de negocio aprobados para H4, no un recorrido de cuentas ya implementado. H3 solo aporta los mecanismos técnicos de base indicados a continuación.

- Sesión de servidor en memoria, ID rotado al login, caducidad tras 2 horas sin solicitudes autenticadas; reiniciar cierra sesión, no borra progreso. Sin remember-me ni credenciales en localStorage. Logout invalida sesión, limpia cookie y estado privado.
- Cookie HttpOnly, SameSite=Lax, sin Domain; Secure en HTTPS, excepción solo HTTP local. Sin cachés compartidas en autenticación/progreso ni secretos en registros.
- Comparar versión de credenciales en CADA solicitud autenticada contra base de datos, sin caché obsoleta. Rechazar sesiones viejas en siguiente solicitud, no cancelar retroactivamente solicitudes en vuelo.
- Recuperación local sin endpoint web: contraseña oculta dos veces, no argumentos. Hash nuevo e incremento de version_credenciales en una transacción, conservando ID y progreso; sin estado parcial que deje sesiones previas válidas.
- CSRF en TODAS las operaciones inseguras, incluidos registro, login y logout. Token antes de login y renovado tras login/logout. SameSite y proxy no sustituyen CSRF ni autorización. No reintentar escrituras inciertas.
- La integración SPA oficial `csrf.spa()` se verificó y se usa en la base de H3. La integración con registro, login y logout sigue pendiente de H4. `/api/auth/csrf` solo condicional si integración nativa no basta, no decimoquinta ruta fija; sesión técnica nunca progreso.
- Desarrollo de mismo origen con proxy Vite hacia `/api` Spring Boot, incluidos recursos, sin CORS abierto. Publicación definirá esquema de origen equivalente.
- Registro y consola: 15–128 puntos de código Unicode, admitiendo espacios/frases, sin composición obligatoria ni recorte, normalización o truncamiento. Login no aplica mínimo de política, ni exige no vacío por longitud: solo rechaza por longitud más de 128 puntos de código antes del hash, siempre con 401 genérico `AUTHENTICATION_FAILED`, nunca 400 `INVALID_PASSWORD`. Errores de esquema/tipo pueden ser 400. Evita bloquear usuarios existentes si cambia el mínimo.
- El máximo 128 protege operativamente el uso de Argon2, no es límite inherente del algoritmo. Argon2id con Argon2PasswordEncoder, Bouncy Castle y sal por contraseña; suelo inicial OWASP de 19 MiB, 2 iteraciones y paralelismo 1. H3 comprobó el mecanismo y midió su coste local sin bajar ese suelo; su uso en cuentas reales corresponde a H4.
- No bcrypt: límite de 72 bytes, no caracteres; tampoco 64 Unicode garantizan caber. Sin truncamiento ni prehash improvisado. Validar límites explícitamente.
- Revisar en H7 intentos de acceso/recuperación y enumeración de registro (`USERNAME_TAKEN`), antes si cambia el entorno de uso fuera de local.

### Cierre de H2 y verificaciones de H3

**H2 cerrado por el propietario:** recorrido, modelo, arquitectura, contrato JSON (incluido username) y manifiesto mínimo aprobados con los ajustes finales de login, extracción, continuación y procedencia. No quedan propuestas de estos contratos pendientes de aprobación; el recorrido de negocio aún no está implementado.

H3 completó las comprobaciones técnicas de versiones, CSRF y Argon2id, junto con el arranque y la integración local. La evidencia vive en [el archivo único de tareas](odd/tasks/h3-base.md). Esto no demuestra todavía cuentas, contenido ni progreso de H4. Estilos y springdoc siguen opcionales; el importador completo corresponde a H5 y el despliegue a H7.

## 5. Hitos

Secuencia de trabajo con estado explícito; los hitos futuros se detallarán en tareas al acordar su alcance. El cierre documental de H2 no implica que existan entregables ejecutables.

| Hito | Estado | Entregable | Criterio de cierre |
|---|---|---|---|
| H1. Definición | Cerrado | Alcance, progreso y exclusiones | Requisitos y decisiones del primer recorrido resueltos |
| H2. Diseño | Cerrado | Pantallas, modelo, API, arquitectura y manifiesto mínimo | Recorrido, separación de datos y contratos aprobados en diseño |
| H3. Base técnica | Cerrado | Frontend, backend, entorno local y pruebas iniciales | Arranque reproducible documentado y comprobaciones técnicas e independientes completadas |
| H4. Primer recorrido | No iniciado; requiere aprobación del propietario | Una lección real importada, cuentas y avance persistente | Dos usuarios conservan avances distintos al reiniciar, sin acceso cruzado |
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
- El segundo commit documental `a6f5206` ya cerró H2 y separó los contratos en `docs/api.md` y `docs/manifiesto.md`, con el mensaje `docs: close H2 design and split API and manifest contracts`.
- Una sola rama `feat/h3-base`, con commits locales pequeños y coherentes como unidades de revisión; sin cadenas de ramas ni PR. No hay autorización de merge a `main` ni de publicación. Pruebas/documentación acompañan al comportamiento; no versionar secretos, datos personales ni temporales.
- Antes de publicar, decidir visibilidad, licencias y material del curso distribuible. Portfolio distinguirá autoría educativa y contribuciones de plataforma.
- Desarrollo con agentes de IA, sin exigir dominar previamente React, TypeScript o Spring ni convertir cada implementación en una clase. Aprendizaje gradual sin bloquear entregas.
- Otro modelo podrá actuar como asesor; sus recomendaciones se evaluarán, sin otorgarle escritura ni autoridad para cambiar alcance por sí solo.
- Priorizar aplicación útil, mantenible y verificable, con decisiones que el propietario pueda explicar.

## 8. Próximo paso

**H3 completado y versionado en `feat/h3-base`.** El [archivo único de tareas y comprobaciones](odd/tasks/h3-base.md) conserva la evidencia de las cinco tareas. La revisión independiente de seguridad y entorno está completada. La evaluación nativa no estuvo disponible por alcance no evaluable con archivos sin seguimiento, incluso al solicitar rangos comprometidos; no se declara un veredicto ni aprobación nativa.

Los servicios quedaron detenidos normalmente, con el volumen propio conservado. El perfil local del backend carga la configuración humana sin exportaciones manuales. El agente no lee ni modifica `.env` o `.env.example`; no se cambian credenciales ni se borran volúmenes inicializados. Se conserva una sola rama `feat/h3-base` y commits coherentes, sin cadenas de ramas/PR, merge a `main`, remotos ni publicación.

**Próximo paso: conversar y autorizar H4 antes de implementarlo.** Concretar el primer recorrido real: cuentas, primera lección en copia controlada y progreso persistente, con la comprobación futura de dos usuarios sin acceso cruzado. H4 no está iniciado y este cierre no autoriza acceder al curso de origen ni importar contenido automáticamente.

Los contratos de H2 siguen siendo diseño para ese recorrido: política de contraseñas, versión de credenciales por solicitud, recuperación por consola, ejercicios separados, continuación condicionada y procedencia por hash completo. La base técnica validada no equivale a esas funciones ya disponibles.

Este documento se actualizará cuando se acuerden cambios. Una propuesta opcional no pasa a ser requisito confirmado por aparecer aquí.
