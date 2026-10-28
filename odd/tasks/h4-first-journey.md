# H4 — First learner journey
Objective: accounts, one real lesson (00.01) imported from a pinned source commit, persistent per-user progress, screens and a two-user end-to-end proof.
Branch: feat/h4-first-journey from main b5847fa. Delivery: local coherent Conventional Commits per part; no branch/PR chains, merge to main, push or remotes without owner approval. Runners: Maven Wrapper (JUnit/Testcontainers), Vitest, Playwright.

## Specs
S1. Authorization: owner selected «Autorizo H4 completo» — «Rama feat/h4-first-journey, seguimiento y las 4 partes con commits por parte. Me detengo para mostrarte versiones antes de instalar dependencias. Sin merge ni remotos.»
S2. Parts, verbatim: «H4 es el hito más grande, así que conviene que el plan venga dividido en partes que se puedan cerrar y commitear por separado:
1. Cuentas: registro, login, logout, la herramienta de consola y la distinción de errores 403 y CSRF.
2. Catálogo e importación de una lección: el manifiesto mínimo y el procesamiento seguro del Markdown.
3. Progreso y continuación: inscripción, estados y avance.
4. Pantallas y la prueba de dos usuarios de principio a fin.»
S3. First lesson, verbatim: «Qué lección importar primero. Revisé las candidatas y recomiendo la 00.01 «Qué es programar»: Es la primera del curso, lo natural para un alumno nuevo. Tiene 5 diagramas Mermaid y 8 ejercicios con solución, así que pone a prueba la extracción de ejercicios y el plan alternativo si falla un Mermaid, que el manifiesto exige comprobar en H4. Es mediana (1099 líneas).» «Ninguna de las primeras lecciones tiene imágenes, así que los recursos y SVG se probarían más adelante, o con un recurso de prueba.» Resources/SVG are proven with test-only fixtures, stated as such at closure.
S4. Source access, verbatim: «Acceso al curso original: sí, pero importando desde el commit, no desde los archivos tal como están.» «Si el otro modelo lee los archivos del disco y apunta el hash de HEAD, el hash no corresponderá con lo que copió, y eso rompe la trazabilidad del manifiesto. La solución es leer el contenido desde el commit (git show <hash>:ruta o git archive), así lo copiado y el hash siempre coinciden. Tampoco debe ejecutar en ese repositorio ningún comando de Git que modifique algo: nada de stash, checkout ni fetch.» Only read-only git (rev-parse, show, archive to stdout/outside the source repo, ls-tree) in /home/santiago/Proyectos 2/Curso Java Completo; uncommitted work there (e.g. proyecto/09-interfaz-web.md) is out of the manifest.
S5. Dependencies, verbatim: «Dependencias: sí, con dos condiciones. Faltan las versiones. Que las verifique y fije, como hizo en H3, y te las muestre antes de instalarlas.» «La extensión de anclas de commonmark-java no garantiza generar los mismos IDs que GitHub. Pero tu curso ya trae la referencia: herramientas/comprobar_enlaces.py tiene una función ancla() que replica el algoritmo de GitHub. Conviene usarla, en modo lectura, para fabricar casos de prueba. Si los IDs no coinciden, los enlaces con #ancla quedarán rotos en H5.» «Mermaid es una librería grande. Que se cargue solo en la vista de lección, no en toda la aplicación.» Proposed: backend commonmark-java (+extensions) and OWASP Java HTML Sanitizer; frontend react-router and mermaid; no Tailwind/shadcn in H4. ancla() is extracted from the pinned commit to a scratch dir, run with `python -I`, and its outputs are fixed into tests (tests never depend on the source repo).
S6. Other 403s, verbatim: «ACCESS_DENIED: sí. Es claro y coherente con el resto de los códigos de error.» PLAN §8: «El `accessDeniedHandler` debe distinguir `CsrfException` de los demás errores 403: solo los fallos de CSRF deben responder `CSRF_REJECTED`.» Document ACCESS_DENIED in docs/api.md.
S7. Markdown processing, verbatim: «Procesar el Markdown en el backend al arrancar: sí. Así no se guarda el mismo contenido dos veces.» The validator runs the same pipeline.
S8. Console recovery, verbatim: «Para que la contraseña no se vea al escribirla hace falta System.console(), y con ./mvnw spring-boot:run suele no estar disponible, porque Maven arranca Java en un proceso aparte sin terminal propia. La herramienta debe ejecutarse con java -jar y negarse a funcionar si no hay consola, en vez de pedir la contraseña de forma visible.» Plus approved addition: require `System.console() != null && console.isTerminal()` (Java 22+).
S9. Sessions across restart, verbatim: «Las sesiones se pierden al reiniciar el backend, porque viven en memoria. En la prueba de dos usuarios, después del reinicio cada uno tiene que volver a iniciar sesión. El progreso persiste y la sesión no; la prueba debe reflejarlo.»
S10. Noted, not required now: «los ejercicios tienen una estructura muy regular (#### Ejercicio N · Título + enunciado + <details><summary>Ver solución</summary>). La extracción debería ser sencilla. Además vienen agrupados por nivel (🟢 Básico, 🟡 Intermedio…). No hace falta ahora, pero queda anotado como posible mejora.»
S11. Contract authority: PLAN.md §3–§4 and §8, docs/api.md (14 routes, shapes, validations, errors, session/CSRF, content policy) and docs/manifiesto.md remain the approved requirements; the approved plan in L2 maps them to tasks. Human owns .env/.env.example (never read/changed); no down -v/prune/volume deletion; services bound to loopback.

## Tasks
T1 | S2.1,S6,S11 | inline | pending | Part 1a: V2 users migration, POST /api/auth/register, JSON error handling, ACCESS_DENIED vs CSRF_REJECTED split, docs/api.md.
T2 | S2.1,S11 | inline | pending | Part 1b: login/logout/GET /api/me, session rotation, CSRF renewal, 2h idle timeout, per-request credentials_version check.
T3 | S2.1,S8,S11 | inline | pending | Part 1c: console password reset via java -jar, terminal required, atomic hash+version; docs; Part 1 independent security verify.
T4 | S5 | inline | pending | Verify and pin dependency versions; show owner before installing (stop point).
T5 | S2.2,S3,S4,S11 | inline | pending | Part 2a: manifest (full syllabus, full commit hash), controlled copy of 00.01 read via git show, fail-fast validator.
T6 | S2.2,S5,S7,S10,S11 | inline | pending | Part 2b: Markdown→sanitized HTML, GitHub anchors pinned from ancla(), exercise extraction, link rewriting/validation.
T7 | S2.2,S3,S11 | inline | pending | Part 2c: catalog tables + startup load, GET courses/course/lesson, resources with test-only SVG/HTML fixtures; Part 2 independent verify.
T8 | S2.3,S11 | inline | pending | Part 3: enrollment, states, progress counts, continuation; two-user API isolation and restart persistence.
T9 | S2.4,S5,S11 | inline | pending | Part 4a: screens (auth, my courses, catalog, index, lesson), Mermaid lazy-loaded with escaped fallback.
T10 | S2.4,S9,S11 | inline | pending | Part 4b: two-user E2E with backend restart and re-login; README/guide/PLAN closure.

## Log
L1. Original owner request (relaying advisor), verbatim: «Sobre H4: sí, pídele el plan

H4 es el hito más grande, así que conviene que el plan venga dividido en partes que se puedan cerrar y commitear por separado:
1. Cuentas: registro, login, logout, la herramienta de consola y la distinción de errores 403 y CSRF.
2. Catálogo e importación de una lección: el manifiesto mínimo y el procesamiento seguro del Markdown.
3. Progreso y continuación: inscripción, estados y avance.
4. Pantallas y la prueba de dos usuarios de principio a fin.

Qué lección importar primero. Revisé las candidatas y recomiendo la 00.01 «Qué es programar»:
- Es la primera del curso, lo natural para un alumno nuevo.
- Tiene 5 diagramas Mermaid y 8 ejercicios con solución, así que pone a prueba la extracción de ejercicios y el plan alternativo si falla un Mermaid, que el manifiesto exige comprobar en H4.
- Es mediana (1099 líneas).

Ninguna de las primeras lecciones tiene imágenes, así que los recursos y SVG se probarían más adelante, o con un recurso de prueba.»
L2. Agent proposed plan T1.1–T4.6 (parts 1–4 as in Tasks) with four decisions: source read access, dependencies, ACCESS_DENIED, Markdown processing location.
L3. Advisor answers relayed by owner (S4–S10 quote them). Agent accepted and added console.isTerminal() and pinning ancla() outputs into tests.
L4. Owner relayed advisor recommendation as a custom answer; agent asked for owner's own choice; owner selected «Autorizo H4 completo». Branch feat/h4-first-journey created from main b5847fa, clean tree.
