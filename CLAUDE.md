# Academia de MyOpenAcademy

Se suma a `../CLAUDE.md` (reglas comunes). Las de `../Cursos/CLAUDE.md` no se aplican aquí.

## Antes de hacer nada en cada sesión

1. Lee `../README.md`, `../PLAN.md` (sobre todo los apartados 1, 2, 7 y 8.5) y `../ESTADO.md`.
2. Lee `ESTADO.md` de esta carpeta y, después, `PLAN.md` (el plan heredado de Code Academy, que hay que poner al día) y `README.md`.
3. Lee el contrato `../Cursos/kit-cursos/FORMATO.md`.
4. Lee, solo para saber cómo van, `../Cursos/kit-cursos/ESTADO.md` y `../Cursos/curso-inicial/ESTADO.md`, y revisa la sección de dependencias de tu `ESTADO.md`: dile al autor si algo te bloquea o si otra sesión necesita algo de esta.
5. Resume al autor en pocas líneas dónde se quedó el trabajo y qué toca a continuación.

## Qué es

La plataforma de **MyOpenAcademy**, la academia de programación libre, gratuita y de código abierto que funciona como un juego con DLC: **esta academia es el juego** y cada curso es una expansión que se instala con su versión. El **pack base 1.0** es la academia más el DLC del curso inicial. En GitHub irá en la cuenta del autor, `santi-cast/myopenacademy` (su portfolio).

Se creó el 8 de noviembre de 2026 **clonando Code Academy con su historial** (hitos H1 a H5 hechos). El original, `../../Code Academy/`, **no se toca** (ni sus ficheros ni su memoria de engram).

## Reglas de este proyecto

- **⚠️ Antes de arrancar nada con Docker:** el `compose.yaml` heredado se llama `code-academy` y publica el puerto `5433`, igual que el original. Arrancarlo tal cual **usaría los contenedores y el volumen de datos del Code Academy original**. Primero se cambia el nombre del proyecto de Compose (y, si hace falta, el puerto) y se crea un `.env` propio. Nunca se leen ni se usan el `.env` ni la base de datos del original.
- **Manda la academia, pero el contrato vive en el kit.** `FORMATO.md` recoge lo que la academia necesita; si la academia necesita cambiarlo, se propone al autor y lo aplica la sesión del kit (`../Cursos/kit-cursos/`). Esta sesión no lo edita.
- **Ningún curso escrito en el código:** el catálogo, las recomendaciones y los roadmaps salen del contenido instalado (`curso.json` de cada curso).
- **La identidad la guarda el curso** (`curso.json`): el importador deja de inventar identificadores y los lee del curso. Ver «Decisiones del paso 0» en `../ESTADO.md`.
- **Diseñada como si fuera a desplegarse en internet**, aunque se use en local y en la red de casa: todo lo que cambie entre niveles, en variables de entorno.
- **Aspecto:** todo sale de `frontend/src/styles/tokens.css` y el test que lo comprueba no se desactiva. El nombre y el logo, en un solo sitio.
- **Se mantiene la forma de trabajar de Code Academy** (hitos, tareas en `odd/tasks/`, pruebas, ramas por hito, revisión del autor antes de integrar), salvo que el autor diga otra cosa.
- **Documentación en español de España, tuteando.** El README heredado usa voseo («abrí»): se corrige al tocarlo.
- **Nada de rutas absolutas.** Las heredadas (en `PLAN.md` y en `odd/tasks/`) se limpian antes de publicar (`../PLAN.md`, apartado 8.6).
- Commit cuando el autor lo pida o el plan lo diga. Sin remotos hasta publicar.

## Coordinación con las sesiones del kit y del curso inicial

Se trabaja a la vez que `../Cursos/kit-cursos/` y `../Cursos/curso-inicial/`. El autor es quien pasa los mensajes de una a otra.

- **Avísale al autor siempre que cambie algo que afecta a otra sesión**: cuando necesites un cambio en `FORMATO.md`, cuando el curso tenga que cambiar algo para poder importarse o cuando termines algo que otra espera. Hazlo **al final de tu respuesta**, en un bloque aparte que empiece por «📣 **Para la sesión del kit:**» o «📣 **Para la sesión del curso inicial:**», con el mensaje **listo para copiar y pegar**.
- Antes de avisar, **actualiza la tabla de dependencias de tu `ESTADO.md`**.
- No toques nunca el repositorio de otra sesión: solo lees su `ESTADO.md` y, para las pruebas de importación, su contenido.
