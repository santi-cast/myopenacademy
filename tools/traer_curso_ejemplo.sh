#!/bin/sh
# Trae a cursos-ejemplo/ el curso de ejemplo del kit desde un commit fijo, solo con Git de lectura.
#
# Uso, desde la raíz de la academia:
#     sh tools/traer_curso_ejemplo.sh COMMIT
#
# El curso de ejemplo vive en el kit (herramientas/pruebas/cursos/bueno/); la academia guarda
# una copia para sus pruebas, porque no puede leer el kit al ejecutarlas. Para renovarla, se
# vuelve a ejecutar con el commit nuevo del kit. La ruta del kit se puede cambiar con KIT_CURSOS.
set -eu

if [ "$#" -ne 1 ]; then
    echo "uso: sh tools/traer_curso_ejemplo.sh COMMIT" >&2
    exit 2
fi
kit=${KIT_CURSOS:-../Cursos/kit-cursos}
origen=herramientas/pruebas/cursos/bueno
destino=cursos-ejemplo/ejemplo

commit=$(git -C "$kit" rev-parse --verify "$1^{commit}")
git -C "$kit" cat-file -e "$commit:$origen" || { echo "el commit $commit no tiene $origen" >&2; exit 1; }

rm -rf "$destino"
mkdir -p "$destino"
# Solo lectura del kit: el árbol de ese commit, sin tocar su carpeta de trabajo.
git -C "$kit" archive "$commit" "$origen" | tar -x -C "$destino" --strip-components=4
printf '%s\n' "$commit" > cursos-ejemplo/COMMIT-KIT
echo "Curso de ejemplo traído del kit en $commit a $destino"
