# Instalar y actualizar cursos sin conexión

Los cursos se instalan como DLC: cada uno desde su repositorio (`FORMATO.md`, en el kit), en la versión que diga su etiqueta, y se guardan en `content/<id>/` con el [manifiesto](manifiesto.md) que genera la academia. Ningún paso arranca Spring, escribe en PostgreSQL ni necesita internet, y no hay ninguna ruta web para instalar. Estado: H8 (16 de noviembre de 2026).

| Comando | Qué hace |
|---|---|
| `backend/content-import generate` | Genera una **copia candidata** nueva: la copia activa más el curso indicado, leído de los objetos de Git del commit fijado. Nunca la aplica. |
| `backend/content-update compare` | Compara la candidata con la activa y escribe un informe: cambios y conflictos. |
| `backend/content-update apply` | Aplica la candidata, solo si el informe no tiene conflictos y nada ha cambiado desde la comparación. Con el servidor parado. |
| `backend/content-courses list` | Escribe `cursos-instalados.json` (en Git) desde la copia instalada. |
| `backend/content-courses reinstall` | Vuelve a instalar exactamente esa lista en una copia nueva. |

Los cinco necesitan el JAR empaquetado (`cd backend && ./mvnw -DskipTests package`); `--help` funciona sin él. Cada comando dice en stderr, con su nombre delante, por qué ha fallado.

## Recorrido habitual

Desde la raíz de la academia, con los cursos al lado en `../Cursos/` (el plan común). Las rutas de trabajo son de ejemplo; tienen que ser distintas y no estar unas dentro de otras.

```sh
# 0. La carpeta de trabajo tiene que existir.
mkdir -p ../trabajo

# 1. Generar la candidata: la copia activa más el curso, en la versión de su etiqueta.
sh backend/content-import generate --active content --source ../Cursos/curso-inicial --tag v1.0.0 \
    --output ../trabajo/candidata --report ../trabajo/generacion.json

# 2. Comparar y revisar el informe.
sh backend/content-update compare --active content --candidate ../trabajo/candidata \
    --source ../Cursos/curso-inicial --report ../trabajo/comparacion.json

# 3. Con el servidor parado, aplicar con las mismas rutas y el mismo informe.
sh backend/content-update apply --active content --candidate ../trabajo/candidata \
    --source ../Cursos/curso-inicial --report ../trabajo/comparacion.json

# 4. Anotar la lista de cursos instalados y hacer commit de ella.
sh backend/content-courses list --active content --output cursos-instalados.json
```

Después se arranca el backend como siempre: carga la copia, la valida entera y sincroniza el catálogo de la base. Si algo no encaja con lo guardado (por ejemplo, desaparece un ejercicio con progreso), no arranca y no cambia la base.

Para instalar otro curso se repite lo mismo: la candidata lleva los cursos que ya había sin tocarlos y el nuevo encima.

## Generar la candidata: `content-import`

```text
Usage: content-import generate --active DIR --source GIT (--tag vX.Y.Z | --commit SHA) --output DIR --report FILE
```

- `--active DIR`: la copia activa (puede estar vacía). Los demás cursos instalados pasan a la candidata sin cambios.
- `--source GIT`: el repositorio del curso. Puede ser relativo y subir con `..` (`../Cursos/curso-inicial`), porque solo se lee: se normaliza y pasa las mismas comprobaciones que las demás rutas (sin enlaces simbólicos, sin nombres protegidos, sin solaparse). Las rutas en las que se escribe no admiten `..`.
- **Qué versión** se instala, una de las dos:
  - `--tag vX.Y.Z`: la etiqueta de una versión publicada (`FORMATO.md` 11.1), la forma normal. Se busca solo entre las etiquetas (ligera o anotada; nunca una rama con ese nombre) y se resuelve a su commit. La `version` de `curso.json` en ese commit tiene que ser la de la etiqueta (`v2.1.0` → `"version": "2.1.0"`); si no, es un hallazgo.
  - `--commit SHA`: un commit completo, para probar contenido sin publicar.
- `--output DIR`: la candidata; no debe existir. `--report FILE`: el informe; tampoco debe existir.

| Salida | Significado |
|---|---|
| 0 | Candidata escrita y validada: `Candidate written; lessons: N; stages: N; resources: N` (más `; reported: N` si hay avisos). |
| 3 | Hallazgos: no se escribe nada (`Candidate not written; findings: N`); el informe los enumera con su ruta. |
| 2 | Argumentos, etiqueta o commit que no existen, copia activa no válida o error de ficheros. |

**Qué hace:**

- Lee `curso.json` del commit y toma de él **toda la identidad**: fases, módulos (los de `"disponible": false`, como pendientes, sin cuerpo), ejercicios y etapas, con sus identificadores tal cual. No lee el índice del curso ni inventa identificadores: un ejercicio que el módulo tiene y `curso.json` no declara, o al revés, es un hallazgo.
- Copia los cuerpos y los ficheros **de los objetos de Git del commit** (nunca de una carpeta en disco, ni HEAD, ni el índice), una sola vez cada uno, y registra su SHA-256 en la procedencia. Las carpetas enlazadas se recorren enteras, también las ocultas, y cada una da su ZIP canónico; si la carpeta está dentro de un proyecto de Maven o Gradle, el ZIP es el del proyecto entero, el más exterior dentro de `codigo/` (H12; [manifiesto](manifiesto.md#recursos-zip-y-procedencia)), y los enlaces al mismo proyecto comparten un solo ZIP. Un fichero con modo `100755` en el árbol del commit queda como `"executable": true` y sale con permiso `755` en el ZIP (H12); el disco no cuenta.
- Si la copia activa tenía un cuerpo adaptado a mano, es un hallazgo (`local adaptation would be replaced`): nunca lo sobrescribe.
- Valida ejercicios, enlaces y anclas con el mismo procesado que usa la academia al servir (`LessonPipeline`). Un enlace que no se resuelve se informa con `ruta:línea` y no se escribe nada; nunca se adivina el destino. Los enlaces a `00-indice.md`, a `README.md` y a unos retos con `"retos_disponibles": false` llevan al índice del curso.
- Instala también los **documentos sin seguimiento** (H8): los retos de cada fase (`retos.md`, si están disponibles) y el glosario (`glosario.md`), con su código.
- **Rutas protegidas:** `.git`, `.env` y `.env.*` nunca se leen ni se copian, salvo el nombre exacto `.env.example` (la plantilla sin secretos que enseñan los cursos). Un fichero así se excluye y sale en `reported`, sin bloquear; un enlace directo a él conserva solo su texto.
- **Codificación de los CSV:** si la carpeta tiene `codificacion.txt` (`FORMATO.md` 7.1), sus `.csv` se sirven con esa codificación (`charset=windows-1252`); una codificación desconocida es un hallazgo. El resto del texto, en UTF-8.
- **Saneado:** si el saneado del HTML solo quita formato inerte (`<span>`, `<u>`, un ancla…), se avisa sin bloquear. Un `<script>`, un marco, un manejador de eventos, una imagen en HTML crudo o un `id` desconocido bloquean.
- Al terminar, valida la candidata entera con el mismo cargador que el arranque. El manifiesto guarda el commit, la etiqueta, la versión y el repositorio del curso; el informe, el `commit` y la `tag`.

## Comparar y aplicar: `content-update`

```text
Usage: content-update compare|apply --active DIR --candidate DIR --source GIT --report FILE
```

Las cuatro opciones son obligatorias, en cualquier orden y sin repetir. `--source` es el repositorio del curso que cambia (relativo y con `..`, como arriba); las demás rutas no admiten `..`, enlaces simbólicos ni solaparse. `compare` exige un informe nuevo.

| Salida | Significado |
|---|---|
| 0 | Comparación sin conflictos (`Comparison written; conflicts: 0`) o aplicación hecha (`Applied; previous root retained at RUTA`). |
| 3 | Comparación con conflictos: no se puede aplicar. |
| 2 | Argumentos o contenido no válidos, informe desactualizado o manipulado, o error de ficheros. |

**Qué se compara:**

- Las dos copias pasan por el cargador completo (identificadores, ejercicios, enlaces, procedencia, ZIP canónicos).
- **Por curso:** cada curso que cambia, y cada curso cuyo commit está en el repositorio indicado, se contrasta byte a byte con los objetos de Git de su commit. En la candidata, además, el `executable` de cada fichero tiene que coincidir con su modo en ese commit (`candidate: source mode differs RUTA at COMMIT`, si no) en los dos sentidos: un `100755` sin el campo y un `true` sobre un `100644`. Esto solo se mira en los cursos que la actualización instala: un curso que se arrastra sin cambios de la copia activa no se comprueba, porque una copia instalada antes de la 1.1.0 no tiene el campo aunque su commit tenga ficheros `100755`, y tiene que poder actualizarse (H12, T8). Ese curso conserva sus ZIP de antes, sin el permiso, hasta que se actualice o se reinstale. Un curso de otro repositorio que no cambia tiene que llegar idéntico («course X changed without its source», si no).
- **Cambios** (se aplican): altas, ejercicios nuevos, un fichero que gana o pierde el permiso de ejecución (`metadata id`, y los bytes de su ZIP), cambios de orden o de fase y retítulos (`retitle id: «antes» -> «después»`; `FORMATO.md` 11.2: el identificador no cambia). Las partes (H12) no guardan avance, así que todo lo suyo es un cambio: cambiarles el título, añadirlas, quitarlas (`removal id`, entre los cambios) o pasar una fase de una parte a otra. Un ZIP de carpeta tampoco guarda avance: si un enlace pasa a dar otro ZIP (el del proyecto entero, al actualizar con la 1.1.0 un curso instalado antes o porque el curso añade un `pom.xml`), el ZIP que ya no se usa sale como `removal id` entre los cambios, y el nuevo, como alta.
- **Conflictos** (no se aplican): identificadores que desaparecen (salvo los de las partes y los ZIP de carpeta), cambian de tipo, de curso o de dueño, lecciones que dejan de estar disponibles, identidades ambiguas y adaptaciones locales que se perderían. No hay `--force` ni migraciones automáticas.
- El informe lleva las huellas de las dos copias, los commits (`baseRefs`, `candidateRefs`) y la versión y la etiqueta de cada curso (`baseVersions`, `candidateVersions`).

**Aplicar:** primero, **parar el servidor** y cualquier otro proceso que escriba ahí (es una condición de uso, no algo que el comando compruebe). `apply` repite la comparación entera y exige que salga exactamente el mismo informe: si se ha tocado cualquiera de las dos copias o el informe, se niega. Después prepara la copia nueva al lado (`content.staged`), la valida otra vez y hace dos renombres atómicos: la activa pasa a `content.previous` y la preparada ocupa su sitio. Entre los dos hay un instante sin copia activa, pero nunca una copia a medias. Si el segundo falla, intenta volver a poner la anterior. `content.staged` y `content.previous` no pueden existir antes; no se borran solos, así que antes de la siguiente actualización hay que archivarlos a mano.

La instalación necesita permisos POSIX: conserva los de la copia activa y crea la preparada con modo `0700` hasta validarla.

**Complementos:** un fichero que no pertenece a ningún manifiesto (un complemento de la academia) se conserva si la candidata no lo toca; la candidata no puede apropiarse de él (`candidate claims complement RUTA`). Las carpetas de primer nivel de `content/` son siempre cursos.

## Lista de cursos instalados: `content-courses`

`cursos-instalados.json`, en la raíz del repositorio y **en Git**, dice qué cursos tiene instalados esta academia: por curso, `id`, `repository` (el `repositorio` de su `curso.json`), `version`, `tag` (`null` si se instaló por commit) y `commit`. No va dentro de `content/` porque `apply` sustituye esa carpeta entera. Nunca se escribe a mano:

```text
Usage: content-courses list --active DIR --output FILE
       content-courses reinstall --list FILE --sources DIR --output DIR --report FILE
```

- `list` escribe la lista de una copia instalada, ordenada por `id`, sustituyendo el fichero entero. Se ejecuta después de cada `apply` y se hace commit del cambio.
- `reinstall` instala **exactamente** esa lista en una copia nueva (`--output`, que no debe existir), un curso encima del otro, con el mismo importador. Cada curso sale de `--sources/<nombre de su repositorio>`, el final de su dirección sin `.git` (`../Cursos/curso-inicial`). Si tiene etiqueta, tiene que seguir apuntando al commit anotado: si se ha movido o ya no existe, se para. Al final comprueba que la copia tiene los mismos cursos, versiones, etiquetas y commits que la lista. Si algo falla, no escribe la copia (salida 3) y el informe dice qué curso y por qué. La copia nueva se compara y se aplica con `content-update`.

## Pruebas

Las pruebas no leen `content/`: instalan los cursos de ejemplo del kit (`cursos-ejemplo/`) con el importador real, en repositorios temporales con fechas fijas, así que todo lo generado sale igual siempre. Las de estos comandos (`ContentImportCliTest`, `ContentUpdateCliTest`, `ContentCoursesCliTest`) usan subprocesos y repositorios sintéticos; `ContentUpdateProgressTest` comprueba, con la aplicación real y una base PostgreSQL desechable, que una actualización conserva el progreso de dos usuarios. El recorrido E2E instala los cursos de ejemplo por su etiqueta.
