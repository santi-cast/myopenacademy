# Estado de la academia

> Actualízalo al terminar cada tarea. Es la memoria entre sesiones: lo que no esté aquí o en `PLAN.md` no lo sabrá la sesión siguiente.

## Dónde estamos

**8 de noviembre de 2026:** repositorio creado desde la sesión de la raíz **clonando Code Academy con su historial** (82 commits, hitos H1 a H5 cerrados; ver `PLAN.md`) y sin remoto. Añadidos `CLAUDE.md` y este `ESTADO.md`. El código y el resto de la documentación son, de momento, los de Code Academy: el plan heredado (`PLAN.md`) **todavía no está al día** con lo decidido para MyOpenAcademy (`../PLAN.md`, apartado 8.5).

Lo que ya existe en los otros proyectos:

- **Contrato de formato:** `../Cursos/kit-cursos/FORMATO.md`, versión 1 (identidad en `curso.json`, enlaces entre cursos, retos, preguntas de repaso, versiones y equivalencias…).
- **Kit:** `formato_academia.py`, `generar_indice.py`, `generar_chuleta_global.py`, `comprobar_mermaid.py` (Mermaid 12.2.0, la misma biblioteca que usa este frontend) y el verificador de código.
- **Curso inicial:** fase 1 escrita, revisada y **aprobada por el autor** (4 módulos y sus retos), con su `curso.json`.

## Siguiente paso

**Paso 2 del plan común: la prueba de importación** (`../PLAN.md`, apartado 7).

1. **Ponerse al día:** leer el plan común, `FORMATO.md` y lo que tiene esta academia (sobre todo `docs/manifiesto.md`, `docs/actualizacion-contenido.md`, `backend/content-import` y `backend/content-update`).
2. **Preparar un entorno propio**, separado del original. **Hecho en parte (8 de noviembre de 2026):** el proyecto de Compose se llama ya `myopenacademy` (volumen `myopenacademy_postgres_data`; comprobado con `docker compose config`, sin arrancar nada). Falta crear un `.env` propio y decidir el puerto: sigue en `5433`, igual que el original (su contenedor `code-academy-db-1` está parado); `5433` también está escrito en `backend/src/main/resources/application.properties` y en dos pruebas. No arrancar nada antes del `.env` (ver `CLAUDE.md`).
3. **Intentar importar la fase 1 del curso inicial** (`../Cursos/curso-inicial/`, desde un commit fijo, solo leyendo) con el importador actual. Se espera que falle: el importador inventa los identificadores, no lee `curso.json`, solo conoce un curso y no sabe de retos, preguntas de repaso, pistas ni enlaces entre cursos.
4. **Escribir la lista concreta de cambios** que necesita el importador (y el resto de la academia) para instalar cursos según `FORMATO.md`, ordenada y estimada. Si algo del formato no encaja con la academia, avisar al autor para la sesión del kit.
5. Con esa lista, **reescribir con el autor `PLAN.md`** para MyOpenAcademy: los hitos nuevos a partir de H6 hasta el pack base 1.0 (`../PLAN.md`, apartado 8.5).

## Pendientes heredados que no hay que olvidar

- **H6 de Code Academy:** el autor usa la academia para estudiar y anota lo que encuentra. Tiene prioridad sobre funciones nuevas (`../PLAN.md`, apartado 8.5).
- **Antes de publicar** (`../PLAN.md`, apartado 8.6): rutas absolutas en `PLAN.md` y en `odd/tasks/`; README desactualizado y con voseo; `gitleaks` sobre todo el historial; licencias de las dependencias (elkjs, dompurify); el nombre MyOpenAcademy en un solo sitio (hoy «Code Academy» está en `index.html`, `src/layout/Layouts.tsx` y `e2e/stages.spec.ts`).
- **`content/` fuera de Git:** hoy guarda en el repositorio la copia del curso de Java (unos 22.600 recursos). Pasará a instalarse desde cada curso; hará falta un curso de ejemplo pequeño para las pruebas.

## Dependencias con el kit y el curso inicial

| La academia espera de… | Qué | Para | Estado |
|---|---|---|---|
| Curso inicial | Fase 1 escrita, con `curso.json` | La prueba de importación | **Hecho** (aprobada por el autor el 8 de noviembre de 2026) |
| Kit | `FORMATO.md` estable | Saber qué tiene que importar | Versión 1, con ajustes dentro de la versión mientras no haya nada publicado |

| Otros esperan de la academia | Qué | Para | Estado |
|---|---|---|---|
| Kit y curso inicial | La lista de lo que no encaje del formato | Ajustar `FORMATO.md` antes de publicar | Pendiente (sale de la prueba de importación) |
| Curso inicial | Un importador que lea `curso.json` | Instalarse en la academia (pack base 1.0) | Pendiente |

Si algo bloquea, dilo al autor: él pasa la información de una sesión a otra.

## Hecho

| Fecha | Qué |
|---|---|
| 2026-11-08 | Repositorio creado clonando Code Academy con su historial, sin remoto; añadidos `CLAUDE.md` y `ESTADO.md`. |
| 2026-11-08 | Proyecto de Compose renombrado de `code-academy` a `myopenacademy` (y su mención en `docs/desarrollo-local.md`), para no usar nunca los contenedores ni el volumen del original. |
