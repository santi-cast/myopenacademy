# Manifiesto mínimo — contrato de H2 cerrado para H4

Contrato aprobado en diseño, no creado como manifiesto de contenido ni implementado como importador o esquema de base de datos. Complementa el [plan principal](../PLAN.md) y el [contrato de API](api.md). H5 conserva el formato completo, comparación, actualización y reconciliación.

## Alcance de la primera carga

El conjunto mínimo de H4 tendrá metadatos del temario entero, incluidas entradas no disponibles, y una primera lección disponible con su cuerpo controlado. Se contrastará el catálogo con el índice completo de la revisión elegida, sin inventar recuentos.

- `sourceAvailable`: material escrito en la revisión de origen.
- `available`: contenido importado y utilizable aquí.

Otras entradas, incluso escritas en origen, pueden seguir sin importar en H4. No se afirmará que ya se importó todo el material escrito. Las pendientes no tienen cuerpo ni ejercicios importados.

`source.versionRef` es el **hash completo del commit del repositorio Curso Java Completo desde el que se importa**, correspondiente a la revisión usada para el índice y el contenido. No es una fecha, rama, etiqueta ni referencia abreviada. No se ha inspeccionado el origen ni obtenido un hash en esta actualización documental.

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
  "lessons": [
    {
      "id": "java-lesson-intro",
      "title": "Introducción",
      "position": 1,
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
- IDs de curso, lección, ejercicio y recurso globalmente únicos, permanentes y coherentes con namespace y pertenencia. Los IDs del contenido usan prefijo permanente del curso; no se derivan del título ni se renumeran al reorganizar el origen. El número de origen no es fuente de identidad.
- El cuerpo controlado conserva el material de los ejercicios y su asociación explícita con los IDs permanentes declarados para su lección. Al procesarlo se extrae la sección exacta «Ejercicios prácticos resueltos»: enunciados a `Exercise.statementHtml`, soluciones en `<details>` a `Exercise.solutionHtml`, saneados por separado. Esa sección se excluye de `bodyHtml` para evitar duplicación. No usar el ordinal transitorio como identidad ni adivinar asociaciones al reorganizar. Es política documental, no extracción ya ejecutada.
- `bodyPath`/`localPath` son relativos a la raíz de copia controlada; `sourcePath` aporta solo procedencia y resolución relativa desde el documento de origen. Ninguna ruta habilita lecturas del origen al servir contenido.
- `sourceToId` cubre documentos, recursos y fragmentos referenciados. Resolver cada enlace relativo respecto a su documento de origen, convertirlo a destino por ID y conservar/mapear su fragmento al ID final compatible con GitHub, incluidos duplicados, acentos y puntuación.
- Validar enlaces locales, cruzados, recursos y fragmentos tras separar contenido, reescribir y sanear, también en `statementHtml` y `solutionHtml`. Solo IDs de encabezados generados, no arbitrarios. Un enlace sin fragmento a entrada pendiente lleva a «todavía no disponible»; no fingir fragmento validado si falta el cuerpo de destino. Una referencia no resuelta nunca se sustituye por un destino adivinado.

## Validación y entrega segura

Validación fail-fast de versión/esquema, hash completo real de procedencia (rechazar el marcador), temario incompleto, IDs duplicados o cruzados entre cursos, archivos requeridos ausentes, traversal/escape canónico con symlinks y enlaces o fragmentos rotos. Abortarlo todo sin importación parcial ni alteración de progreso.

La copia solo incluirá lecciones, ejemplos, ejercicios y recursos educativos necesarios; excluirá historial Git, temporales y herramientas internas. Las actualizaciones serán manuales, con comparación previa y conflictos explícitos, sin sobrescribir adaptaciones ni borrar o reasignar progreso silenciosamente. Contenido importado y complementos de la academia permanecen separados.

La entrega aplica la [política de contenido de la API](api.md#contenido-ejercicios-y-recursos): HTML con lista permitida, `<details>`/`<summary>` seguros, sin scripts ni manejadores; Mermaid `strict` y fallback de fuente original escapada sin romper la lección. El SVG generado no elude controles. Recursos autenticados solo por `resourceId` registrado, contención canónica incluidos symlinks, sin rutas arbitrarias ni exposición del origen. SVG con tipo correcto, `nosniff` y CSP `sandbox` sin permisos; HTML educativo como descarga/texto escapado, nunca ejecución del mismo origen.

## Comprobación futura de H4 y límite de H5

H4 comprobará primera lección, extracción sin duplicación, enlaces y fragmentos resultantes, recursos seguros y fallback Mermaid escapado, además de aislamiento y persistencia entre dos usuarios. Son comprobaciones pendientes de implementación, no pruebas ya realizadas.

H5 conserva importación completa, comparación, actualización y reconciliación. Este contrato mínimo no resuelve retiradas ni divisiones: ninguna actualización borrará o reasignará progreso silenciosamente.
