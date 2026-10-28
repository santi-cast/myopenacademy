# Manifiesto mínimo — contrato de H2 cerrado para H4

Contrato aprobado en H2 e implementado en H4: `content/java/manifest.json` y su carga y validación en el backend al arrancar. Complementa el [plan principal](../PLAN.md) y el [contrato de API](api.md). H5 conserva el formato completo, comparación, actualización y reconciliación.

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

H5 conserva importación completa, comparación, actualización y reconciliación. Este contrato mínimo no resuelve retiradas ni divisiones: ninguna actualización borrará o reasignará progreso silenciosamente.

Notas para H5:

- Descargar una carpeta de código completa como `.zip`.
- Añadir `commonmark-ext-gfm-strikethrough`: el tachado `~~` aparece en 5 lecciones.
- Permitir `<img>` con `src` limitado a `/api/resources/…` y `alt`: el curso tiene 157 PNG/JPG y 60 SVG.
- Repetir justo antes de la importación masiva la comprobación del formato «Ejercicio N · Título»; otra variante rechaza la importación.
- Un subtítulo «Ejercicios…» dentro de la sección de ejercicios daría el error de encabezado mal formado (hoy no hay ninguno).
- La sección «Proyecto evolutivo» del índice es contenido del curso que la academia todavía no modela.
- Jackson acepta posiciones como `"2"` o `2.0` y las convierte en enteros.
- La negrita y la cursiva dentro de etiquetas Mermaid no se muestran (unos 50 archivos).
- Un diagrama Mermaid con directivas, frontmatter, interacciones, imágenes o `url()`/`@import` en estilos se muestra como código fuente (hoy ninguno en el curso).
- En las etiquetas Mermaid solo se admiten las entidades `&quot;`, `&lt;`, `&gt;` y `&amp;`, las únicas que usa el curso (en 5 archivos); un diagrama con cualquier otra entidad se muestra como código. Mermaid parte en dos las palabras más anchas que una línea («Pedi / do»); podría ajustarse con `flowchart.wrappingWidth`.
- `lessons.phase_id` podría pasar a `NOT NULL` en una migración posterior, cuando todas las bases estén sincronizadas.
