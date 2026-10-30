# Comparar y aplicar copias de contenido sin conexión

`backend/content-update` compara una **copia candidata ya preparada** con la copia activa y aplica únicamente un informe sin conflictos. No genera candidatos, no importa las 169 lecciones de H5, no arranca Spring y no escribe en PostgreSQL. La generación e inventario del origen pertenecen a T5; el [manifiesto](manifiesto.md#comparar-antes-de-aplicar-límite-del-importador) define la identidad y procedencia.

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
- El manifiesto activo es el mapa de identidad aceptado. Esta operación no reconstruye el inventario completo ni deduce títulos/IDs nuevos del índice fuente: esa preparación pertenece a T5. No hay transformaciones literales distintas de las ya admitidas por el cargador.
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
