# H12 — Partes de los cursos (academia 1.1.0)

Objetivo: agrupar por partes el índice y el menú de los cursos que las tienen, con el avance de cada parte, y mostrar el avance por fase en todos los cursos. Alcance: `PLAN.md`, apartado «H12. Partes de los cursos». Plan aprobado por el autor el 23 de noviembre de 2026. Rama `feat/h12-partes`.

## Specs

- S1 — Regla del autor, textual: «las partes solo cambian los cursos que las tienen; el avance por fase sale en todos». Un curso sin partes, como el inicial, se ve igual que hoy, salvo el avance por fase.
- S2 — Validación de `partes` en `curso.json`, con «exactamente las de FORMATO.md 3.2 y las de formato_academia.py del kit, con los mismos casos» (`comprobar_partes`, kit `db706a0`):
  - `partes` es opcional. Si está, tiene que ser una lista: ««partes» debe ser una lista».
  - Cada parte es un objeto: «cada parte debe ser un objeto { … }».
  - `id` obligatorio, con la forma `<curso>.` y un nombre (solo minúsculas sin tildes, cifras, guiones y puntos), y no lo puede usar ningún otro identificador del curso (fases, módulos, ejercicios, etapas).
  - `titulo` obligatorio: un texto no vacío y sin formato Markdown (`FORMATO.md` 3.2, «Los títulos son texto plano»).
  - `fases` obligatorio y tiene que ser una lista. Cada fase listada existe («la fase «X» no existe») y está en una sola parte («la fase «X» ya está en la parte «Y» (cada fase va en una sola)»). Si hay partes, cada fase del curso está en alguna («la fase «X» no está en ninguna parte (si hay partes, cada fase va en una)»).
  - Un campo desconocido en una parte es un aviso, no un error.
  - Cualquier error es un hallazgo y la candidata no se escribe.
  - **Pendiente del kit**, para cuando esté en su `FORMATO.md` 1.1 y en `formato_academia.py`: una parte sin fases es un error, y cada parte es un tramo de fases seguidas en el orden del curso, con las partes una tras otra.
- S3 — Manifiesto: `parts` opcional en el formato 2, sin subir de versión. Los manifiestos de la 1.0.0 siguen cargando. La carga valida lo mismo que el importador. Al actualizar, cambiar el título de una parte o pasar una fase de una parte a otra es un cambio, no un conflicto.
- S4 — API: `GET /api/courses/{id}` devuelve las partes (una lista vacía si no hay). Sin tablas nuevas.
- S5 — Índice del curso: cada parte es un bloque con su título y su avance («12 de 40 lecciones»); dentro, sus fases con su avance y sus módulos.
- S6 — Menú lateral: partes plegables, con solo la de la lección actual abierta; dentro, fases plegables como hoy; cada parte y cada fase con su «N de M».
- S7 — «Mis cursos», solo en cursos con partes: «Vas por: 2. Java avanzado (5 de 30)», con la parte de la lección que abre «Continuar».
- S8 — El avance cuenta lecciones completadas, sin los retos, como el del curso.

## Tareas

T1 | Leer y validar `partes` (S2) | hecho: L5 (salvo las dos reglas pendientes del kit).
T2 | `parts` en el manifiesto, carga y comparación (S3) | pendiente.
T3 | Partes en la API del temario (S4) | pendiente.
T4 | Índice, menú y «Mis cursos» (S1, S5 a S8) | pendiente.
T5 | Pruebas de navegador: curso con partes y curso sin partes (S1, S5 a S7) | pendiente.
T6 | Documentación, `CHANGELOG.md` y nota de la 1.1.0. Etiqueta y *release*, solo con la orden del autor | pendiente.

## Registro

- L1 — Petición del autor (23 de noviembre de 2026), textual: «Empieza a preparar la 1.1.0 con las partes (índice y menú agrupados por parte, con el avance de cada una, como dicen la decisión 15 y el apartado 4 del plan). Enséñame el plan antes de escribir código.»
- L2 — Decisiones del autor sobre el plan: partes plegables en el menú, con solo la actual abierta; avance por fase en el índice y en el menú; «Mis cursos» con una línea de la parte actual. Regla: «las partes solo cambian los cursos que las tienen; el avance por fase sale en todos».
- L3 — Aprobación: «Sí al plan H12 […] en T1, las reglas de validación de las partes tienen que ser exactamente las de FORMATO.md 3.2 y las de formato_academia.py del kit, con los mismos casos; si ves alguna diferencia, avísame antes de seguir.»
- L4 — Diferencias encontradas y decididas: el plan pedía identificadores `<curso>.parte.…` y partes no vacías; el kit solo exige `<curso>.` y acepta una parte sin fases y partes en cualquier orden. Decisión del autor: el kit hará errores de la parte vacía y del orden (tramos seguidos), y la academia los copiará después; T1 sigue con el resto de reglas.
- L5 — T1 hecha (23 de noviembre de 2026). `CourseDescriptor` lee `partes` en registros `Part(id, title, phases)` y las valida como `comprobar_partes` del kit. Cualquier error es un hallazgo: salida 3, sin candidata. Un campo desconocido es un aviso en `reported`. El identificador de una parte pasa por la comprobación de identificadores que ya había (prefijo del curso, sin repetir con fases, módulos, ejercicios, etapas ni otras partes). Los títulos con formato se detectan con los mismos 7 patrones de `formato_en_titulo`. Hay 17 pruebas nuevas en `ContentImportCliTest`, una por caso, con el hallazgo exacto; la fase de RED dio 16 fallos esperados. Comprobaciones: `./mvnw -B verify`, salida 0, 815 pruebas sin fallos. Comparación con lo instalado: el curso inicial `v1.0.0` generado con el código nuevo sale idéntico byte a byte a `content/`. **Diferencias con el kit, solo en el mensaje, con el mismo resultado:** los mensajes van en inglés, como los demás del importador. Para un `id` que falta o que se repite se usa el mensaje que ya había para fases y módulos, que no dice quién lo usa ya. `"partes": null` da «partes must be a list», y el kit da «falta el campo»; los dos lo rechazan.
