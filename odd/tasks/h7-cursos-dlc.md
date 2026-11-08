# H7 — Cursos como DLC

Objetivo: instalar cursos según `FORMATO.md` leyendo su `curso.json`, como DLC: el importador da de alta cursos nuevos, no inventa identificadores y el contenido sale de Git. Alcance aprobado por el autor el 9 de noviembre de 2026 (`PLAN.md`, apartados 3 y 4; cambios 1 a 5, 8 y 13 de la lista de `ESTADO.md`). Rama `feat/h7-cursos-dlc`, desde `main` `53d675f`. Commits locales; el autor revisa antes de integrar. Sin remotos.

Pruebas: `./mvnw -B verify` en `backend/`; `npm run test -- --run`, `npm run lint` y `npm run build` en `frontend/`; el recorrido aislado `npm run test:e2e:journey`. Las pruebas con contenido provisional, siempre en bases desechables.

## Alcance (del plan aprobado)

- A1. **Curso de ejemplo:** copia del curso «bueno» del kit desde un commit fijo, que sustituye al curso de Java real en las pruebas.
- A2. **Importador por `curso.json`:** da de alta cursos nuevos sin manifiesto previo; el manifiesto interno se genera desde `curso.json` (punto de partida: `tools/curso_a_manifiesto.py`), con las anclas calculadas; no lee las filas del índice ni inventa identificadores; los módulos con `"disponible": false` entran como pendientes.
- A3. **Identificadores tal cual**, con el prefijo `<curso>.` y la forma de `FORMATO.md` 3.3.
- A4. **`--source` con rutas relativas y `..`**, con las comprobaciones de enlaces simbólicos, `.env` y solapamiento.
- A5. **Versión:** instalar desde una etiqueta, resolverla al commit y guardar las dos, con la `version` de `curso.json`.
- A6. **Varios cursos y `content/` fuera de Git:** catálogo con descripción y recomendaciones; `content/java/` sale del repositorio, sin mantener la lectura del manifiesto antiguo (el autor estudia Java en el Code Academy original).
- A7. **Lista de cursos instalados** en el repositorio (curso, versión y commit) y un comando que la reinstala.
- Fuera de H7 (van en H8): retos, glosario, enlaces entre cursos, proyecto desde `curso.json`, «Mejorar esta lección».

## Tareas

T1 | A1 | Traer el curso de ejemplo: `tools/traer_curso_ejemplo.sh` y `cursos-ejemplo/` (copia del kit en `80e803a1a17eb0bcf60500e232b4d6c5152767bb`) | hecho: la copia es idéntica al commit y pasa `formato_academia.py` con 0 errores | commit: ver L2.
T2 | A3 | Identificadores con punto en el validador del manifiesto (`<curso>.` y forma de `FORMATO.md` 3.3) | pendiente.
T3 | A1, A6 | Pruebas sin el curso de Java real: los 12 ficheros de pruebas que usan `../content` pasan al curso de ejemplo (o a contenido de prueba propio); `content/java/` sale de Git y `content/` pasa a `.gitignore` | pendiente.
T4 | A2, A3, A5 | Importador por `curso.json`: modelo y lectura de `curso.json`, generación del manifiesto (lecciones, ejercicios, fases, pendientes, anclas, recursos y ZIP, reutilizando `LessonPipeline` y lo de `ContentImportGenerator` que sirva), instalación desde etiqueta o commit | pendiente.
T5 | A4 | `--source` relativo con `..` | pendiente.
T6 | A6 | Catálogo de varios cursos con descripción y recomendaciones (API y pantallas) | pendiente.
T7 | A7 | Lista de cursos instalados y comando de reinstalación | pendiente.
T8 | todos | Documentación (`docs/manifiesto.md`, `docs/actualizacion-contenido.md`, `docs/desarrollo-local.md`, `content/README.md`), prueba con la fase 1 del curso inicial en el navegador (base desechable) y cierre | pendiente.

## Registro

L1. 9 de noviembre de 2026: el autor aprueba empezar H7 («Sí, empieza H7»). Rama `feat/h7-cursos-dlc` creada desde `main` tras el commit del estado. Decisión del autor del mismo día: el segundo curso de ejemplo que se pide al kit tendrá su propio `id` y su propio `repositorio`, no los del curso inicial real.
L2. T1: el curso «bueno» del kit se trae con `git archive` del commit `80e803a1a17eb0bcf60500e232b4d6c5152767bb` (la carpeta de trabajo del kit tiene cambios sin commit, pero no en `bueno/`). 21 ficheros; `formato_academia.py`: 0 errores y 4 avisos (enlace al módulo pendiente 02, y los de enlaces a otros cursos sin carpeta hermana). Pruebas que dependen del contenido de Java real: 12 ficheros del backend y el recorrido E2E (`frontend/e2e/journey/two-users.spec.ts`), para T3.
