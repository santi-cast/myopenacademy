# Comparar y aplicar copias de contenido sin conexión

`backend/content-update` compara una **copia candidata ya preparada** con la copia activa y aplica únicamente un informe sin conflictos. No genera candidatos ni importa contenido por sí solo, no arranca Spring y no escribe en PostgreSQL. La generación y el inventario del origen se hacen antes con `backend/content-import`; el [manifiesto](manifiesto.md#comparar-antes-de-aplicar-límite-del-importador) define la identidad y procedencia.

## Generar el candidato desde el commit fijado

`backend/content-import` es el paso que **genera** una raíz candidata nueva a partir de un commit inmutable del repositorio de origen. No aplica nada, no arranca Spring y no escribe en PostgreSQL. Requiere el JAR empaquetado, igual que el comando anterior, y funciona sin conexión.

```sh
sh backend/content-import --help
sh backend/content-import generate --active /srv/academy/content --source /srv/source-course \
    --commit c8e29163d59f41830eff2d449bf8b02a19905f61 --output /srv/candidates/h5 --report /srv/reports/h5-generation.json
```

- `--active DIR` es la raíz de la copia aceptada: su manifiesto es el mapa permanente de identidad (IDs, títulos, posiciones, fases, ejercicios, recursos, rutas locales y bytes ya importados) y se valida completo antes de generar.
- `--source GIT` es el repositorio de origen y `--commit SHA`, un commit completo de 40 caracteres hexadecimales. Solo se leen objetos Git (`cat-file`, `ls-tree`) de ese commit: nunca HEAD, ramas, etiquetas, el índice, archivos de trabajo ni un archivo comprimido de la raíz del repositorio.
- `--output DIR` debe ser una ruta nueva; el candidato queda en una raíz distinta de la activa y del origen.
- `--report FILE` debe ser nuevo y guarda recuentos y hallazgos con su ruta y línea.

| Salida | Significado |
|---|---|
| 0 | Ayuda, o candidato escrito y validado con el `ContentLoader` puro. |
| 3 | Hallazgos: no se escribe ninguna raíz candidata y el informe los enumera. |
| 2 | Argumentos inválidos, commit ausente o abreviado, contenido activo inválido o error de archivos. El motivo va a stderr con prefijo `content-import:`. |

Mensajes: `Candidate written; lessons: N; stages: N; resources: N`, con el sufijo `; reported: N` cuando hay hallazgos no bloqueantes, y `Candidate not written; findings: N`. El informe guarda `findings` (bloqueantes) y `reported` (no bloqueantes), siempre con la ruta del documento o de origen. El script usa `PropertiesLauncher` con `dev.codeacademy.content.ContentImportCli`, no el main normal ni su parser de contraseña.

Qué garantiza la generación:

- Inventario contrastado con `00-indice.md` del commit fijado: cada lección del manifiesto aceptado necesita su fila y su título exacto. Una discrepancia se informa; no se completa material inventado. Las etapas del «Proyecto evolutivo» salen de su propia sección y nunca cuentan como lecciones.
- Copia literal: cuerpos y recursos son los blobs de ese commit, registrados en `provenance.files` con `sourceSha256` igual a `copySha256`. Una adaptación local se informa como `local adaptation would be replaced` y nunca se sobrescribe.
- Lectura del origen en lote: el árbol se lista una vez (`ls-tree`) y todos los blobs se leen con un único `git cat-file --batch` por generación, de modo que la cantidad de procesos Git no crece con la cantidad de archivos del curso. Cada cuerpo o recurso se lee una sola vez —el cuerpo ya leído se escribe desde esos mismos bytes— y el SHA-256 de un archivo aceptado se calcula una vez y se reutiliza. El hash de cada ZIP es el de los bytes canónicos recién escritos, no el de una segunda lectura del archivo.
- Ejercicios: los aceptados conservan ID, título y orden. Cada encabezado nuevo recibe un ID determinista con el prefijo de su lección (`<lección>-eNN`), de modo que repetir la generación sobre el mismo commit produce el mismo manifiesto; un ejercicio aceptado cuyo encabezado ya no existe se conserva y se informa, nunca se retira en silencio.
- Ejercicios y fragmentos: se validan con el mismo `LessonPipeline` antes de escribir. La asociación de ejercicios es por título exacto y los fragmentos cruzados se registran en `sourceToId` para lecciones y para etapas por igual, con la misma regla de encabezados: los de la propia lección o etapa, los de otra etapa y los del índice del curso.
- Bloque de solución: es el `<details>` de primer nivel cuyo `<summary>` dice «Ver solución», con apertura y cierre equilibrados, de modo que un bloque anidado no lo corta antes de tiempo. Sin ese bloque se usa el primer `<details>`. Lo que sigue a la solución pertenece a la solución y nunca se descarta.
- Sección de ejercicios: el encabezado de la sección y los encabezados de nivel que la agrupan salen con ella; la prosa escrita fuera de todo ejercicio —incluida la entradilla de la sección— permanece en el cuerpo de la lección, así que no desaparece texto de autor.
- Carpetas: se recorren de forma recursiva, incluidas las ocultas, cada archivo se registra como recurso propio de su propietario y se archiva con `FolderZip`; `provenance.generated` guarda el hash del ZIP y sus IDs de entrada. Una ruta que comparten una lección y una etapa produce un recurso por propietario, con ID y ruta local distintos; la entrada de carpeta enumera los archivos de ambos y el ZIP pertenece al propietario cuyo ID ordena primero, con las copias de ese propietario. La entrada de cada carpeta enumera todo archivo reclamado debajo de ella, a cualquier profundidad: si un propietario enlaza la carpeta y otro una subcarpeta, las dos carpetas listan los archivos de los dos, de modo que un árbol compartido nunca queda como dos listas incompletas. Ese contenido se arma en una sola pasada sobre los recursos reclamados y en su orden determinista: ninguna carpeta vuelve a recorrer la colección, porque la pertenencia se resuelve por prefijo de ruta y no por barrido. Los ZIP de una generación se escriben con los mismos bytes canónicos y el manifiesto se valida **una vez para todos**, no una vez por carpeta: un manifiesto inválido se rechaza antes de abrir el primer archivo, así que ninguna carpeta multiplica el costo de la validación y un solo archivo no puede salir de un manifiesto que la validación rechazaría. La entrada pública `FolderZip.write` conserva su validación por llamada para cualquier otro consumidor. Dos lecciones o dos etapas sobre la misma ruta son un conflicto informado.
- Fragmentos sin destino renderizado: un fragmento sobre el índice del curso —que no se importa— o sobre el encabezado de la sección de ejercicios, que se extrae a la lista de ejercicios, resuelve a la entrada del documento sin fragmento, igual que un documento pendiente. No se inventa un ancla.
- El informe de la comparación identifica cada recurso por tipo, propietario y ruta de origen, de modo que un archivo compartido por una lección y una etapa no es una identidad ambigua.
- `.git`, `.env` y cualquier variante `.env.*` no se leen ni se serializan: se excluyen del candidato y se registran en `reported` con su ruta de origen, de modo que el documento afectado se publica sin ese fichero y no se sustituye por uno inventado. Vale también para archivos de carpetas y para miembros de ZIP, y una carpeta sigue siendo copiable sin ellos. Los valores didácticos `secretos/mibiblioteca/jwt/secret` no se excluyen por su nombre y su contenido nunca se imprime.
- Un destino que no se puede resolver (documento sin identidad, fragmento inexistente, enlace absoluto, consulta o esquema no permitido, recurso con fragmento) se informa con `ruta:línea`; no se adivina, no se reescribe y el candidato no se escribe.
- Sanitizador: si lo único que se elimina es formato inerte (`<span>`, `<u>`, `<div>`…), el hallazgo es no bloqueante, se registra con la ruta del documento y la etiqueta exacta, y el HTML renderizado no cambia. Un ancla eliminada es formato inerte en el mismo sentido: el elemento y su `href` desaparecen juntos, su texto permanece y no llega al lector ninguna capacidad de enlace, así que el par se registra como no bloqueante. Un `<script>`, un marco, un manejador de eventos —también en un ancla—, una imagen HTML cruda, un `id` desconocido o cualquier otro atributo siguen bloqueando la carga; no se amplía ningún permiso del allow-list.
- El candidato terminado se valida otra vez con el `ContentLoader` puro, bytes canónicos de cada ZIP incluidos. Si esa validación falla, la raíz candidata queda para inspección y el comando termina con 2.

Después, la comparación y la aplicación siguen el recorrido habitual de abajo.

## Recorrido habitual

Desde la raíz del proyecto, construir una vez el backend con su JDK requerido. El comando nunca compila automáticamente:

```sh
cd backend && ./mvnw -DskipTests package
```

Volver a la raíz del proyecto. Las rutas siguientes son ilustrativas: usar directorios existentes, distintos y fuera unos de otros. La raíz activa contiene los directorios de curso; no es `content/java` sino `content`.

```sh
sh backend/content-update --help
sh backend/content-update compare --active /srv/academy/content --candidate /srv/candidates/h5 --source /srv/source-course --report /srv/reports/h5.json
```

1. Revisar el JSON completo: revisiones inmutables por curso, huellas de ambas copias, IDs añadidos, metadatos, cambios de bytes y motivos de conflicto. `compare` solo crea el informe; no cambia la copia activa ni el candidato.
2. **Detener el servidor y cualquier escritor de estos directorios.** Mantenerlos detenidos durante toda la aplicación. Es una condición operativa, no un bloqueo comprobado contra procesos o servicios.
3. Aplicar exactamente las mismas rutas y el mismo informe:

```sh
sh backend/content-update apply --active /srv/academy/content --candidate /srv/candidates/h5 --source /srv/source-course --report /srv/reports/h5.json
```

4. Arrancar normalmente el backend para cargar y sincronizar el catálogo. Una incompatibilidad con la base rechaza ese arranque; la aplicación offline no hizo escrituras de base ni migraciones personales. Mantener el servidor detenido si es necesario resolver el conflicto.

## Interfaz y resultados

```text
Usage: content-update compare|apply --active DIR --candidate DIR --source GIT --report FILE
Compare prepared copies offline; apply requires the server stopped. No database writes.
```

Las cuatro opciones son obligatorias, admiten cualquier orden y no pueden repetirse. Las rutas relativas se interpretan respecto del directorio del llamador; se rechazan componentes `..`, symlinks y solapamientos, incluido un informe dentro de cualquiera de las tres raíces. El padre del informe debe existir. `compare` exige un informe nuevo: no sobrescribe archivos anteriores. Para otra comparación, elegir otro archivo de informe.

| Salida | Significado |
|---|---|
| 0 | Ayuda, comparación sin conflictos o aplicación terminada. |
| 3 | Comparación escrita con conflictos; no es aplicable. |
| 2 | Argumentos inválidos, contenido inválido, informe desactualizado/manipulado o error de archivos. El motivo va a stderr con prefijo `content-update:`. |

Mensajes de éxito: `Comparison written; conflicts: N` y `Applied; previous root retained at RUTA`. No hay `--force` ni reconciliación automática. `--help` funciona incluso sin JAR, sin crear archivos. Si falta el JAR empaquetado, el script indica el comando de construcción. El script usa `PropertiesLauncher` con `dev.codeacademy.content.ContentUpdateCli`, no el main normal ni su parser de contraseña.

## Qué se verifica

- Ambas copias pasan por `ContentLoader`: esquema v1/v2, IDs, ejercicios, enlaces, fragmentos, recursos, procedencia y ZIP canónico mediante los validadores existentes.
- Cada `source.versionRef` debe identificar un commit completo presente en el repositorio indicado. Solo se leen objetos Git inmutables (`cat-file`, `ls-tree`), nunca HEAD ni archivos de trabajo. Los cuerpos y recursos literales se contrastan byte a byte mediante SHA-256 contra blobs regulares del árbol. También se comprueba la base v1 de H4; sus archivos actuales no se aceptan como una huella limpia por sí solos. Los ZIP se verifican desde sus miembros copiados, no como blobs inexistentes del origen.
- Las copias y rutas fuente no pueden incorporar `.git`, `.env` ni `.env.*`. No se abren esos archivos. Los ejemplos JWT didácticos autorizados no se excluyen por el nombre `secret`.
- Se rechazan retiradas, retítulos, cambio de tipo de identidad, reasignación de curso/lección/propietario, pérdida de `available` o `sourceAvailable` e identidades ambiguas. Cambiar de ID en una ruta ya asociada, o sustituir el ID de un ejercicio con el mismo título y propietario, no migra progreso. Se permiten altas, nuevos ejercicios con encabezados exactos y cambios de orden/fase válidos según H4, conservando IDs.
- El manifiesto activo es el mapa de identidad aceptado. Esta operación no reconstruye el inventario completo ni deduce títulos/IDs nuevos del índice fuente: esa preparación la hace antes `backend/content-import` ([generación](#generar-el-candidato-desde-el-commit-fijado)). No hay transformaciones literales distintas de las ya admitidas por el cargador.
- Las huellas incluyen todos los archivos y directorios de ambas raíces. `apply` vuelve a calcular la comparación completa y exige igualdad exacta con el texto del informe: editar un conflicto, cambiar cualquier copia o reutilizar otras rutas invalida el informe. El informe es un dato vinculante, nunca autoridad para saltarse validaciones.

## Complementos e instalación

Los complementos deben permanecer separados de los archivos gestionados por los manifiestos. Un archivo no gestionado activo se conserva si el candidato no lo cambia ni intenta registrarlo como propio. El candidato solo puede traer un archivo no gestionado si ya existe idéntico como complemento activo. No se borran adaptaciones ni se instalan archivos extra arbitrarios. Los directorios de primer nivel siguen siendo cursos para el cargador normal; no introducir allí una carpeta de complementos sin manifiesto.

Un directorio candidato tampoco puede ocupar el nombre de un complemento activo que sea archivo: se informa `candidate claims complement RUTA` y se rechaza la aplicación.

La instalación requiere permisos POSIX; si no están disponibles, falla sin sustituir la raíz. El stage se crea atómicamente con modo `0700` antes de copiar bytes. Se conservan los atributos de archivos, y los propietarios, grupos y permisos de directorios activos; los directorios nuevos toman los del candidato. La raíz del stage sigue privada hasta restaurar las restricciones anidadas y validar la copia; después recibe los controles de la raíz activa antes del intercambio.

Antes de instalar se reservan dos hermanos de la raíz activa: `content.staged` y `content.previous` para una raíz llamada `content`. Si ya existen, la aplicación rechaza sin sobrescribirlos. Se prepara una sustitución completa en el mismo sistema de archivos, se valida otra vez, se comprueban las huellas y el informe y se realizan renombres atómicos de directorio:

1. raíz activa → `.previous`;
2. `.staged` → raíz activa.

**Entre los dos renombres existe una ventana breve sin raíz activa.** Nunca se expone una copia activa con solo parte de sus archivos. No es una actualización en caliente, una garantía frente a cortes eléctricos ni una transacción distribuida con la base de datos.

Si falla el segundo renombre, se intenta restaurar `.previous` mediante otro renombre atómico cuando la ruta activa sigue libre. No se reemplaza una ruta creada por otro proceso. La copia anterior y/o el stage se retienen en caso de error; el mensaje indica las rutas que inspeccionar. Tras éxito, `.previous` conserva íntegra la raíz anterior. No hay limpieza automática ni borrado recursivo: antes de otra actualización, el operador debe archivar esas rutas explícitamente fuera de este flujo, sin descartar adaptaciones.

## Límites de la evidencia

Las pruebas del CLI usan subprocesos y repositorios sintéticos temporales: salidas, códigos, snapshots de archivos, comparación v1/v2, altas, cambios de bytes, etapas, ZIP, conflictos, informes manipulados y deriva. No usan el origen real ni una base de propietario. La conservación de estados de dos usuarios tras la sincronización normal pertenece a la integración T6; no se afirma una prueba de base de datos desde este comando offline.

## Importación de H5 aplicada

La copia activa `content/` procede ya del commit fijado `c8e29163d59f41830eff2d449bf8b02a19905f61` de «Curso Java Completo», que sustituye como base vigente a las bases aprobadas antes (`f95ae6f…` y `7e71300…`, solo historial). Se produjo con este mismo flujo, sin conexión y nunca contra la base del propietario:

1. `sh backend/content-import generate --active content --source <repo> --commit <sha> --output <dir nuevo> --report <archivo>` escribió un candidato nuevo sin aplicarlo.
2. `content-update compare` informó 0 conflictos.
3. `content-update apply` instaló el candidato y retuvo la raíz anterior en `content.previous`.

Resultado, con esquema v2: 199/199 lecciones disponibles en 21 fases, 14/14 etapas del Proyecto evolutivo y 22642 recursos en el manifiesto (16647 archivos copiados y 5995 ZIP canónicos de carpeta). La lección `java-02.01` de H4 conserva sus IDs y su cuerpo es idéntico byte a byte. Cargar el curso real sin Spring tarda unos 9 s, después de eliminar tres rutas de validación cuadráticas.

Hallazgos no bloqueantes informados en esta generación:

- 4 rutas `.env` protegidas, excluidas por política sin leerlas: en fase-09, `ej05-variables-en-un-fichero-env`; en fase-18, `2.3-variables-y-secretos`, `2.6-una-version-nueva` y `ej08-mi-biblioteca-en-produccion`. Esos ejercicios se publican sin ese fichero de ejemplo.
- 3 eliminaciones de `<i>` inerte, en 18.05, 19.05 y 19.15.
- Un ancla eliminada en 12.16: su enlace de descarga queda como texto plano porque el ID del recurso contiene «ñ» y el patrón de `href` del sanitizador solo admite ASCII. Ampliarlo es una decisión abierta del propietario; no se ha hecho.

Sigue pendiente: la actualización aislada con conservación del progreso de dos usuarios sobre el contenido real (T6), la conciliación final (T7) y el merge, que requiere una aprobación aparte del propietario. Esta sección no afirma que la suite completa del backend haya pasado con el contenido real.
