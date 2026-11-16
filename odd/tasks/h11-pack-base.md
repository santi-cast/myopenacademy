# H11 — Pack base 1.0

Objetivo: dejar la academia lista para publicarse en GitHub como pack base 1.0 (la academia más el DLC del curso inicial). Alcance: `PLAN.md`, apartado «H11. Pack base 1.0», y `../PLAN.md` 8.6. Lista de tareas **aprobada por el autor** el 16 de noviembre de 2026. Rama `feat/h11-pack-base`, desde `main` `a3ef84e`. **Ni `push` ni remoto ni release sin la autorización expresa del autor.**

## Decisiones del autor (16 de noviembre de 2026)

- `git-filter-repo`, instalado con `pipx`, solo para su usuario.
- Copia de seguridad fuera de `Proyecto Academia/`, en `../../copias-myopenacademy/` (la carpeta `../copias/` caería dentro del repositorio de la raíz sin ignorar). El `.tar.gz` lleva el `.env`: se queda en este equipo y nunca se sube.
- Avisar al autor antes de sustituir el repositorio por el reescrito.
- README en español, con un párrafo corto en inglés al principio, para el portfolio.
- Todos los commits llevan ya el correo `noreply` de GitHub del autor: no hay que reescribir autores.

## Tareas

T1 | Copia de seguridad (espejo de Git y `.tar.gz` de la carpeta, con sumas, comprobada) | hecho: L1.
T2 | Historial sin `content/java` (`git filter-repo` sobre un clon; comprobar; sustituir solo con el visto bueno del autor) | pendiente.
T3 | `gitleaks` sobre el historial reescrito y la carpeta de trabajo | pendiente.
T4 | Rutas absolutas, textos desactualizados y voseo de la documentación | pendiente.
T5 | `LICENSE` (MIT), licencias de las dependencias y vulnerabilidades conocidas (`npm audit` y Maven) | pendiente.
T6 | `CONTRIBUTING.md` y `CHANGELOG.md` | pendiente.
T7 | README del portfolio (español, con un párrafo en inglés al principio) | pendiente.
T8 | El curso inicial completo, desde su etiqueta `v1.0.0` (espera a que su sesión lo complete y lo etiquete) | pendiente.
T9 | Etiqueta `v1.0.0` de la academia y nota de la release; primer `push`, remoto y release solo con la autorización del autor | pendiente.

## Registro

L1. 16 de noviembre de 2026, T1. Copias en `../../copias-myopenacademy/` (fuera de `Proyecto Academia/` y de cualquier repositorio; carpeta con permisos `700`), del estado `a3ef84e` (152 commits): `academia-2026-11-16-a3ef84e.git` (`git clone --mirror`, 50 MB) y `academia-2026-11-16-a3ef84e.tar.gz` (la carpeta entera, con `.git`, `.env` y lo regenerable; 273 MB; permisos `600`), con `SHA256SUMS`. Comprobado: la suma del `.tar.gz` cuadra; el espejo se clona con los 152 commits y deja el mismo árbol de `main`; `git fsck` termina con 0 (solo objetos sueltos, «actualizando» en la traducción al español de *dangling*, igual que en el original); el `.tar.gz` lleva `.env`, `.git` y el código.
