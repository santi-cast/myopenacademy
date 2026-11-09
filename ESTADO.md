# Estado de la academia

> Actualízalo al terminar cada tarea. Es la memoria entre sesiones: lo que no esté aquí o en `PLAN.md` no lo sabrá la sesión siguiente.

## Dónde estamos

**8 de noviembre de 2026:** repositorio creado desde la sesión de la raíz **clonando Code Academy con su historial** (82 commits, hitos H1 a H5 cerrados; ver `PLAN.md`) y sin remoto. Añadidos `CLAUDE.md` y este `ESTADO.md`. El código y el resto de la documentación son, de momento, los de Code Academy.

**9 de noviembre de 2026:** Compose renombrado a `myopenacademy`, `.env` propio, prueba de importación de la fase 1 del curso inicial hecha y **`PLAN.md` reescrito para MyOpenAcademy** (hitos H6 a H11 hasta el pack base 1.0).

Lo que ya existe en los otros proyectos:

- **Contrato de formato:** `../Cursos/kit-cursos/FORMATO.md`, versión 1 (identidad en `curso.json`, enlaces entre cursos, retos, preguntas de repaso, versiones y equivalencias…).
- **Kit:** `formato_academia.py`, `generar_indice.py`, `generar_chuleta_global.py`, `comprobar_mermaid.py` (Mermaid 12.2.0, la misma biblioteca que usa este frontend) y el verificador de código.
- **Curso inicial:** fase 1 escrita, revisada y **aprobada por el autor** (4 módulos y sus retos), con su `curso.json`.

## Siguiente paso

**9 de noviembre de 2026:** prueba de importación hecha y **`PLAN.md` reescrito con el autor** para MyOpenAcademy, con los hitos H6 a H11 hasta el pack base 1.0 (unos 20 días de trabajo). **Siguiente: H7, cursos como DLC** (`PLAN.md`, apartado 4): rama `feat/h7-…`, tareas en `odd/tasks/` y, como primer paso, el curso de ejemplo pequeño. H6 (uso real) va en paralelo.

**H7 en marcha** (9 de noviembre de 2026) en la rama `feat/h7-cursos-dlc`: tareas y registro en `odd/tasks/h7-cursos-dlc.md`. Hecha la T1 (curso de ejemplo traído del kit a `cursos-ejemplo/`, desde `80e803a`). T4 en curso: el generador ya lee `curso.json` e instala el curso de ejemplo y el curso inicial completo; faltan sus pruebas, la comparación por curso y la etiqueta.

Decisiones del autor del 9 de noviembre de 2026, recogidas en `PLAN.md`:

- **Java mientras tanto, en el Code Academy original**, que sigue intacto y con Java importado. H7 saca `content/java/` sin mantener la lectura del manifiesto antiguo. El original y esta academia no se arrancan a la vez (puerto `5433`, hasta H10).
- **Registro por invitación, después del 1.0.**
- **El idioma del README**, antes de H11.

## Prueba de importación de la fase 1 (hecha, 8 y 9 de noviembre de 2026)

Plan aprobado por el autor: el manifiesto inicial lo genera un script desde `curso.json` (`tools/curso_a_manifiesto.py`, primer borrador del importador nuevo) y se mira en el navegador contra una **base desechable** (contenedor `myopenacademy-prueba-importacion`, en memoria, puerto 5440), no contra la base de `myopenacademy`: el backend no arranca si desaparece un identificador ya guardado, y los de esta prueba son provisionales. El `.env` propio ya existe (contraseña generada, sin mostrar). Trabajo en `backend/target/prueba-importacion/` (fuera de Git). Curso inicial fijado en `c2bfba83d056fb2fc37aef412a0728cfde6a860c`; la comprobación del kit sobre esa copia: 0 errores.

Hallazgos:

1. **`--source` no admite `..`** («path escapes are not allowed»), y el plan común pone los cursos en `../Cursos/<curso>/`. En la prueba se resuelve la ruta al ejecutar.
2. **No puede dar de alta un curso.** Con un contenido vacío: «has no course». Con el de Java: intenta regenerar Java contra el repositorio del inicial (22.836 hallazgos). Solo regenera lo que ya está en el manifiesto aceptado.
3. **Forma de los identificadores:** la academia exige `<curso>-…`; `FORMATO.md` usa `<curso>.<nombre>`. El script traduce el primer punto por un guion (`inicial.que-es-programar` → `inicial-que-es-programar`).
4. **El índice manda, no `curso.json`:** el importador lee las filas `NN.NN` con enlace de `00-indice.md`. Los 27 módulos pendientes no llevan enlace en el índice, así que con el temario completo da 27 hallazgos «has no source index row». Con solo los 4 disponibles, pasa.
5. **Retos:** el enlace «Siguiente» de 01.04 a `retos.md` bloquea (documento sin identidad). Para seguir se usó una variante desechable con ese enlace en texto. Los retos no se importan ni se ven.
6. **Ejercicios con identificadores inventados:** los 35 salen como `inicial-que-es-programar-e01…`, no con los de `curso.json`.
7. **Fragmentos:** el script no calcula las anclas de los enlaces (lo hace el importador); por eso el modo «completo» del script no carga. El importador nuevo tendrá que calcularlas al leer `curso.json`.
8. **Lo que funciona:** las 4 lecciones y los 35 ejercicios pasan el procesado (sección de ejercicios, saneado del HTML, enlaces entre módulos y al índice); las pistas quedan plegadas en el enunciado, antes de la solución; las preguntas de repaso salen plegadas en el cuerpo (sin modo test); la cabecera (versiones, conviene saber antes, tiempo, nivel) sale como una cita de texto, sin usarse como datos.
9. **Sin probar con la fase 1:** enlaces al glosario y a otros cursos, carpetas de código y ZIP, imágenes, partes, proyecto de tipo «modulo», módulos pendientes visibles. Hará falta el curso de ejemplo pequeño (o fases posteriores).

El autor revisó la fase 1 en el navegador (9 de noviembre de 2026): **la cabecera y las preguntas de repaso, plegadas al final, le valen como están**. **Los retos sí tienen que verse** (decisión del autor): en el índice, al final de cada fase, y con el «Siguiente» del último módulo llevando a ellos. Servicios y base desechable parados; no se creó el volumen de `myopenacademy`.

### Identificadores con punto: comprobado que no dan problemas

Pedido del autor: ver si `inicial.que-es-programar` daría algún problema real. Comprobado el 9 de noviembre de 2026:

- **Base de datos:** los identificadores son columnas `text` sin ninguna restricción de forma (las únicas `CHECK` son de usuario, estados y posiciones).
- **Spring:** `/api/lessons/inicial.que-es-programar` llega entero al controlador (404 en JSON, no 400 ni 406), y `/api/lessons/inicial-que-es-programar.json` da 404: Spring no corta el identificador en el punto ni lo toma como extensión.
- **Frontend:** las direcciones se arman con `encodeURIComponent`, que no toca el punto, y Vite devuelve la aplicación para `/lessons/inicial.que-es-programar` (y hasta para `….md`).
- **Ya pasa hoy:** los identificadores de Java llevan punto (`java-01.03`, `java-01.03-e01`) y funcionan desde H4.
- **Lo único que lo impide** es la regla del validador del manifiesto (`ManifestValidator`, prefijo `<namespace>-`) y los identificadores que inventa el importador.
- **A vigilar en H7:** el servidor que sirva la aplicación en internet no debe tomar `.algo` como extensión de fichero.

**Propuesta:** el importador nuevo acepta los identificadores de `curso.json` **tal cual**, con punto, para no tener dos formas del mismo identificador. La academia empieza sin alumnos, así que no hay nada que trasladar.

### Lista de cambios hasta el pack base 1.0

#### A. Contenido: instalar cursos según `FORMATO.md`

Ordenada por dependencia. Estimación orientativa, con sus pruebas: **P** = medio día, **M** = un día, **G** = dos o tres días. Total: unos **10 días** de trabajo, sin contar las revisiones del autor. Lo más incierto es el 2.

| # | Cambio | Por qué (hallazgo) | Tamaño |
|---|---|---|---|
| 1 | **Curso de ejemplo pequeño:** copia, desde un commit fijo del kit, de su curso «bueno» (`../Cursos/kit-cursos/herramientas/pruebas/cursos/bueno/`), para que haya un solo curso de ejemplo. Ya tiene módulo disponible y pendiente, ejercicios con pistas, preguntas de repaso, Mermaid, imagen SVG, código, retos, proyecto por etapas, partes y un enlace a otro curso. Lo que falta se ha pedido al kit (ver «Dependencias»). Las pruebas dejan de usar el curso de Java real | 9; `../PLAN.md` 8.5 | M |
| 2 | **Importador que lee `curso.json`** y da de alta cursos nuevos sin manifiesto previo. El manifiesto interno pasa a generarse desde `curso.json` (sale de `tools/curso_a_manifiesto.py`, más el cálculo de las anclas), deja de leer las filas `NN.NN` del índice y no inventa identificadores de lecciones ni de ejercicios. Los módulos con `"disponible": false` entran como pendientes desde `curso.json` | 2, 4, 6, 7 | G |
| 3 | **Identificadores tal cual**, con punto: el validador exige el prefijo `<curso>.` y la forma de `FORMATO.md` 3.3 (minúsculas sin tildes, cifras, guiones y puntos) | 3 | P |
| 4 | **`--source` con rutas relativas y `..`** (`../Cursos/curso-inicial`). Es de solo lectura (solo objetos Git del commit), así que basta con normalizar la ruta; se mantienen las comprobaciones de enlaces simbólicos, `.env` y solapamiento. `--active`, `--output` y `--report` siguen como están | 1 | P |
| 5 | **Versión del curso:** instalar desde una etiqueta (`v0.1.0`), resolverla al commit y guardar las dos cosas, además de la `version` de `curso.json` | `FORMATO.md` 11 | P |
| 6 | **Retos de fase** (decisión del autor, 9 de noviembre de 2026): `retos.md` se importa como documento de la fase. En el índice del curso, al final de cada fase, una línea «Retos de la fase» (opcionales, sin seguimiento), como hoy las etapas del proyecto. El «Siguiente» del último módulo de la fase lleva a los retos, igual que en GitHub, y los enlaces a `retos.md` se resuelven. No se muestran mientras `"retos_disponibles": false` | 5 | M |
| 7 | **Glosario** como documento del curso sin seguimiento (lo mismo que el 6) | 9; `FORMATO.md` 9 | P |
| 8 | **Varios cursos instalados:** catálogo con `descripcion` y recomendaciones (`conviene_saber_antes`) y `content/` fuera de Git (el curso de Java sale del repositorio) | `../PLAN.md` 8.5 | M |
| 9 | **Enlaces entre cursos:** la dirección de GitHub se convierte en enlace a la lección si ese curso está instalado; si no, se queda la de GitHub | 9; `FORMATO.md` 10.2 | M |
| 10 | **Proyecto** desde `curso.json`: de tipo «modulo» (el inicial) o por etapas (Java) | 9; `FORMATO.md` 8 | P |
| 11 | **Botón «Mejorar esta lección»** con `repositorio` y la ruta del fichero | `../PLAN.md` 8.5 | P |
| 12 | **Partes** (`partes` de `curso.json`) y su avance. Las necesita Java, no el inicial: puede ir después del pack base | `FORMATO.md` 3.2 | M |

**No hace falta tocar** (revisado por el autor): la cabecera del módulo, que se ve como cita; las pistas; las preguntas de repaso, plegadas al final; lo que no está en `curso.json` (`herramientas/`, `CLAUDE.md`, `PLAN-CURSO.md`, `ESTADO.md`) ya no se importa si solo se instala lo declarado. **Después del 1.0:** equivalencias (`FORMATO.md` 11.2); las preguntas de repaso como test; los datos de la cabecera (tiempo, nivel, «Conviene saber antes») como datos, por ejemplo la duración de cada curso en el catálogo (`../PLAN.md` 8.5).

#### B. Plataforma: lo que falta del plan común (`../PLAN.md` 8.5 y 8.6)

Añadido a petición del autor el 9 de noviembre de 2026. Punto de partida comprobado ese día: todo escucha en `127.0.0.1` y está escrito en `application.properties` (`server.address`, `server.port`, base en `127.0.0.1:5433`) y en `vite.config.ts`; no hay compilación del frontend para servirlo (solo Vite en desarrollo); la consola ya tiene `--reset-password`; «Code Academy» está en `index.html`, `Layouts.tsx` y una prueba.

| # | Cambio | Tamaño |
|---|---|---|
| 13 | **Lista de cursos instalados** en el repositorio (curso, versión y commit), con un comando que reinstala exactamente esa lista | P |
| 14 | **Roles de administrador y usuario** (migración, seguridad por rol) y **administrador creado por consola** al instalar (como `--reset-password`; nunca usuario y contraseña por defecto) | M |
| 15 | **Panel de administración:** registro abierto o cerrado (por invitación, después del 1.0); usuarios (listar, desactivar, restablecer contraseña); cursos instalados y su versión (solo verlos: se instalan por consola); nombre de la instancia | G |
| 16 | **Perfil del usuario:** cambiar la contraseña (el tema claro u oscuro ya existe) | P |
| 17 | **Todo en variables de entorno:** dirección y puerto de escucha, puerto y nombre de la base (también el `5433`), dirección pública, carpeta de contenido; documentadas en `.env.example`. Nada de `localhost` escrito en el código | M |
| 18 | **Arrancar con un solo comando:** el backend sirve el frontend compilado (un solo origen y un solo puerto, sin Vite) y Compose levanta la base y la aplicación. Ojo con los identificadores con punto: el servidor no debe tomar `.algo` como extensión | G |
| 19 | **Red local:** escuchar en la red y no solo en `127.0.0.1` (con una variable), documentar la IP fija o el nombre `.local` y avisar de que sin HTTPS las contraseñas viajan sin cifrar | P |
| 20 | **Nombre y logo en un solo sitio** (MyOpenAcademy), con el nombre de la instancia del panel por encima | P |
| 21 | **README del portfolio:** el juego y los DLC, «todo gratis y de código abierto» desde la primera línea, capturas, diagrama de la arquitectura, instalación con un comando y hoja de ruta. Antes, el autor decide el idioma (`../PLAN.md` 10, decisión 11) | M |
| 22 | **Revisión antes de publicar** (`../PLAN.md` 8.6): `gitleaks` sobre todo el historial, rutas absolutas de `PLAN.md` y `odd/tasks/`, licencias de las dependencias, `LICENSE` (MIT), `CONTRIBUTING.md`, `CHANGELOG.md`, documentación sin voseo, versión etiquetada y *release* | M |
| 23 | **Uso real (H6):** el autor estudia con la academia y anota lo que encuentra; tiene prioridad sobre las funciones nuevas. Es tiempo del autor, no se estima; arreglar lo que salga, según lo que salga | — |

Total de la parte B: unos **11 días**. Con la parte A, unos **20 días de trabajo** (sin las partes, el 12, que van con Java) para el pack base 1.0, más las revisiones del autor, lo que salga del uso real y lo que tarde el curso inicial en estar completo.

**Nada que cambiar en `FORMATO.md`:** todo lo que no encajó es de la academia. Avisado al autor para el kit y el curso inicial el 9 de noviembre de 2026.

## Pendientes heredados que no hay que olvidar

Ya recogidos en los hitos de `PLAN.md`; se quedan aquí como recordatorio.

- **H6, uso real:** el autor usa la academia para estudiar y anota lo que encuentra. Tiene prioridad sobre funciones nuevas (`../PLAN.md`, apartado 8.5).
- **Antes de publicar** (H11; `../PLAN.md`, apartado 8.6): rutas absolutas en `odd/tasks/` (las de `PLAN.md` se quitaron al reescribirlo); README desactualizado y con voseo; `gitleaks` sobre todo el historial; licencias de las dependencias (elkjs, dompurify); el nombre MyOpenAcademy en un solo sitio (hoy «Code Academy» está en `index.html`, `src/layout/Layouts.tsx` y `e2e/stages.spec.ts`).
- **`content/` fuera de Git:** hoy guarda en el repositorio la copia del curso de Java (unos 22.600 recursos). Sale del repositorio en H7, con el curso de ejemplo pequeño para las pruebas.

## Dependencias con el kit y el curso inicial

| La academia espera de… | Qué | Para | Estado |
|---|---|---|---|
| Curso inicial | Fase 1 escrita, con `curso.json` | La prueba de importación | **Hecho** (aprobada por el autor el 8 de noviembre de 2026) |
| Kit | `FORMATO.md` estable | Saber qué tiene que importar | Versión 1, con ajustes dentro de la versión mientras no haya nada publicado |
| Kit | Ampliar su curso «bueno» para que sirva también a la academia: un segundo curso pequeño de destino, **con su propio `id` y su propio `repositorio`** (no los del curso inicial real, para que no puedan chocar; decisión del autor, 9 de noviembre de 2026), con `glosario.md`, un módulo y el proyecto de tipo «modulo»; desde «bueno», un enlace a un módulo de ese curso con ancla y otro a un término del glosario; y que el «Siguiente» del último módulo disponible lleve a `retos.md`. Y ajustar `FORMATO.md` 14: el curso de ejemplo vive en el kit y la academia guarda una copia desde un commit fijo | Curso de ejemplo de H7 (cambio 1) y pruebas de H8 | **Pedido** el 9 de noviembre de 2026; no bloquea el arranque de H7 (se empieza con «bueno» tal cual) |

| Otros esperan de la academia | Qué | Para | Estado |
|---|---|---|---|
| Kit y curso inicial | La lista de lo que no encaje del formato | Ajustar `FORMATO.md` antes de publicar | **Hecho** (9 de noviembre de 2026): nada que cambiar; la academia aceptará los identificadores con punto tal cual |
| Curso inicial | Un importador que lea `curso.json` | Instalarse en la academia (pack base 1.0) | Pendiente: cambios 2 a 8 y 10 de la lista de la prueba de importación |

Si algo bloquea, dilo al autor: él pasa la información de una sesión a otra.

## Hecho

| Fecha | Qué |
|---|---|
| 2026-11-08 | Repositorio creado clonando Code Academy con su historial, sin remoto; añadidos `CLAUDE.md` y `ESTADO.md`. |
| 2026-11-08 | Proyecto de Compose renombrado de `code-academy` a `myopenacademy` (y su mención en `docs/desarrollo-local.md`), para no usar nunca los contenedores ni el volumen del original. |
| 2026-11-09 | `.env` propio. Prueba de importación de la fase 1 del curso inicial (`tools/curso_a_manifiesto.py`, base desechable, revisión del autor en el navegador), comprobación de los identificadores con punto y lista de cambios para instalar cursos según `FORMATO.md`. |
| 2026-11-09 | `PLAN.md` reescrito con el autor: hitos H6 a H11 hasta el pack base 1.0, con la lista de cambios de la prueba de importación y lo que faltaba del plan común. |
