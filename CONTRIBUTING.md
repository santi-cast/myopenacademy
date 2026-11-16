# Cómo contribuir a MyOpenAcademy

Gracias por querer mejorar la academia. Toda ayuda cuenta: avisar de un fallo, mejorar un texto de la interfaz, la documentación o el código.

Este repositorio es **la plataforma**. Los cursos viven cada uno en su repositorio: si encuentras una errata en una lección, usa el enlace «Mejorar esta lección» de la propia lección, que lleva al fichero en el repositorio del curso.

## Antes de empezar

- Escribe en **español de España**, tuteando: la interfaz, los mensajes y la documentación.
- Prepara el entorno con [`docs/desarrollo-local.md`](docs/desarrollo-local.md). Para instalar la academia sin desarrollar, [`docs/instalacion.md`](docs/instalacion.md).
- La academia instala cursos que cumplen el **formato de los cursos** (`FORMATO.md`, en el kit de cursos, que estará en `github.com/myopenacademy/kit-cursos`). El formato se cambia en el repositorio del kit, no aquí.

## Cómo proponer un cambio

1. **Fallos y mejoras pequeñas:** abre una *issue* o directamente una *pull request*.
2. **Cambios grandes** (una pantalla nueva, cambiar la API o la base de datos, otra forma de instalar cursos): abre antes una *issue* para hablarlo.
3. En la *pull request*, explica qué cambias y por qué, y cómo lo has comprobado.

**Fallos de seguridad:** no abras una *issue* pública. Avisa en privado con «Report a vulnerability», en la pestaña *Security* del repositorio.

## Lo que se comprueba

Todo en verde antes de proponer el cambio:

```sh
# desde backend/ (necesita Docker: las pruebas usan una base desechable)
./mvnw -B verify

# desde frontend/
npm run test -- --run
npm run lint
npm run build
npm run test:e2e      # navegador, con su propia base, backend y cursos de ejemplo
```

Un cambio nuevo llega con sus pruebas.

## Reglas que no se pueden saltar

- **Ningún curso escrito en el código.** El catálogo, los títulos, el orden y los enlaces salen de los cursos instalados (`curso.json`). Las pruebas usan los cursos de ejemplo del kit (`cursos-ejemplo/`), nunca un curso real.
- **El aspecto sale de `frontend/src/styles/tokens.css`**, y la prueba que lo comprueba no se desactiva. El nombre y el logo están en un solo sitio (`frontend/src/brand/`).
- **Lo que cambia entre instalaciones va en variables de entorno**, nunca en el código ni en rutas absolutas.
- **No cambies los identificadores ni las migraciones ya publicadas:** el progreso de los alumnos va unido a ellos. Un cambio en la base es una migración nueva.
- **Nada de secretos en Git:** el `.env` se queda en tu equipo.

## Licencia de lo que aportes

Al contribuir aceptas que tu aportación se publique con la licencia de la academia, MIT ([`LICENSE`](LICENSE)).
