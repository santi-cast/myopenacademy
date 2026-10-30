# Manifiesto — v1 implementado y contrato H5

El contrato v1 aprobado en H2 está implementado en H4: `content/java/manifest.json` y su carga y validación al arrancar. Las secciones hasta «Comprobación de H4 y límite de H5» describen esa base. Las imágenes Markdown seguras y el tachado de T2 ya funcionan con v1. El [contrato v2 de H5](#h5-propuesta-v2-pendiente-de-implementar) ya tiene carga pura de etapas, comprobación de copias, ZIP y entrega HTTP de recursos de etapas; importación, persistencia y rutas de contenido/progreso de etapas siguen pendientes. Complementa el [plan principal](../PLAN.md) y el [contrato de API](api.md).

## Alcance de la primera carga

El conjunto mínimo de H4 tendrá metadatos del temario entero, incluidas entradas no disponibles, y una primera lección disponible con su cuerpo controlado. Se contrastará el catálogo con el índice completo de la revisión elegida, sin inventar recuentos.

- `sourceAvailable`: material escrito en la revisión de origen.
- `available`: contenido importado y utilizable aquí.

Otras entradas, incluso escritas en origen, pueden seguir sin importar en H4. No se afirmará que ya se importó todo el material escrito. Las pendientes no tienen cuerpo ni ejercicios importados.

`source.versionRef` es el **hash completo del commit del repositorio Curso Java Completo desde el que se importa**, correspondiente a la revisión usada para el índice y el contenido. No es una fecha, rama, etiqueta ni referencia abreviada. La primera copia controlada de H4 fija su hash completo en `content/java/manifest.json`.

## Ejemplo ilustrativo

Extracto diminuto, no manifiesto completo ni medición del curso. Números de capítulos y rutas ficticios. `PENDIENTE_HASH_COMPLETO_DEL_COMMIT` es un marcador **inválido en una importación real**, que debe sustituirse por el hash completo verificado del commit de origen. `schemaVersion` versiona el formato; `namespace` y todos los IDs permanecen aunque cambien rutas y orden.

```json
{
  "schemaVersion": 1,
  "source": {
    "name": "Curso Java Completo",
    "versionRef": "PENDIENTE_HASH_COMPLETO_DEL_COMMIT"
  },
  "course": { "id": "java", "namespace": "java", "title": "Java" },
  "phases": [
    { "id": "java-phase-basics", "title": "Primeros pasos", "position": 1 }
  ],
  "lessons": [
    {
      "id": "java-lesson-intro",
      "title": "Introducción",
      "position": 1,
      "phaseId": "java-phase-basics",
      "sourcePath": "01/introduccion.md",
      "sourceAvailable": true,
      "available": true,
      "bodyPath": "java/lessons/intro.md",
      "exercises": [
        { "id": "java-exercise-first", "lessonId": "java-lesson-intro", "title": "Primer ejercicio" }
      ]
    },
    {
      "id": "java-lesson-next",
      "title": "Siguiente módulo",
      "position": 2,
      "phaseId": "java-phase-basics",
      "sourcePath": "02/siguiente.md",
      "sourceAvailable": true,
      "available": false,
      "bodyPath": null,
      "exercises": []
    }
  ],
  "resources": [
    {
      "resourceId": "java-resource-example",
      "lessonId": "java-lesson-intro",
      "sourcePath": "01/ejemplo.txt",
      "localPath": "java/resources/ejemplo.txt",
      "mediaType": "text/plain"
    }
  ],
  "sourceToId": [
    {
      "sourcePath": "01/introduccion.md",
      "lessonId": "java-lesson-intro",
      "fragments": { "introduccion": "introduccion" }
    },
    { "sourcePath": "02/siguiente.md", "lessonId": "java-lesson-next", "fragments": {} },
    { "sourcePath": "01/ejemplo.txt", "resourceId": "java-resource-example" }
  ]
}
```

## Identidad, extracción y rutas

- Incluir cada entrada del índice de la revisión elegida con título, posición, identidad y disponibilidad; no deducir el total del extracto. `available: true` exige `sourceAvailable: true`, cuerpo existente y ejercicios/recursos válidos.
- IDs de curso, fase, lección, ejercicio y recurso globalmente únicos, permanentes y coherentes con namespace y pertenencia. Los IDs del contenido usan prefijo permanente del curso; no se derivan del título ni se renumeran al reorganizar el origen. El número de origen no es fuente de identidad.
- El cuerpo controlado conserva el material de los ejercicios y su asociación explícita con los IDs permanentes declarados para su lección. Al procesarlo se extrae la sección exacta «Ejercicios prácticos resueltos»: enunciados a `Exercise.statementHtml`, soluciones en `<details>` a `Exercise.solutionHtml`, saneados por separado. Esa sección se excluye de `bodyHtml` para evitar duplicación. No usar el ordinal transitorio como identidad ni adivinar asociaciones al reorganizar.
- Cada ejercicio de la lección se asocia con el ejercicio del manifiesto cuyo `title` es exactamente el texto plano de su encabezado tras «Ejercicio N · » (código en línea sin comillas invertidas): igualdad de puntos de código Unicode, sin más recorte que el de CommonMark, sin mayúsculas/minúsculas ni normalización Unicode (una forma NFD frente a NFC es un conflicto). Ese mismo texto es el título que guarda el catálogo y devuelve la API. Reordenar los ejercicios conserva sus IDs; el número N nunca los identifica. Un encabezado sin entrada en el manifiesto, una entrada sin encabezado o un título repetido en la misma lección rechazan la importación completa: retitular, añadir o quitar un ejercicio es un conflicto explícito que resolverá H5. Además, al sincronizar el catálogo, un ejercicio ya guardado no puede cambiar de título, de lección ni desaparecer: el arranque falla sin cambiar la base. Los ejercicios nuevos, con su entrada en el manifiesto, sí se añaden.
- `bodyPath`/`localPath` son relativos a la raíz de copia controlada; `sourcePath` aporta solo procedencia y resolución relativa desde el documento de origen. Ninguna ruta habilita lecturas del origen al servir contenido.
- Una carpeta enlazada (por ejemplo, la de código de un ejemplo) se declara en `sourceToId` con `sourcePath` terminado en `/` y la lista explícita y ordenada de sus archivos: `{ "sourcePath": "01/codigo/ejemplo/", "resourceIds": ["java-resource-a", "java-resource-b"] }`. Cada archivo es además un recurso registrado con su propia entrada. En la lección, el enlace a la carpeta se conserva y va seguido de un enlace por archivo a su recurso, servido como texto plano. Forma exacta: el texto del enlace a la carpeta queda como texto (sin enlace), seguido de ` (`, un enlace por archivo en el orden de `resourceIds` separado por `, `, y `)`; cada enlace apunta a `/api/resources/{resourceId}` y su texto es el nombre original del archivo, tomado del `sourcePath` del recurso, nunca el nombre interno. Por ejemplo: `<code>codigo/01-primer-programa/ejemplos/2.1-hola-mundo/</code> (<a href="/api/resources/java-02.01-r-2.1-hello">Hello.java</a>)`.
- El índice del curso de origen se declara en `sourceToId` con `courseId`: `{ "sourcePath": "00-indice.md", "courseId": "java" }`. Sus enlaces llevan a la pantalla de índice del curso, no a un documento importado.
- `sourceToId` cubre documentos, carpetas, recursos y fragmentos referenciados. Resolver cada enlace relativo respecto a su documento de origen, convertirlo a destino por ID y conservar/mapear su fragmento al ID final compatible con GitHub, incluidos duplicados, acentos y puntuación.
- Validar enlaces locales, cruzados, recursos y fragmentos tras separar contenido, reescribir y sanear, también en `statementHtml` y `solutionHtml`. Solo IDs de encabezados generados, no arbitrarios. Un enlace sin fragmento a entrada pendiente lleva a «todavía no disponible»; no fingir fragmento validado si falta el cuerpo de destino. Una referencia no resuelta nunca se sustituye por un destino adivinado.

## Fases

- `phases` es obligatorio y enumera las fases del curso en el orden del índice de origen, cada una con `id`, `title` y `position`. Cada lección declara su fase con `phaseId`, que debe ser una fase del mismo manifiesto.
- El `id` de una fase es permanente, como el de una lección: lleva el prefijo del curso (por ejemplo, `java-phase-02`), es único entre todos los IDs y no se deriva del título ni se renumera al reorganizar el origen.
- El `title` se copia literalmente del índice del curso (`00-indice.md`) en el commit fijado en `source.versionRef`, sin inventarlo ni traducirlo: es el texto que sigue a «Fase NN — » en cada encabezado `## Fase NN — <título>`, sin ese prefijo. Las secciones del índice que no son fases y no tienen lecciones en el manifiesto (por ejemplo, «Proyecto evolutivo») no se declaran como fases.
- Las posiciones de las fases van de 1 a N, sin huecos ni repeticiones, y cada fase tiene al menos una lección. Recorridas las lecciones por `position`, la posición de su fase nunca retrocede: las lecciones de cada fase son consecutivas y los bloques siguen el orden de las fases. Cualquier incumplimiento rechaza la carga completa.
- Al sincronizar el catálogo, el título y la posición de una fase pueden cambiar. Mover una lección a otra fase está permitido y no es un conflicto, porque el progreso no se refiere a fases. Una fase ya guardada que desaparece del manifiesto, cambia de ID (se trata como desaparecida) o pasa a otro curso es un conflicto explícito: el arranque falla sin cambiar la base.

## Validación y entrega segura

Validación fail-fast de versión/esquema, hash completo real de procedencia (rechazar el marcador), temario incompleto, IDs duplicados o cruzados entre cursos, archivos requeridos ausentes, traversal/escape canónico con symlinks y enlaces o fragmentos rotos. Abortarlo todo sin importación parcial ni alteración de progreso.

La copia solo incluirá lecciones, ejemplos, ejercicios y recursos educativos necesarios; excluirá historial Git, temporales y herramientas internas. Las actualizaciones serán manuales, con comparación previa y conflictos explícitos, sin sobrescribir adaptaciones ni borrar o reasignar progreso silenciosamente. Contenido importado y complementos de la academia permanecen separados.

La entrega aplica la [política de contenido de la API](api.md#contenido-ejercicios-y-recursos): HTML con lista permitida, `<details>`/`<summary>` seguros, sin scripts ni manejadores; Mermaid `strict` y fallback de fuente original escapada sin romper la lección. El SVG generado no elude controles. Recursos autenticados solo por `resourceId` registrado, contención canónica incluidos symlinks, sin rutas arbitrarias ni exposición del origen. SVG con tipo correcto, `nosniff` y CSP `sandbox` sin permisos; HTML educativo como descarga/texto escapado, nunca ejecución del mismo origen.

## Comprobación de H4 y límite de H5

H4 comprobó la lección 02.01, la extracción sin duplicación, enlaces y fragmentos, recursos seguros y el fallback Mermaid escapado, además del aislamiento y la persistencia entre dos usuarios. Los recursos SVG y el fallback Mermaid se probaron con contenido de prueba, porque 02.01 no tiene imágenes ni diagramas rotos.

H4 no resuelve comparación, importación completa ni etapas. H5 rechazará retiradas y divisiones, sin borrar ni reasignar progreso; no implementará reconciliaciones destructivas automáticas.

Observaciones heredadas de H4, no recuentos verificados del nuevo commit ni requisitos adicionales:

- Descargar una carpeta de código completa como `.zip`.
- Resuelto en la primera unidad T2: `commonmark-ext-gfm-strikethrough` 0.31.0, alineado con CommonMark; la observación heredada contaba tachado `~~` en 5 lecciones.
- Resuelto en esa unidad: imágenes Markdown registradas con `src` generado y `alt` escapado; el recuento heredado era de 157 PNG/JPG y 60 SVG, no un inventario nuevo.
- Repetir justo antes de la importación masiva la comprobación del formato «Ejercicio N · Título»; otra variante rechaza la importación.
- Un subtítulo «Ejercicios…» dentro de la sección de ejercicios daría el error de encabezado mal formado (hoy no hay ninguno).
- La sección «Proyecto evolutivo» no está modelada en H4; el contrato H5 siguiente la separa de las lecciones.
- Jackson acepta posiciones como `"2"` o `2.0` y las convierte en enteros.
- La negrita y la cursiva dentro de etiquetas Mermaid no se muestran (unos 50 archivos).
- Un diagrama Mermaid con directivas, frontmatter, interacciones, imágenes o `url()`/`@import` en estilos se muestra como código fuente (hoy ninguno en el curso).
- En las etiquetas Mermaid solo se admiten las entidades `&quot;`, `&lt;`, `&gt;` y `&amp;`, las únicas que usa el curso (en 5 archivos); un diagrama con cualquier otra entidad se muestra como código. Mermaid parte en dos las palabras más anchas que una línea («Pedi / do»); podría ajustarse con `flowchart.wrappingWidth`.
- `lessons.phase_id` podría pasar a `NOT NULL` en una migración posterior, cuando todas las bases estén sincronizadas.

## Imágenes y tachado disponibles en v1

La primera unidad T2 amplía el procesamiento de cuerpo, enunciado y solución sin cambiar el esquema. Las imágenes usan la misma resolución relativa al `sourcePath` del documento y el mismo `sourceToId` que los enlaces. El destino debe ser un recurso registrado con tipo PNG/JPEG/SVG; no se aceptan URLs externas o `data:`, rutas absolutas, consultas, fragmentos ni destinos no gráficos. Escribir `/api/resources/...` directamente tampoco acredita procedencia. El HTML crudo no puede introducir `img`, aunque copie una URL resuelta; solo las imágenes Markdown resueltas generan `src` y `alt`, sin otros atributos.

El tachado `~~texto~~` se convierte en `<del>` saneado en los tres campos; los bloques y tramos de código conservan sus literales. Se mantienen los IDs de encabezados, los enlaces y la política Mermaid. La expansión exacta de carpetas v1 por archivos no cambia; ZIP requiere v2.

PNG/JPEG registrados se sirven `inline` con el nombre original; SVG y los demás recursos siguen como descarga. Todos conservan sesión, `nosniff`, CSP `sandbox`, caché privada y comprobación canónica al servir. Las pruebas HTTP cubren estos encabezados y el rechazo de symlinks. Una verificación independiente en Chromium comprobó PNG/JPEG y SVG contra el backend real y PostgreSQL desechable: las imágenes cargaron para dos usuarios autenticados; sin sesión, 401 y sin imagen. SVG conservó sandbox/descarga. No es una prueba del recorrido completo de lección en React.

## H5: propuesta v2 pendiente de implementar

Contrato de T1 para S1, S3, S5 y S7–S10; implementación y pruebas en T2–T6 del [alcance aprobado](../odd/tasks/h5-content-import.md). No cambia el contenido v1 existente en esta unidad.

**Estado de la primera unidad T4:** `ContentLoader` admite v1 y el subconjunto v2 de archivos normales (`kind: "file"`), con `projectStages` y `provenance` obligatorios. Devuelve cuerpos disponibles en `CourseContent.stages`, separados de `lessons`; no extrae ejercicios de una etapa. Reutiliza el mismo procesamiento Markdown, saneamiento, imágenes, resolución relativa y validación de fragmentos entre lecciones y etapas. V1 conserva la expansión exacta de carpetas y no admite campos nuevos, aunque sean null.

La procedencia debe cubrir exactamente los cuerpos disponibles y recursos registrados, sin repetir rutas locales y coincidiendo con sus rutas fuente. Se verifica `copySha256` contra archivos contenidos sin symlinks. `sourceSha256` se valida aquí como metadato SHA-256; el [CLI offline de comparación y aplicación](actualizacion-contenido.md) contrasta además los bytes con blobs del commit fuente inmutable. Solo se admite copia literal (`transformation: null`, ambos hashes iguales); no hay transformaciones reproducibles registradas. Una transformación distinta o un `kind` desconocido se rechazan explícitamente. T2 añade `kind: "zip"` y verifica su procedencia generada y sus bytes, según el contrato de carpeta siguiente. No se inventa procedencia para los archivos v1.

La carga y el catálogo ya permiten entregar recursos de etapas disponibles por la ruta HTTP existente. No habilitan rutas de contenido/progreso ni sincronización/persistencia de etapas. No hay cambios de base, frontend, copia educativa ni contadores en esta unidad. ZIP reutiliza el validador y el resolutor existentes.

### Versión, origen e identidad

- El lector H5 admitirá explícitamente `schemaVersion: 1` y `2`; rechazará versiones desconocidas y campos desconocidos según cada versión. V1 conserva su significado: sin etapas, sin ZIP y con expansión de carpetas por archivo. No reinterpretar silenciosamente v1 como v2 ni exigirle campos nuevos.
- V2 conserva `source`, `course`, `phases`, `lessons`, `resources` y `sourceToId`; añade `projectStages` y `provenance`. El candidato H5 fija `source.versionRef` a `f95ae6fba5de2cacf5a4a00387a81b6c7166a632`. Toda lectura del origen procede de ese árbol Git inmutable, nunca de HEAD móvil, índice de trabajo o archivos sin commit.
- Contrastar índice, documentos y recursos de ese árbol: referencia observada de 199 lecciones/21 fases, 169 lecciones disponibles en origen y 14 etapas (12 escritas, 2 pendientes). Una discrepancia se informa y detiene la importación; no se completa material inventado. Las etapas no se incluyen en `lessons` ni alteran su total previsto de 199.
- Conservar todos los IDs H4 y sus pertenencias. Los nuevos IDs se asignan una sola vez con namespace del curso y se mantienen en el manifiesto; no inferir identidad por posición, similitud de texto o nuevo nombre de archivo. Una ruta movida exige asociación inequívoca y explícita al ID anterior o se rechaza.

### Etapas del Proyecto evolutivo

`projectStages` es un array obligatorio en v2 (vacío para cursos sin proyecto). Cada entrada lleva `id`, `title`, `position`, `sourcePath`, `sourceAvailable`, `available`, `bodyPath`. Posiciones 1..N sin huecos ni repeticiones; IDs globalmente únicos también frente a lecciones, ejercicios y recursos. Títulos y orden proceden del índice fijado. No tienen `phaseId` ni generan ejercicios con estados de lección.

Una etapa disponible exige cuerpo UTF-8 importado y enlaces/recursos válidos; una pendiente tiene `available: false` y `bodyPath: null`. `available: true` exige `sourceAvailable: true`. Se aplican saneamiento, IDs de encabezados, Mermaid seguro y validación final de fragmentos; el cuerpo de etapa no pasa por la extracción de ejercicios de lección. El estado manual y la continuación se definen en la [API de etapas](api.md#h5-contrato-aditivo-de-etapas-pendiente-de-implementar).

Extracto ilustrativo v2, no manifiesto completo ni rutas reales del origen:

```json
{
  "projectStages": [
    {
      "id": "java-stage-initial",
      "title": "Etapa inicial",
      "position": 1,
      "sourcePath": "proyecto/inicial.md",
      "sourceAvailable": true,
      "available": true,
      "bodyPath": "java/project/inicial.md"
    }
  ],
  "sourceToId": [
    {
      "sourcePath": "proyecto/inicial.md",
      "stageId": "java-stage-initial",
      "fragments": { "inicio": "inicio" }
    }
  ]
}
```

V2 añade `stageId` como destino alternativo exclusivo en `sourceToId`; `fragments` se admite para lección o etapa. Resolver desde `sourcePath` del documento emisor con las mismas reglas actuales de decodificación, contención y rechazo de consultas/esquemas no permitidos. Lecciones van a `/lessons/{lessonId}`, etapas a `/stages/{stageId}` e índice a `/courses/{courseId}`. Los fragmentos se validan contra el HTML final; para pendientes se elimina el fragmento y se ofrece «todavía no disponible», como en H4.

### Copia verificable, imágenes y carpetas ZIP

`provenance.files` registra cada cuerpo/recurso copiado mediante `sourcePath`, `localPath`, `sourceSha256`, `copySha256`: hashes SHA-256 hexadecimales de 64 caracteres de los bytes del árbol fijado y de los bytes importados, respectivamente. Cada entrada incluye `transformation`: null para copia literal o identificador versionado de la transformación reproducible del importador si los hashes difieren; una transformación desconocida rechaza la comparación. No se presenta una adaptación de la academia como copia literal. `provenance.files` y `provenance.generated` son arrays obligatorios, vacíos si no hay archivos de su tipo; cada `localPath` gestionado aparece una sola vez. El manifiesto conserva la asociación de identidad; un hash no es un ID. Los complementos quedan fuera de las rutas gestionadas y nunca se sobrescriben.

| Extensión v2 | Contrato |
|---|---|
| Recursos de etapas (carga y entrega HTTP implementadas) | Cada recurso tiene exactamente un propietario: `lessonId` o `stageId`, del mismo curso. Disponibilidad del propietario condiciona entrega. No exigir inscripción para leer recursos compartidos autenticados. |
| Imágenes | Resolver Markdown de imagen por `sourceToId` hacia un recurso registrado de tipo PNG/JPEG/SVG, no mediante rutas físicas. Solo `img` con `src` resuelto a `/api/resources/{resourceId}` y `alt` textual; sin imágenes remotas, `data:`, manejadores ni HTML arbitrario. Validar también imágenes en enunciado y solución. |
| Tachado | Admitir `~~texto~~` como `<del>` saneado; no ampliar permisos de HTML por ello. |
| Carpeta ZIP | Conservar `sourcePath` terminado en `/` y `resourceIds` explícitos, ordenados, completos y sin duplicados. Añadir `zipResourceId` a esa entrada: no es otro destino independiente, sino el archivo descargable de esa misma carpeta. |
| Recurso ZIP | En `resources`, añadir una entrada con `kind: "zip"`, `resourceId`, propietario, `sourcePath` de carpeta, `localPath` de ZIP generado, `mediaType: "application/zip"`, `fileName` (nombre original de carpeta más `.zip`) y `memberResourceIds` idénticos a `resourceIds`. Los archivos normales v2 usan `kind: "file"` y conservan sus campos v1. |

El ZIP se genera solo desde miembros registrados copiados, nunca recorriendo una carpeta del servidor a petición del cliente.

`FolderZip.write(manifest, root, resourceId, output)` es el helper reutilizable del futuro importador offline: valida la estructura, lee únicamente copias contenidas sin symlinks y escribe al stream del llamador sin instalar archivos ni cerrar ese stream.

El formato canónico usa entradas STORED (sin compresión), nombres UTF-8, orden registrado y fecha local fija 1980-01-02 00:00; no agrega entradas de directorio, comentarios ni atributos de symlink. La carga compara el archivo instalado completo con esos bytes esperados, incluidos cabeceras, CRC, tamaños, directorio central y fin de archivo, además del hash. No descomprime datos no confiables: los tamaños y el trabajo proceden de los archivos copiados registrados, sin límites educativos arbitrarios ni expansión de bombas. Archivos ZIP equivalentes pero no canónicos también se rechazan.

Sus entradas conservan rutas relativas a la carpeta fuente, sin rutas absolutas, `..`, symlinks ni nombres duplicados. Excluir el ZIP de su propia lista de miembros. Registrar el archivo generado en `provenance.generated` con `localPath`, `copySha256` y `inputResourceIds`; no inventar hash de un ZIP inexistente en origen.

En v2, al añadir `zipResourceId`, el texto original de carpeta se convierte en enlace a `/api/resources/{zipResourceId}`, seguido de los enlaces individuales de H4 en el mismo orden y con sus nombres originales. V1 conserva exactamente la expansión documental previa y rechaza campos ZIP incluso null. Las entradas de carpeta v2 sin decoración conservan la expansión individual ya admitida por la primera unidad T4; el importador debe decorar las carpetas descargables con su ZIP. No añadir rutas de descarga por path. PNG/JPEG usados como imágenes podrán servirse `inline`; SVG mantiene descarga con sandbox (su visualización debe comprobarse en T2), ZIP y demás archivos conservan descarga. Todos mantienen sesión, tipo correcto, `nosniff`, CSP `sandbox`, caché privada sin almacenamiento y comprobación de contención al servir. Véase la [extensión de entrega](api.md#h5-extensión-de-recursos-pendiente-de-implementar).

### Comparar antes de aplicar: límite del importador

T3 implementa `sh backend/content-update compare|apply --active DIR --candidate DIR --source GIT --report FILE` y `sh backend/content-update --help`; véase el [procedimiento de actualización](actualizacion-contenido.md). Compara copias ya preparadas y aplica únicamente informes sin conflictos; no genera candidatos ni realiza el inventario/importación de las 169 lecciones (T5). No hay endpoint web de importación ni lecturas del origen durante solicitudes.

1. **Preparar y comparar sin escrituras activas ni base de datos.** La preparación del candidato queda en T5. El CLI lee los commits fijados, el manifiesto/copia aceptados y sus huellas; produce un informe con revisión base/destino, IDs afectados, altas, cambios de bytes/metadatos y conflictos con motivo. Valida ambas copias con el mismo cargador/resolutor que servirá contenido. Para la primera transición v1, contrasta los cuerpos y recursos literales de H4 con blobs regulares del commit H4; si no se pueden verificar, rechaza, sin bendecir los archivos actuales como base limpia.
2. **Rechazar conflictos antes de aplicar.** Cambios locales respecto de la huella aceptada son adaptaciones, incluso si el origen también cambió. Retítulos de identidades existentes, retiradas, divisiones/fusiones, reasignación de curso/lección/propietario, pérdida de disponibilidad e identidad ambigua son conflictos explícitos, no migraciones automáticas. Nuevos ejercicios de una lección válida se permiten con nuevos IDs y encabezados exactos; ejercicios existentes conservan título y pertenencia. Cambios de orden/fase conservan la regla H4 si no hay conflicto de identidad. Las capacidades de actualización de títulos del sincronizador H4 no autorizan retitular automáticamente en el importador H5.
3. **Aplicar solo sobre la base comparada.** Volver a verificar revisión, manifiesto y hashes de copia activa y candidato; si cambiaron desde la comparación, rechazar sin sobrescribir nada. Un informe con conflictos nunca es aplicable; no incluir `--force` ni inferir equivalencias. Actualizar únicamente contenido gestionado verificado y procedencia; no tocar complementos ni tablas personales. La instalación prepara y verifica una raíz completa hermana en el mismo sistema de archivos y renombra directorios atómicamente, reteniendo la raíz anterior. Entre los dos renombres hay una breve ventana sin raíz activa; no hay copia activa parcial. Ante fallo intenta restaurar la anterior y conserva rutas para inspección, sin borrar directorios arbitrarios.
4. **Sincronizar catálogo después de validar.** `CatalogConfiguration` une carga y sincronización al arrancar. El main independiente del CLI reutiliza la carga pura existente de `ContentLoader`, la resolución y la validación de procedencia/ZIP, sin abrir Spring/DB ni cambiar el arranque normal. `CatalogSynchronizer` sigue perteneciendo al arranque, no a la instalación offline; no hay validadores ni inserciones paralelas. La sincronización sigue transaccional, con comprobaciones de identidad antes de escrituras y sin cascadas destructoras. Una incompatibilidad con catálogo persistido rechaza el arranque y no modifica la base.

La aplicación offline de archivos no se presenta como una transacción distribuida con PostgreSQL. No hay actualización en caliente: validar e instalar con servidor detenido, luego cargar/sincronizar; ante conflicto de base, conservar datos y no servir un catálogo parcialmente actualizado. Un conflicto requiere resolución explícita fuera de este flujo seguro, no autoriza una migración arbitraria.

### Evidencia exigida a las tareas siguientes

- Importación contrastada con el árbol fijo, enlaces e imágenes/ZIP validados y ambas versiones del manifiesto cubiertas con pruebas deterministas.
- Una actualización incremental exitosa conserva IDs, estados, fechas y ambas continuaciones de dos usuarios; las nuevas unidades no reciben estados precargados.
- Rechazos por adaptación, retítulo, retirada, identidad ambigua y base cambiada dejan intactos archivos activos, datos personales y contadores. Un conflicto detectado al sincronizar deja intacta la base.
- Las etapas tienen estados binarios manuales y continuación propia; nunca entran en los contadores de lecciones. Verificar persistencia, propietario, inscripción y CSRF en pruebas aisladas, no en la base del propietario.
