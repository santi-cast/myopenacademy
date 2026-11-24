# Registro de cambios

Qué cambia en cada versión de la academia, de la más nueva a la más antigua. Cada versión publicada se etiqueta en Git (`v1.0.0`). Los números siguen `MAYOR.MENOR.PARCHE`:

- **PARCHE:** arreglos, sin cambiar lo que ve quien instala ni la API.
- **MENOR:** funciones nuevas que no rompen nada: una instalación anterior se actualiza sin pasos a mano.
- **MAYOR:** cambios que obligan a hacer algo al actualizar (variables de entorno, la forma de instalar cursos o la versión del formato que acepta).

## Sin publicar

Para la 1.1.0 (en preparación; falta lo que mande el kit para su `FORMATO.md` 1.1):

- **Partes de los cursos.** Un curso puede dividirse en partes (`partes` en `curso.json`). El índice y el menú de las lecciones se agrupan por parte, con el avance de cada una; en el menú solo está abierta la parte en la que estás. «Mis cursos» dice por qué parte vas. Los cursos sin partes se ven como antes.
- **Avance por fase** en el índice y en el menú, en todos los cursos.
- **Listas de tareas** (`- [ ]`, `- [x]`) dibujadas como casillas desactivadas. Las teclas (`<kbd>`) se ven como teclas.
- **Los ZIP de código conservan el permiso de ejecución** de los ficheros que en Git son ejecutables (`mvnw`, `gradlew`), así que `./mvnw` funciona al descomprimir en Linux y macOS. El permiso sale de Git, nunca del disco. Los ZIP de un curso sin ejecutables salen idénticos byte a byte a los de la 1.0.0.
- **Manifiesto:** dos campos opcionales en el formato 2, `parts` y `executable`; los cursos instalados con la 1.0.0 siguen cargando sin reinstalar. Un curso instalado antes con ficheros ejecutables gana el permiso al actualizarlo o reinstalarlo.
- El temario de la API (`GET /api/courses/{courseId}`) devuelve `parts`.

## 1.0.0 — 16 de noviembre de 2026

El **pack base**: la academia más el curso inicial, «Fundamentos para programar» (su versión 1.0.0).

- **Cursos como DLC.** La academia arranca sin cursos y se instalan desde su repositorio, por su etiqueta de versión (o un commit), con el importador sin conexión. Cada curso trae su identidad en `curso.json`; ninguno está escrito en el código. Se comparan los cambios antes de aplicarlos, y el progreso de los alumnos se conserva.
- **Contenido según el formato de los cursos:** módulos con ejercicios, pistas y soluciones, preguntas de repaso, retos de cada fase, glosario, ruta de aprendizaje, chuleta global y proyecto (por etapas o como módulo). Diagramas Mermaid (12.3.0), código resaltado, imágenes y carpetas de código descargables como ZIP. Los enlaces entre cursos instalados son internos, y cada lección lleva «Mejorar esta lección» a su repositorio.
- **Progreso del alumno:** lecciones, ejercicios y etapas que marca el propio alumno, «Mis cursos» y continuar donde lo dejó.
- **Cuentas y administración:** registro abierto o cerrado, administrador creado desde la consola, desactivar cuentas, contraseñas temporales que hay que cambiar al entrar y nombre de la academia.
- **Instalación** con Docker Compose, en un solo comando y con toda la configuración en variables de entorno ([`docs/instalacion.md`](docs/instalacion.md)).
- Nace de Code Academy, su primera versión (sin publicar), hecha a medida del curso de Java; el historial se conserva, sin el contenido de ese curso.
