#!/bin/sh
# Trae a cursos-ejemplo/ los cursos de ejemplo del kit desde un commit fijo, solo con Git de lectura.
#
# Uso, desde la raíz de la academia:
#     sh tools/traer_curso_ejemplo.sh COMMIT
#
# Los cursos de ejemplo viven en el kit (herramientas/pruebas/cursos/), que los comprueba; la academia
# guarda una copia para sus pruebas, porque no puede leer el kit al ejecutarlas (FORMATO.md 14). Son
# dos y van uno al lado del otro: «bueno» enlaza a «ejemplo-destino», cuya carpeta se llama como su
# repositorio. Para renovarlos, se vuelve a ejecutar con el commit nuevo del kit. La ruta del kit se
# puede cambiar con la variable KIT_CURSOS.
set -eu

if [ "$#" -ne 1 ]; then
    echo "uso: sh tools/traer_curso_ejemplo.sh COMMIT" >&2
    exit 2
fi
kit=${KIT_CURSOS:-../Cursos/kit-cursos}
origen=herramientas/pruebas/cursos
cursos="bueno ejemplo-destino"

commit=$(git -C "$kit" rev-parse --verify "$1^{commit}")
for curso in $cursos; do
    git -C "$kit" cat-file -e "$commit:$origen/$curso" || { echo "el commit $commit no tiene $origen/$curso" >&2; exit 1; }
done

for curso in $cursos; do
    rm -rf "cursos-ejemplo/$curso"
    mkdir -p "cursos-ejemplo/$curso"
    # Solo lectura del kit: el árbol de ese commit, sin tocar su carpeta de trabajo.
    git -C "$kit" archive "$commit" "$origen/$curso" | tar -x -C "cursos-ejemplo/$curso" --strip-components=4
done
printf '%s\n' "$commit" > cursos-ejemplo/COMMIT-KIT
echo "Cursos de ejemplo traídos del kit en $commit: $cursos"
