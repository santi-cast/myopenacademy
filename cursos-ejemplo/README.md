# Cursos de ejemplo

Copia de los cursos de ejemplo del kit (`herramientas/pruebas/cursos/` de `kit-cursos`), para las pruebas de la academia. Son los **únicos** cursos de ejemplo de MyOpenAcademy: viven en el kit, que los comprueba con `formato_academia.py`, y aquí solo se guarda una copia de un commit fijo, porque la academia no puede leer el kit al ejecutar sus pruebas (`FORMATO.md`, apartado 14).

- `bueno/`: el curso de ejemplo (id `ejemplo`): módulo disponible y pendiente, ejercicios con pistas, preguntas de repaso, Mermaid, imagen, código, retos, proyecto por etapas, partes y enlaces a `ejemplo-destino`.
- `ejemplo-destino/`: el curso al que enlaza (id `ejemplo-destino`, repositorio propio para no chocar con el curso inicial real), con un módulo que es su proyecto y su `glosario.md`. Va al lado de `bueno/` y su carpeta se llama como su repositorio.
- `COMMIT-KIT`: el commit del kit del que salen las copias.

**No se editan a mano.** Si las pruebas necesitan algo que no tienen, se pide a la sesión del kit y, cuando esté, se renueva la copia con el commit nuevo, desde la raíz de la academia:

```sh
sh tools/traer_curso_ejemplo.sh <commit-del-kit>
```

El script solo usa comandos de Git de lectura sobre `../Cursos/kit-cursos/` (otra ruta, con la variable `KIT_CURSOS`).
