# El manifiesto de cada curso instalado

Cada curso instalado vive en `content/<id>/` con su `manifest.json`: el formato **interno** con el que la academia guarda un curso ya instalado. **No se escribe a mano**: lo genera `backend/content-import` a partir del `curso.json` del curso, en el commit fijado (el contrato con los cursos es `FORMATO.md`, en el kit). El backend lo carga y lo valida entero al arrancar (`ContentLoader`), y si algo no cuadra no arranca. Cómo se genera, se compara y se aplica: [actualización de contenido](actualizacion-contenido.md). Qué devuelve la API: [API](api.md).

Estado: H8 (16 de noviembre de 2026). Versión del formato: `schemaVersion: 2`. El cargador sigue admitiendo la versión 1 (sin etapas, ZIP, procedencia ni documentos), que ya no genera nadie.

## De `curso.json` al manifiesto

| `curso.json` (`FORMATO.md` 3) | Manifiesto |
|---|---|
| `id` | `course.id` y `course.namespace` (prefijo de todos los identificadores) |
| `titulo`, `descripcion`, `conviene_saber_antes` | `course.title`, `course.description`, `course.recommended` |
| `version`, `repositorio`, la etiqueta y el commit instalados | `source.courseVersion`, `source.repository`, `source.versionTag` (o `null` si se instaló por commit) y `source.versionRef` (commit completo); `source.name` es el título |
| `fases` | `phases`, en el mismo orden, con posiciones 1…N |
| `modulos` | `lessons`, en el orden del curso y con posiciones 1…N en todo el curso; `"disponible": false` da una lección pendiente (`sourceAvailable` y `available` a `false`, sin cuerpo ni ejercicios) |
| `ejercicios` | `lessons[].exercises`, con su `id` y su `titulo` |
| `proyecto` | `course.project`: `kind` (`etapas` o `modulo`), `title` y, si es un módulo, `lessonId`; las etapas, en `projectStages` (vacío si no hay) |
| `retos` de cada fase (si `retos_disponibles`) y `glosario.md` | `documents`: documentos sin seguimiento (abajo) |

**Nada se inventa:** todos los identificadores de fases, lecciones, ejercicios y etapas salen de `curso.json` tal cual. Lo único que pone la academia son los identificadores de los recursos (abajo). Los números «Fase 01» y «01.01» que se ven en pantalla salen del orden, nunca de un identificador.

## Ejemplo

Extracto del curso de ejemplo del kit (`cursos-ejemplo/bueno`), recortado:

```json
{
  "schemaVersion": 2,
  "source": {
    "name": "Curso de ejemplo",
    "versionRef": "<commit completo de 40 caracteres>",
    "versionTag": "v1.0.0",
    "courseVersion": "1.0.0",
    "repository": "https://github.com/myopenacademy/curso-ejemplo"
  },
  "course": {
    "id": "ejemplo", "namespace": "ejemplo", "title": "Curso de ejemplo",
    "description": "Un curso pequeño que cumple FORMATO.md, para las pruebas del kit.",
    "recommended": ["inicial"],
    "project": { "kind": "etapas", "title": "Mi catálogo", "lessonId": null }
  },
  "phases": [ { "id": "ejemplo.fase.primeros-pasos", "title": "Primeros pasos", "position": 1 } ],
  "lessons": [
    {
      "id": "ejemplo.ficheros-json", "title": "Ficheros JSON", "position": 1,
      "phaseId": "ejemplo.fase.primeros-pasos",
      "sourcePath": "fase-01-primeros-pasos/01-ficheros-json.md",
      "sourceAvailable": true, "available": true,
      "bodyPath": "ejemplo/lessons/ejemplo.ficheros-json.md",
      "exercises": [
        { "id": "ejemplo.ficheros-json.un-pedido", "lessonId": "ejemplo.ficheros-json", "title": "Un pedido de la tienda" }
      ]
    }
  ],
  "resources": [ "…" ],
  "sourceToId": [ "…" ],
  "projectStages": [ "…" ],
  "documents": [
    { "id": "ejemplo.fase.primeros-pasos.retos", "kind": "retos", "title": "Retos de la fase 01 — Primeros pasos",
      "phaseId": "ejemplo.fase.primeros-pasos", "sourcePath": "fase-01-primeros-pasos/retos.md",
      "bodyPath": "ejemplo/documents/ejemplo.fase.primeros-pasos.retos.md" }
  ],
  "provenance": { "files": [ "…" ], "generated": [ "…" ] }
}
```

## Identificadores

- **Forma** (`FORMATO.md` 3.3): todos empiezan por `<curso>.`; los que vienen de `curso.json` solo llevan minúsculas sin tildes, cifras, guiones y puntos, y los ejercicios cuelgan de su módulo (`<módulo>.<nombre>`). La forma `<curso>-…` del importador antiguo se rechaza.
- **Únicos** entre todos los identificadores del curso (fases, lecciones, ejercicios, etapas y recursos) y, con el prefijo, en toda la academia.
- **Permanentes:** el progreso de los alumnos va unido a ellos. Cambiar un título es un cambio permitido (`FORMATO.md` 11.2), que la comparación enseña como «retitle» y el catálogo actualiza al arrancar; que desaparezca un identificador con progreso es un conflicto.
- **Recursos:** los pone la academia, a partir del dueño (lección o etapa), el nombre del fichero y un resumen de su ruta: `<dueño>-r-<nombre>-<8 hex>` para un fichero y `<dueño>-zip-<carpeta>-<8 hex>` para el ZIP de una carpeta (`ejemplo.ficheros-json-r-año-pequeñocsv-8ab61787`). Conservan las letras no ASCII del nombre, en NFC (una forma descompuesta se rechaza), y nunca llevan espacios (se convierten en guiones). Un curso ya instalado conserva los suyos al actualizarse.

## Cuerpos, ejercicios y enlaces

- `bodyPath` y `localPath` son rutas dentro de `content/`; `sourcePath` es la ruta en el curso, solo para la procedencia y para resolver los enlaces relativos. Al servir no se lee nunca el repositorio del curso.
- De cada lección se extrae la sección «Ejercicios prácticos resueltos»: cada enunciado va a `statementHtml` y cada solución (`<details>`) a `solutionHtml`, saneados por separado y fuera de `bodyHtml`. Cada encabezado «Ejercicio N · Título» se asocia con el ejercicio de `curso.json` de **título exactamente igual** (puntos de código Unicode, sin normalizar). Un encabezado sin ejercicio declarado, o al revés, bloquea la instalación; nunca se inventa un identificador.
- `sourceToId` dice a dónde lleva cada ruta del curso: una lección o etapa (con sus `fragments`, las anclas al estilo de GitHub, incluidas tildes y repetidas), un recurso, una carpeta (`resourceIds`, la lista ordenada de sus ficheros, y `zipResourceId`) o el índice del curso (`courseId`, para `00-indice.md`). Los enlaces se resuelven desde el documento que los contiene y se validan contra el HTML final; un enlace que no se resuelve bloquea la instalación y nunca se sustituye por un destino adivinado.
- Los enlaces a `00-indice.md`, a `README.md` y a unos retos todavía no disponibles llevan al índice del curso; los enlaces a los retos y al glosario, a su página (`/documents/<id>`), con el ancla.
- **Enlaces entre cursos** (`FORMATO.md` 10.2): un enlace a `<repositorio>/blob/main/<fichero>#ancla` de un curso **instalado** se convierte, al cargar, en el enlace interno a su lección, etapa o documento (con el ancla si el destino la tiene). Si ese curso no está instalado, o el fichero no es uno de esos documentos, se queda la dirección de GitHub. No se guarda en el manifiesto: instalar después el curso de destino convierte el enlace sin reinstalar el de origen.
- La cita de cabecera del principio (`FORMATO.md` 5.2) se muestra con un campo por línea, sin cambiar el formato del curso.
- Un enlace a una lección pendiente lleva a «todavía no disponible», sin fragmento.
- Una carpeta enlazada se convierte en un enlace a su ZIP seguido de un enlace por fichero con su nombre original.
- Las imágenes Markdown tienen que apuntar a un recurso registrado PNG, JPEG o SVG; el HTML crudo no puede meter imágenes.

## Fases y etapas

- Las fases van de 1 a N, sin huecos, y cada una tiene al menos una lección; las lecciones de una fase son consecutivas y siguen el orden de las fases.
- Al sincronizar el catálogo pueden cambiar el título y la posición de una fase, y una lección puede cambiar de fase. Que desaparezca una fase guardada es un conflicto.
- Las etapas del proyecto (`projectStages`) tienen sus propias posiciones 1…N, no tienen fase ni ejercicios y no cuentan en el avance de lecciones.

## Documentos sin seguimiento

`documents` (opcional en el esquema 2) guarda los retos de cada fase (`kind` `retos`, con su `phaseId`, identificador `<fase>.retos`; `FORMATO.md` 6) y el glosario (`kind` `glosario`, sin fase, `<curso>.glosario`; `FORMATO.md` 9), con su `title` (el `# ` del documento), su `sourcePath` y su `bodyPath` (`content/<curso>/documents/`). Se procesan como las etapas, son dueños de sus recursos (`documentId`) y no tienen seguimiento: la base no sabe nada de ellos. Como mucho, unos retos por fase y un glosario por curso.

## Recursos, ZIP y procedencia

- Cada recurso tiene un dueño: una lección, una etapa o un documento. Si dueños de tipos distintos enlazan el mismo fichero, cada uno tiene su recurso.
- **Tipo:** los ficheros de texto se sirven como `text/plain; charset=utf-8`; un CSV de una carpeta con `codificacion.txt` (`FORMATO.md` 7.1) se sirve con esa codificación (`charset=windows-1252`), sin tocar sus bytes. Imágenes, PDF y ZIP llevan su tipo; lo demás, `application/octet-stream`.
- **ZIP de carpeta:** uno por carpeta enlazada (`kind: "zip"`, `memberResourceIds`), con los ficheros de la carpeta y de sus subcarpetas con su ruta relativa, sin la carpeta delante. Formato canónico: entradas sin comprimir, nombres en UTF-8, orden registrado, fecha fija, sin entradas de directorio. Al cargar se compara byte a byte con lo que se generaría.
- **Procedencia:** `provenance.files` guarda, para cada cuerpo y fichero, su ruta en el curso, su ruta local y el SHA-256 en el commit y en la copia (siempre iguales: copia literal, desde los objetos de Git del commit, nunca desde una carpeta en disco); `provenance.generated`, los ZIP con su SHA-256 y sus miembros.
- **Rutas protegidas:** `.git`, `.env` y `.env.*` nunca se leen ni se copian, salvo el nombre exacto `.env.example`. Un fichero así se excluye, sale en el informe y no bloquea; un enlace directo a él conserva solo su texto.

## Validación y entrega

La carga rechaza todo, sin cargar nada a medias: versión de esquema desconocida, commit incompleto, identificadores repetidos o fuera de su curso, ficheros que faltan o que no coinciden con su SHA-256, rutas que se salen de `content/` o pasan por enlaces simbólicos, y enlaces o fragmentos rotos.

La entrega sigue la [política de contenido de la API](api.md#contenido-ejercicios-y-recursos): HTML saneado con lista permitida, `<details>` seguros, sin scripts ni manejadores; Mermaid en modo `strict`, y si no se puede dibujar, su código como texto. Los recursos solo se sirven por su identificador registrado, con sesión, `nosniff`, CSP `sandbox` y caché privada; PNG y JPEG en línea y lo demás como descarga con su nombre original (`Content-Disposition` con `filename*` en UTF-8).
