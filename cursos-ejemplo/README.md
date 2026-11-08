# Curso de ejemplo

Copia del curso de ejemplo del kit (`herramientas/pruebas/cursos/bueno/` de `kit-cursos`), para las pruebas de la academia. Es el **único** curso de ejemplo de MyOpenAcademy: vive en el kit, que lo comprueba con `formato_academia.py`, y aquí solo se guarda una copia, porque la academia no puede leer el kit al ejecutar sus pruebas.

- `ejemplo/`: el curso, tal como está en el commit del kit.
- `COMMIT-KIT`: el commit del kit del que sale la copia.

**No se edita a mano.** Si las pruebas necesitan algo que el curso no tiene, se pide a la sesión del kit y, cuando esté, se renueva la copia con el commit nuevo, desde la raíz de la academia:

```sh
sh tools/traer_curso_ejemplo.sh <commit-del-kit>
```

El script solo usa comandos de Git de lectura sobre `../Cursos/kit-cursos/` (otra ruta, con la variable `KIT_CURSOS`).
