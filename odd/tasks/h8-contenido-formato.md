# H8 — Contenido según el formato

Objetivo: que la academia muestre lo que `FORMATO.md` pide y H7 dejó provisional: retos de fase, glosario, enlaces entre cursos, proyecto de tipo «modulo», «Mejorar esta lección» y la cabecera del módulo con un campo por línea. Alcance: `PLAN.md`, apartado «H8. Contenido según el formato» (cambios 6, 7, 9, 10 y 11 de la lista de `ESTADO.md`, más la cabecera, pedida por el autor al revisar H7). Rama `feat/h8-contenido-formato`, desde `main` `51cff9e`. Commits locales; el autor revisa en el navegador antes de integrar. Sin remotos.

Pruebas: `./mvnw -B verify` en `backend/`; `npm run test -- --run`, `npm run lint` y `npm run build` en `frontend/`; el recorrido aislado `npm run test:e2e:journey` (con el JAR recién empaquetado). Las pruebas con contenido provisional, siempre en bases desechables. Contenido de prueba: los cursos de ejemplo del kit (`cursos-ejemplo/`, `27893e8`), que ya traen retos, glosario, un enlace a otro curso con ancla y otro a un término del glosario, y un proyecto de tipo «modulo» (`ejemplo-destino`).

## Alcance

- A1. **Retos de fase** (`FORMATO.md` 6): `retos.md` se importa como documento de su fase, sin seguimiento, con su código en `codigo/retos/`. En el índice, al final de cada fase, «Retos de la fase»; el «Siguiente» del último módulo lleva a ellos; los enlaces a `retos.md` llevan a su página. Ocultos mientras `"retos_disponibles": false`.
- A2. **Glosario** (`FORMATO.md` 9): `glosario.md` como documento del curso, sin seguimiento; los enlaces a él y a sus términos llevan a su página con el ancla.
- A3. **Enlaces entre cursos** (`FORMATO.md` 10.2): la dirección de GitHub de otro curso se convierte en enlace a su lección (o retos, glosario…) con el ancla si ese curso está instalado; si no, se queda la de GitHub. Se decide al cargar, así que instalar después el curso de destino convierte el enlace sin reinstalar el de origen.
- A4. **Proyecto de tipo «modulo»** (`FORMATO.md` 8): el módulo que es el proyecto se señala en el índice como proyecto del curso.
- A5. **«Mejorar esta lección»**: un enlace a la página del fichero en el `repositorio` del curso.
- A6. **Cabecera del módulo, un campo por línea**, sin cambiar el formato de los cursos.

## Tareas

T1 | A6 | Cabecera del módulo: cada campo de la cita de cabecera en su propia línea | pendiente.
T2 | A5 | «Mejorar esta lección» | pendiente.
T3 | A1, A2 | Documentos del curso sin seguimiento (retos de fase y glosario): manifiesto, carga, API, páginas y enlaces | pendiente.
T4 | A4 | Proyecto de tipo «modulo» en el índice | pendiente.
T5 | A3 | Enlaces entre cursos | pendiente.
T6 | todos | Documentación, prueba con el curso inicial en el navegador (base desechable) y cierre | pendiente.

## Registro

L1. 16 de noviembre de 2026: H7 integrada en `main` (`140c9ba`) y rama borrada; el autor pide seguir con H8. Rama `feat/h8-contenido-formato` desde `51cff9e`. Decisión del autor del mismo día: el nombre y el logo («Code Academy» en la cabecera y el título) van en H10, como dice `PLAN.md`.
