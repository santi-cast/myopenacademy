# Estado de la academia

> Actualízalo al terminar cada tarea. Es la memoria entre sesiones: lo que no esté aquí o en `PLAN.md` no lo sabrá la sesión siguiente.

> **⚠️ Historial reescrito el 16 de noviembre de 2026 (H11, T2):** se quitó `content/java` de todo el historial. **Los identificadores de commit citados en este fichero, en `PLAN.md` y en `odd/tasks/` con fecha anterior a ese día son los de antes de la reescritura**: no existen en el repositorio actual. La tabla de equivalencias (antiguo → nuevo) y el repositorio de antes, entero, están en una copia local, fuera del repositorio.

## Dónde estamos

**8 de noviembre de 2026:** repositorio creado desde la sesión de la raíz **clonando Code Academy con su historial** (82 commits, hitos H1 a H5 cerrados; ver `PLAN.md`) y sin remoto. Añadidos `CLAUDE.md` y este `ESTADO.md`. El código y el resto de la documentación son, de momento, los de Code Academy.

**9 de noviembre de 2026:** Compose renombrado a `myopenacademy`, `.env` propio, prueba de importación de la fase 1 del curso inicial hecha y **`PLAN.md` reescrito para MyOpenAcademy** (hitos H6 a H11 hasta el pack base 1.0).

Lo que ya existe en los otros proyectos:

- **Contrato de formato:** `../Cursos/kit-cursos/FORMATO.md`, versión 1 (identidad en `curso.json`, enlaces entre cursos, retos, preguntas de repaso, versiones y equivalencias…).
- **Kit:** `formato_academia.py`, `generar_indice.py`, `generar_chuleta_global.py`, `comprobar_mermaid.py` (Mermaid 12.2.0; el frontend usa la 12.3.0 desde el 16 de noviembre de 2026 y el kit tiene que pasarse a la misma) y el verificador de código.
- **Curso inicial:** fase 1 escrita, revisada y **aprobada por el autor** (4 módulos y sus retos), con su `curso.json`.

## Siguiente paso

**9 de noviembre de 2026:** prueba de importación hecha y **`PLAN.md` reescrito con el autor** para MyOpenAcademy, con los hitos H6 a H11 hasta el pack base 1.0 (unos 20 días de trabajo). **Siguiente: H7, cursos como DLC** (`PLAN.md`, apartado 4): rama `feat/h7-…`, tareas en `odd/tasks/` y, como primer paso, el curso de ejemplo pequeño. H6 (uso real) va en paralelo.

**H7 en marcha** (9 de noviembre de 2026) en la rama `feat/h7-cursos-dlc`: tareas y registro en `odd/tasks/h7-cursos-dlc.md`. Hecha la T1 (curso de ejemplo traído del kit a `cursos-ejemplo/`, desde `80e803a`). T4 hecha salvo la etiqueta (A5): el generador lee `curso.json` y genera el curso de ejemplo, `ejemplo-destino` y el curso inicial completo. **T4b hecha:** la comparación verifica cada curso contra su origen, los de otros repositorios tienen que llegar idénticos byte a byte, y los retítulos son un cambio, no un conflicto. Cursos de ejemplo del kit (`bueno` y `ejemplo-destino`) traídos de `3c07a76`. **T3 hecha:** ninguna prueba usa ya el curso de Java ni lee `content/` (las pruebas instalan los cursos de ejemplo con el importador real), y `content/` está fuera de Git salvo su README. **T3b hecha:** «Mis cursos» se ordena por identificador byte a byte (`collate "C"`), igual con cualquier intercalación de la base. **T2 hecha** (16 de noviembre de 2026): solo identificadores `<curso>.` y números de fase y de módulo por el orden de `curso.json`. Cursos de ejemplo renovados desde el kit en `27893e8`: la carpeta 2.4 (ejemplo de `curl`, con `servidor.json`) llega entera al ZIP, comprobado en el E2E (L17). Detalle en `odd/tasks/h7-cursos-dlc.md` (L1 a L26).

**Siguiente, en este orden:**

1. ~~Traer el commit nuevo de los cursos de ejemplo del kit~~ **Hecho** (`4a79c20`, 9 de noviembre de 2026), con la prueba real de la comparación por curso: sin conflictos y aplicada (`odd/tasks/h7-cursos-dlc.md`, L8).
2. ~~**T3:** pruebas sin el curso de Java real y `content/java/` fuera de Git~~ **Hecho** (L9), con **T3b**, el orden de «Mis cursos» (L10).
3. ~~**T2:** solo identificadores `<curso>.` y números de fase y de módulo por el orden de `curso.json`~~ **Hecho** (16 de noviembre de 2026, L14): «Fase 01» y «01.01» comprobados en el E2E. Después, prueba real de instalación del curso inicial (pedida por la raíz; ver «Prueba de instalación del curso inicial»).
4. ~~La etiqueta (A5)~~ **Hecho** (16 de noviembre de 2026, L18): `content-import generate --tag vX.Y.Z`; el manifiesto guarda commit, etiqueta y versión de `curso.json`. Probado con una copia desechable del curso inicial; falta que los cursos etiqueten sus versiones.
5. ~~T5~~ **Hecho** (L19): `--source ../Cursos/curso-inicial` funciona.
6. ~~T6~~ **Hecho** (L20): catálogo con descripción y «Conviene saber antes».
7. ~~T7~~ **Hecho** (L21): `cursos-instalados.json` y `content-courses list|reinstall`.
8. ~~T8~~ **Hecho** (L22): documentación al día, interfaz en tuteo y prueba en el navegador con el curso inicial (`ed6c0b7`).
9. ~~Revisión del autor~~ **H7 aprobada e integrada en `main`** (16 de noviembre de 2026, L26), con dos retoques de texto («Todavía no has añadido ningún curso», «0 de 1 disponible»).

**H8 aprobada por el autor e integrada en `main`** (`d08a5a5`, 16 de noviembre de 2026): cabecera del módulo con un campo por línea, «Mejorar esta lección», retos y glosario con su página (y el «Siguiente» del último módulo de cada fase a sus retos), proyecto desde `curso.json` y enlaces entre cursos. Detalle en `odd/tasks/h8-contenido-formato.md`.

**H9 aprobada por el autor e integrada en `main`** (`f042a55`, 16 de noviembre de 2026): roles, administrador por consola (`--create-admin`), cuentas desactivadas, panel de administración, contraseñas temporales, «Mi perfil» y «MyOpenAcademy» como nombre por defecto. Detalle en `odd/tasks/h9-cuentas-admin.md`.

**H10 aprobada por el autor e integrada en `main`** (`7c719a6`, 16 de noviembre de 2026): variables de entorno, un solo comando con Compose, herramientas de instalación de cursos en Docker, arranque sin cursos, aviso de HTTP en la red, marca en un solo sitio y guía `docs/instalacion.md`. Detalle en `odd/tasks/h10-instalacion.md`.

**H11, pack base 1.0, cerrada en local** (16 de noviembre de 2026): historial sin `content/java`, `gitleaks` limpio, licencias y vulnerabilidades revisadas, Mermaid 12.3.0, ruta y chuleta global como documentos, todas las pruebas de navegador contra el backend real, `README.md` nuevo con capturas, `CONTRIBUTING.md`, `CHANGELOG.md`, nota de la versión (`docs/notas-de-version/1.0.0.md`), el curso inicial instalado desde su etiqueta `v1.0.0` y la versión 1.0.0 integrada en `main` con su **etiqueta local `v1.0.0`**. Detalle en `odd/tasks/h11-pack-base.md` (L1 a L15).

**Uso real antes de publicar** (decisión del autor, 16 de noviembre de 2026): el autor usa la academia en local unos días. La arranca él con `docker compose up -d --build` desde `Academia/` y crea su administrador en su propia terminal (`docker compose run --rm app --create-admin=…`). Listo para ello: `.env` con `DB_PASSWORD` (sin mostrarla), `content/` con el curso inicial instalado desde su `v1.0.0` (T8) y `cursos-instalados.json` al día; el volumen `myopenacademy_postgres_data` todavía no existe (base nueva) y los puertos 8080 y 5434 están libres. **Si sale algo que corregir:** se arregla en un commit nuevo. **Regla del autor para la etiqueta `v1.0.0`** (23 de noviembre de 2026; sustituye a la de volver a ponerla sobre el commit nuevo): **solo se mueve si cambia el producto** (código, README o licencias). Los commits que solo tocan `ESTADO.md` no la mueven y se publican con el `push` de `main`. Por eso no se movió con los commits de `ESTADO.md` y solo pasó de `8bd5732` a `a4d48c0`, el commit de los enlaces del README, al publicar.

**Cuentas de prueba en la instancia local del autor** (23 de noviembre de 2026): `prueba_admin` (administrador, creada con `--create-admin`) y `prueba_alumno` (alumno, registrada desde `/register`). Sirven para las pasadas automáticas con navegador sobre la academia real. No guardan contraseña: antes de cada pasada se les pone una aleatoria con `docker compose run --rm app --reset-password=<usuario>` (necesita una terminal de verdad) y se borra al terminar. No se desactivan. `prueba_alumno` tiene marcado progreso en 01.01 (lección completada, un ejercicio resuelto y otro en curso): sirve para comprobar que el progreso sobrevive a una actualización del curso. Viven solo en la base de datos local; no van a Git ni se publican.

**Primera pasada** (23 de noviembre de 2026, curso inicial v1.0.0), sin fallos:

- **Contenido:** 47 páginas sin errores de JavaScript ni respuestas de error del servidor, y ninguna tarda más de 3 segundos. Los 81 diagramas Mermaid se dibujan en los dos temas. Los 527 enlaces internos y sus anclas son correctos. Los 834 recursos se descargan, y 221 de los 224 ZIP están íntegros.
- **Progreso y permisos:** el progreso persiste al recargar y al volver a entrar, y es propio de cada cuenta. El alumno no entra en `/admin` y `/api/admin/users` le responde 403. El tema se conserva al recargar.
- **Dos detalles, decididos el 23 de noviembre de 2026:**
  1. Los 3 ZIP que no abren son de pega a propósito del curso (`fotos-viaje.zip` y `plantilla-web.zip` del reto 01 de la fase 3, y `fotos-2026.zip` del ejercicio 8 de 03.01). La academia los sirve como ZIP, así que un alumno que los abra verá «archivo dañado». **Se quedan así** (decisión del autor): en el reto 01 de la fase 3 solo sirven para ordenarlos con la terminal, y en el ejercicio 8 de 03.01 para ver qué ficheros selecciona un patrón. No hay que abrirlos.
  2. Los enlaces externos, que la pasada no comprobó, los ha comprobado la raíz el 23 de noviembre de 2026 sobre el curso inicial v1.0.0 (50 direcciones). **No hay ninguno roto de verdad.** Los que fallan son de ejemplo a propósito: dominios `.example` y `.invalid`, IPs de la red de casa, enlaces falsos de los módulos de seguridad y repositorios de ejemplo. El único que hay que vigilar es el de `FORMATO.md` del kit, que da 404 hasta que el kit se publique (lo apunta la sesión del curso).

**Versión 1.0.0 publicada** (23 de noviembre de 2026, con el «sí» del autor, después de publicarse el kit y el curso en `myopenacademy/kit-cursos` y `myopenacademy/curso-inicial`):

- **Antes de publicar:** los enlaces al curso y al kit en `README.md` y `CONTRIBUTING.md` (`a4d48c0`; las tres direcciones respondían con 200). `v1.0.0` movida a ese commit, con el mismo mensaje, porque cambia el README (decisión del autor). `gitleaks` sobre los 164 commits, sin hallazgos; todos los commits llevan el correo `noreply` de GitHub.
- **Publicado:** <https://github.com/santi-cast/myopenacademy>, público, con `main` como rama por defecto y la etiqueta `v1.0.0` (las dos en `a4d48c0`). Descripción: «MyOpenAcademy: academia de programación libre y gratuita que instala sus cursos como DLC. Spring Boot, React y Docker Compose». Temas: `education`, `e-learning`, `learning-platform`, `programming`, `spanish`, `spring-boot`, `react`, `docker-compose` y `open-source`. *Release* «MyOpenAcademy 1.0.0 — el pack base» (<https://github.com/santi-cast/myopenacademy/releases/tag/v1.0.0>), marcada como la más reciente, con `docs/notas-de-version/1.0.0.md` como nota.
- **README en GitHub, comprobado con navegador:** las cuatro capturas cargan (1280 px de ancho). El diagrama Mermaid se dibuja (`flowchart-v2`, sin error de sintaxis). Los enlaces al curso y al kit funcionan.

**Cierre de la publicación** (23 de noviembre de 2026, con la orden del autor): «Report a vulnerability» (avisos privados de vulnerabilidades) activado en GitHub, y la API responde `{"enabled":true}`. `git-filter-repo` borrado del equipo: lo borró el autor, porque la política de seguridad del entorno bloquea el `rm -rf` al agente, y después se comprobó que ya no está.

**Siguiente:** lo que diga `../PLAN.md` (el curso de Java como primer DLC aparte).

**Preguntas abiertas para el autor:**

- ~~**Enlaces de correo (`mailto:`)**~~ **Resuelto** (16 de noviembre de 2026, opción c, L23): la academia los deja como texto y avisa sin bloquear; el curso lo corrigió en `9f55661`, que se instala sin hallazgos (fases 1 a 5).

- ~~**Orden de «Mis cursos»**~~ **Decidido y hecho** (16 de noviembre de 2026, L24): primero el curso inicial (`code-academy.first-course`) y después los demás por título.
- ~~**Historial de Git con `content/java`**~~ **Decidido** (16 de noviembre de 2026): se reescribe sin `content/java` antes del primer `push`, en H11, guardando antes una copia del repositorio. No antes.

**Ficheros con «ñ», tildes y espacios del kit:** traídos de `3c07a76` y recuperada la prueba de descarga con letras no ASCII, ampliada a espacios: nombre exacto dentro del ZIP y en `Content-Disposition`, con `%20` y nunca `+` (L12 y L13). **Para más adelante (opcional):** el nombre de respaldo de la descarga, sin tildes (`ano-pequeno.csv`) en lugar de con guiones bajos (`a_o-peque_o.csv`).

**Para cuando se toquen los ZIP de código:** se generan con los ficheros del commit fijado del curso (objetos de Git), nunca con una copia en disco. Así lo hace hoy el generador; que siga así (nota de la sesión de la raíz, 9 de noviembre de 2026: los cursos llegan siempre con LF por el `.gitattributes` del kit, y la comparación byte a byte depende de eso).

Decisiones del autor del 9 de noviembre de 2026, recogidas en `PLAN.md`:

- **Java mientras tanto, en el Code Academy original**, que sigue intacto y con Java importado. H7 saca `content/java/` sin mantener la lectura del manifiesto antiguo. El original y esta academia no se arrancan a la vez (puerto `5433`, hasta H10).
- **Registro por invitación, después del 1.0.**
- **El idioma del README**, antes de H11.

## Prueba de instalación del curso inicial (16 de noviembre de 2026)

Pedida por la raíz al cerrar T2. Curso inicial instalado con el importador real (`content-import generate`, sobre una copia vacía) desde **`ed6c0b74d7bd4f71a3a1311c17ca387f034f4c80`** (`ed6c0b7`), el último commit que su `ESTADO.md` da como verificado con `--commit` (fase 4 con la segunda opinión aplicada; después solo hay commits de documentación). `--source` con la ruta resuelta al ejecutar (T5 pendiente). Base desechable en memoria (`myopenacademy-prueba-instalacion`, puerto 5440), backend en el 8095 y Vite apuntando a él; trabajo en `backend/target/prueba-instalacion/` (fuera de Git). Todo parado al terminar.

1. **Instalación:** 0 hallazgos; 31 lecciones (16 disponibles, 15 pendientes), 8 fases, 318 recursos y 115 ZIP de carpeta.
2. **Byte a byte:** los 318 recursos y cada fichero de los 115 ZIP son idénticos a su objeto de Git del commit fijado.
3. **CSV en Windows-1252 de la fase 4:** `clientes.csv` (04.01, ejemplo 2.5) y `socios.csv` (04.01, ejercicio 6) llegan byte a byte, sueltos y dentro de su ZIP, con `codificacion.txt` al lado (comprobado que son Windows-1252: `iconv` los convierte bien). El de `reto-01-catalogo-de-la-queseria` no se instala: los retos llegan en H8. **Detalle:** se sirven como `text/plain; charset=utf-8` (con `attachment` y `nosniff`, así que la descarga guarda los bytes tal cual), y para estos dos el `charset` es falso. **Resuelto** (decisión del autor, 16 de noviembre de 2026): el `charset` de un CSV sale de su `codificacion.txt` (`charset=windows-1252`) y los demás siguen en `utf-8`.
4. **`.env.example` excluidos:** la política de rutas protegidas (`.git`, `.env` y `.env.*`) deja fuera dos ficheros de 04.04 (`ejemplos/2.6-configuracion-en-cuatro-formatos/.env.example` y `ejercicios/ej06-configuracion-libreria/.env.example`): no bloquea, sale en el informe, y el alumno recibe esas carpetas (y sus ZIP) **sin** ese fichero. **Resuelto** (decisión del autor, 16 de noviembre de 2026): la academia admite el nombre exacto `.env.example` (texto UTF-8); `.env` y el resto de `.env.*` siguen excluidos.
5. **SVG:** los 5 SVG del curso son ficheros de `codigo/` (02-markdown y 04-xml-csv-y-otros); se descargan como `image/svg+xml`, con `attachment` y `nosniff`, byte a byte. Ningún módulo del curso inicial inserta un SVG como imagen fuera de un bloque de código, pero **ya está cubierto** por el curso de ejemplo `bueno` (`estructura.svg` en 01-ficheros-json): `ExampleLessonContentTest` comprueba el `<img>` y el E2E que la imagen carga en el navegador.
6. **Mermaid:** los 39 diagramas de las 16 lecciones se dibujan en Chromium con el Mermaid del frontend (12.2.0), sin ningún error. `&lt;` y `&gt;` dentro de comillas llegan al código del diagrama como entidades y se dibujan como «<» y «>» (04.05: «¿Total > 50 €?» y «¿edad < 12?», este dentro de una solución plegada). Los diagramas de los `.md` de `codigo/` van en las descargas, no se dibujan.
7. **Anclas:** 1.058 enlaces con ancla, 385 con tildes o «ñ»: ninguno roto (incluidos los que llevan a un ejercicio).
8. **Numeración (T2):** el índice del curso muestra de «Fase 01 · Qué es programar» a «Fase 08 · Proyecto final», con los módulos 01.01, 01.02…, igual que el curso.
9. **Tras las decisiones del autor** (L16): repetida la instalación desde `ed6c0b7`: 0 rutas excluidas, 320 recursos byte a byte (los dos `.env.example`, sueltos y en sus ZIP), los dos CSV en Windows-1252 con `charset=windows-1252` y los otros 12 CSV con `utf-8`.

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
- ~~**Antes de publicar**~~ **Hecho en H11** (`../PLAN.md`, apartado 8.6): rutas absolutas en `odd/tasks/` (las de `PLAN.md` se quitaron al reescribirlo); README desactualizado y con voseo; `gitleaks` sobre todo el historial; licencias de las dependencias (elkjs, dompurify); el nombre MyOpenAcademy en un solo sitio (hoy «Code Academy» está en `index.html`, `src/layout/Layouts.tsx` y `e2e/stages.spec.ts`).
- ~~**`content/` fuera de Git**~~ **Hecho** en H7, T3 (9 de noviembre de 2026). El historial sigue guardando la copia de Java: el autor decide antes de H11 si se reescribe (ver «Preguntas abiertas»).

## H12, academia 1.1.0 (24 de noviembre de 2026; sesión cerrada hasta que el kit publique su 1.1.0)

En la rama `feat/h12-partes` (20 commits sobre `main`, sin integrar ni `push`); el detalle está en `odd/tasks/h12-partes.md` (S1 a S18, L1 a L24).

**Hecho, con pruebas y con revisión externa cuando le tocaba:**
- partes, con las reglas del kit `44a8e32` (validación, manifiesto, API y pantallas), y avance por fase en todos los cursos;
- pruebas de navegador con y sin partes;
- permiso de ejecución de Git en los ZIP;
- ZIP del proyecto Maven o Gradle más exterior dentro de `codigo/`, con la regla del kit `774aeaa`;
- teclas `<kbd>` y listas de tareas.

`./mvnw -B verify` da 946 pruebas y las de navegador, 6 de 6. El curso inicial `v1.0.0` sale idéntico byte a byte, y lo comprueba `StarterCourseGoldenTest`. Con `870e1b0f`, el curso de Java da 0 hallazgos.

**Lo que falta, en este orden:**
1. **T15, `academia_minima`:** el diseño está aprobado por el autor (S17 de `odd/tasks/h12-partes.md`). Espera la regla exacta del kit: formato, mensaje y casos.
2. **Duda de `codigo/` dentro de un proyecto** (tercer hallazgo de la revisión de T11). La decide el kit. Propuesta del autor: solo cuenta la `codigo/` que cuelga directamente de una fase o de `proyecto/`. Cuando el kit decida, la academia la copia con sus casos.
3. **T6:** la nota `docs/notas-de-version/1.1.0.md`, la versión 1.1.0 en `backend/pom.xml` y `frontend/package.json`, y repasar `CHANGELOG.md` y `README.md`, ya adelantados.
4. **Revisión del autor en el navegador antes de integrar** en `main` con `--no-ff`. Después, la etiqueta `v1.1.0` y la *release*, solo con su orden.

**Decidido:** el curso de Java se publica después de la academia 1.1.0, con su README diciendo que la necesita y `"academia_minima": "1.1.0"` en su `curso.json`.

**Pendiente para después de la 1.1.0** (`PLAN.md`, apartado 5): subir el límite de tiempo de una prueba (revisión de T12); quitar la forma de `Markdown.render` sin marcador y probar una lista de tareas con líneas en blanco entre sus elementos (revisión de T13); que el enlace al `README.md` de otro curso lleve a su página.

## Curso de Java: qué admite la academia 1.0.0 (23 de noviembre de 2026)

Pedido por la raíz, sin cambiar código. Pruebas con el JAR recompilado desde `main` (`55879ee`, mismo código que `v1.0.0`), en carpetas temporales y sin Docker:

- **Campos desconocidos en `curso.json`:** se ignoran. Lo comprobé con una copia del curso inicial `v1.0.0` que lleva `ruta_minima` arriba, un campo extra en una fase y otro en un módulo: `content-import generate` escribe la candidata (31 lecciones y 613 recursos) sin hallazgos ni avisos. En el código, `CourseDescriptor` solo lee los campos que conoce. `formato` distinto de `1` sí es un error.
- **El curso de Java desde su commit `e777853`** (`curso.json` provisional): 199 lecciones procesadas en 11 s, con 1,1 GB de memoria; la candidata no se escribe por **252 hallazgos, todos por la misma causa**: 88 títulos de ejercicio con formato Markdown, que en `FORMATO.md` 3.2 tienen que ser texto plano. Cada uno da dos hallazgos (encabezado y `curso.json` no coinciden), y además salen 76 anclas `#ejercicio-…` rotas que nacen de esos títulos. Es la fila «176 títulos con formato» de la lista de partida del curso. Avisos: 3 `<i>` y 4 `.env` de `codigo/`, que se excluyen.
- **`<template>` del 13.02:** la academia 1.0.0 no da ningún hallazgo ni aviso en ese módulo. El fallo lo marca `formato_academia.py` del kit. **Era del kit** (23 de noviembre de 2026): una comilla invertida sin cerrar le hacía empezar el código a mitad de tramo. Ya sigue la regla de CommonMark (`afcf745`, rama `feat/kit-1-1-0`). El kit ha comprobado también que la academia 1.0.0 instala un curso con `ruta_minima` y `RUTA-MINIMA.plantilla.md` con 0 hallazgos.
- **Lo que admite:**
  - las 14 etapas del proyecto (`proyecto.tipo: "etapas"`, con su página y su avance);
  - los enlaces al curso inicial (se resuelven dentro de la academia si está instalado; si no, van a GitHub);
  - las carpetas Maven y Gradle (se empaquetan enteras, sin filtro de tipos; queda por comprobar que `mvnw` y `gradlew` conservan el permiso de ejecución);
  - el tamaño.
- **Lo que no admite:** `partes`, `transicion` y `equivalencias` se ignoran (no hay agrupación por partes en el índice ni en el menú). Sería trabajo para la 1.1.0 de la academia.
- **Instalación de prueba propuesta:** desde el commit del curso cuando haya quitado la fase 00 (su paso 3) y tenga los títulos en texto plano (sin eso, la candidata no se escribe). Se haría en un proyecto de Compose aparte (`-p` y `--env-file` propios, otro puerto) y se desmontaría después.

## Dependencias con el kit y el curso inicial

| La academia espera de… | Qué | Para | Estado |
|---|---|---|---|
| Curso inicial | Fase 1 escrita, con `curso.json` | La prueba de importación | **Hecho** (aprobada por el autor el 8 de noviembre de 2026) |
| Kit | `FORMATO.md` estable | Saber qué tiene que importar | Versión 1, con ajustes dentro de la versión mientras no haya nada publicado |
| Kit | Ampliar su curso «bueno» para que sirva también a la academia: un segundo curso pequeño de destino, **con su propio `id` y su propio `repositorio`** (no los del curso inicial real, para que no puedan chocar; decisión del autor, 9 de noviembre de 2026), con `glosario.md`, un módulo y el proyecto de tipo «modulo»; desde «bueno», un enlace a un módulo de ese curso con ancla y otro a un término del glosario; y que el «Siguiente» del último módulo disponible lleve a `retos.md`. Y ajustar `FORMATO.md` 14: el curso de ejemplo vive en el kit y la academia guarda una copia desde un commit fijo | Curso de ejemplo de H7 (cambio 1) y pruebas de H8 | **Hecho** por el kit en `94d93c8` (9 de noviembre de 2026): `bueno` y `ejemplo-destino` (id y repositorio propios), `FORMATO.md` 9 (cualquier curso puede tener glosario) y 14 (los cursos de ejemplo viven en el kit). Copiados a `cursos-ejemplo/` |
| Kit | Un fichero con «ñ» y tilde en el nombre dentro del curso de ejemplo (en una carpeta de `codigo/`) | Recuperar la prueba de descarga de un recurso con letras no ASCII en el identificador, perdida al quitar Java (T3, L9) | **Hecho** por el kit en `23f130f` y, con espacios, en `3c07a76` (9 de noviembre de 2026); traído y prueba recuperada (L12 y L13) |

| Curso inicial | Etiquetar su versión cuando esté completo y aprobado (`v1.0.0`, para el pack base 1.0; `FORMATO.md` 11.1). La academia ya instala por etiqueta (A5) | Instalarlo en la academia como DLC con su versión | **Decidido por el autor** (16 de noviembre de 2026): los cursos no se etiquetan hasta estar completos y aprobados; mientras tanto, se instalan desde un commit |
| Curso inicial | Que la academia instale `.github/` dentro de `codigo/` (plantilla de issue de la fase 7) y la meta en el ZIP | Fase 7 (07.02 y reto 3) | **Confirmado** (16 de noviembre de 2026, H8 L7): solo se protege el nombre exacto `.git`; prueba nueva con `.github/ISSUE_TEMPLATE/` instalado y en el ZIP byte a byte. Nada que cambiar |
| Kit | Avisar de los enlaces de correo fuera de código (opción c del autor) | Que los cursos no lleguen con `mailto:` | **Hecho** por el kit en `e44174e` (16 de noviembre de 2026; `FORMATO.md` 10.7). Respondido al kit: la academia no enlaza los correos sueltos (sin `< >`), porque no usa la extensión de enlaces automáticos de GitHub; GitHub sí los enlaza. El kit avisa también del correo suelto desde `cf6c46d`; el curso inicial da 0 avisos y los cursos de ejemplo no cambian (nada que traer) |
| Kit | Lo mismo para los cursos de ejemplo no hace falta (el E2E los etiqueta en su repositorio temporal); sí, decir en `FORMATO.md` 11.1 o en la guía del kit **cuándo** se etiqueta (al cerrar una versión, después de verificar el commit) | Que los cursos lleguen etiquetados | Pedido el 16 de noviembre de 2026 |
| Curso de Java | Un commit sin la fase 00 (paso 3) y con los títulos de ejercicio en texto plano | La instalación de prueba en la academia, en un proyecto de Compose aparte y con un alumno de usar y tirar (decisión del autor) | **Hecho** (24 de noviembre de 2026): `870e1b0f`, instalado en un proyecto de prueba aparte, fase 01 revisada por el autor y por la raíz, y prueba desmontada |
| Kit | `academia_minima` (regla exacta: formato, mensaje y casos) y la decisión sobre `codigo/` dentro de un proyecto | H12 T15 y el tercer hallazgo de la revisión de T11 | Pedido el 24 de noviembre de 2026 |
| Kit | En `FORMATO.md` 3.2 y en `formato_academia.py`, dentro de su 1.1.0: que una parte sin fases sea un error y que cada parte sea un tramo de fases seguidas en el orden del curso, con las partes una tras otra | Copiar esas dos reglas en H12, T1 (decisión del autor: primero el kit y después la academia) | Pedido el 23 de noviembre de 2026. **Hecho** (24 de noviembre de 2026): el kit las publicó en `44a8e32` (rama `feat/kit-1-1-0`) y la academia las copió en H12 T14. |

| Otros esperan de la academia | Qué | Para | Estado |
|---|---|---|---|
| Kit y curso inicial | La lista de lo que no encaje del formato | Ajustar `FORMATO.md` antes de publicar | **Hecho** (9 de noviembre de 2026): nada que cambiar; la academia aceptará los identificadores con punto tal cual |
| Curso inicial | Un importador que lea `curso.json` | Instalarse en la academia (pack base 1.0) | **Hecho** (H7 y H8). Prueba del curso entero desde `06d4d03` (16 de noviembre de 2026, H11 L5 y L6): se instala tal cual, sin hallazgos ni avisos, tras importar `ROADMAP.md` y `99-chuleta-global.md` como documentos. **Versión 1.0.0** desde `1ab0f22` (H11 L12): limpia. **Instalado desde la etiqueta `v1.0.0`** (`2928b0a`, H11 L14), limpio; en `cursos-instalados.json` |
| Kit | La versión de Mermaid de la academia, para `comprobar_mermaid.py` | Que el kit compruebe los diagramas con la misma biblioteca | **Mermaid 12.3.0** desde `4b688f0` (16 de noviembre de 2026, H11 T5); avisado |
| Kit y curso inicial | Que la academia importe `ROADMAP.md`, `RUTA-MINIMA.md` y `99-chuleta-global.md` (`FORMATO.md` 4) | Que 07.01 y otros enlaces del curso funcionen | **Hecho** en `4be1b6b` (16 de noviembre de 2026): documentos de todo el curso, con página, anclas y enlace en el índice. Nada que cambiar en el formato ni en el curso |
| Kit | Que la academia dibuje las listas de tareas de Markdown, para quitar su aviso | Después del pack base 1.0 (`PLAN.md`, apartado 5) | Pendiente: cuando esté, avisar al kit |

Si algo bloquea, dilo al autor: él pasa la información de una sesión a otra.

## Hecho

| Fecha | Qué |
|---|---|
| 2026-11-08 | Repositorio creado clonando Code Academy con su historial, sin remoto; añadidos `CLAUDE.md` y `ESTADO.md`. |
| 2026-11-08 | Proyecto de Compose renombrado de `code-academy` a `myopenacademy` (y su mención en `docs/desarrollo-local.md`), para no usar nunca los contenedores ni el volumen del original. |
| 2026-11-09 | `.env` propio. Prueba de importación de la fase 1 del curso inicial (`tools/curso_a_manifiesto.py`, base desechable, revisión del autor en el navegador), comprobación de los identificadores con punto y lista de cambios para instalar cursos según `FORMATO.md`. |
| 2026-11-09 | `PLAN.md` reescrito con el autor: hitos H6 a H11 hasta el pack base 1.0, con la lista de cambios de la prueba de importación y lo que faltaba del plan común. |
| 2026-11-09 | H7, T1, T4 (salvo la etiqueta) y T4b: cursos de ejemplo del kit, importador por `curso.json` y comparación por curso con retítulos permitidos. |
| 2026-11-09 | H7, T3 y T3b: pruebas con los cursos de ejemplo instalados por el importador real, `content/` fuera de Git y orden de «Mis cursos» igual con cualquier intercalación. |
| 2026-11-16 | **H10 aprobada por el autor e integrada en `main`** (`7c719a6`): instalación. |
| 2026-11-16 | **H9 aprobada por el autor e integrada en `main`** (`f042a55`): cuentas y administración. |
| 2026-11-16 | **H8 aprobada por el autor e integrada en `main`** (`d08a5a5`): contenido según el formato. |
| 2026-11-16 | **H7 aprobada por el autor e integrada en `main`**: cursos como DLC (importador por `curso.json`, etiquetas, catálogo con recomendaciones, lista de cursos instalados). |
| 2026-11-16 | Orden de «Mis cursos» y del catálogo: primero el curso inicial y después por título (decisiones del autor). |
| 2026-11-16 | Enlaces de correo como texto (aviso sin bloquear) y prueba de instalación del curso inicial desde `9f55661`, fases 1 a 5: 0 hallazgos, todo byte a byte. |
| 2026-11-16 | H7, T8: documentación al día, interfaz en tuteo y prueba en el navegador con el curso inicial; hallazgo del `mailto:` en la fase 5. |
| 2026-11-16 | H7, T7: lista de cursos instalados (`cursos-instalados.json`) y `content-courses list|reinstall`. |
| 2026-11-16 | H7, T6: catálogo con la descripción y las recomendaciones de `curso.json` (API y pantalla). |
| 2026-11-16 | H7, T5: `--source` relativo y con `..` (solo el origen, que solo se lee). |
| 2026-11-16 | H7, A5: instalar desde una etiqueta de versión (`--tag`), con commit, etiqueta y versión en el manifiesto. |
| 2026-11-16 | Cursos de ejemplo del kit en `27893e8`: la carpeta 2.4 (`servidor.json` y `sesion.txt`) llega entera al ZIP, comprobado en el E2E. |
| 2026-11-16 | Prueba de instalación del curso inicial desde `ed6c0b7`; `.env.example` admitido y `charset` de los CSV desde `codificacion.txt` (decisiones del autor). |
| 2026-11-16 | H7, T2: solo identificadores `<curso>.` (forma de `FORMATO.md` 3.3) y números de fase y de módulo por el orden del curso («Fase 01», «01.01»). |
| 2026-11-16 | H11: pack base 1.0 cerrado en local, con la etiqueta `v1.0.0` sobre `main`; el curso inicial instalado desde su `v1.0.0`. Sin remoto ni `push`. |
| 2026-11-23 | **Publicada la 1.0.0** en <https://github.com/santi-cast/myopenacademy> (público): `main` y `v1.0.0` en `a4d48c0` (enlaces al kit y al curso en el README; la etiqueta se movió ahí), con su *release*, descripción y temas. README comprobado en GitHub con navegador: capturas y diagrama bien. «Report a vulnerability» activado y `git-filter-repo` borrado del equipo. |
