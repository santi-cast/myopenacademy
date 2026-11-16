# Instalar MyOpenAcademy

Esta guía instala la academia en un equipo con **Docker** y la deja lista para usarla en ese equipo o en la red de casa. Todo se configura en un fichero, `.env`; nada está escrito en el código. Estado: H10 (16 de noviembre de 2026).

> **Sin HTTPS, las contraseñas viajan sin cifrar por la red.** Úsala en este equipo o en una red de confianza, como la de casa. No la publiques en internet así.

## 1. Lo que hace falta

- Docker con Compose, con el motor en marcha. Nada más: ni Java ni Node en el equipo.
- Los repositorios de los cursos que quieras instalar, en una carpeta (por defecto `../Cursos`, al lado de la academia: `../Cursos/curso-inicial`…; `CURSOS_DIR` en `.env`).

## 2. Configurar

Desde la carpeta de la academia, copia la plantilla sin pisar una configuración que ya tengas:

```sh
cp -n .env.example .env
```

Abre `.env` y pon una **contraseña larga para la base** en `DB_PASSWORD` (sin comillas; no la compartas). Lo demás tiene valores pensados para usarla solo en este equipo; cada línea de `.env.example` explica la suya. Las más útiles:

| Variable | Para qué |
|---|---|
| `APP_PORT` | El puerto en el que se abre la academia (8080). |
| `APP_BIND` | Quién puede entrar: `127.0.0.1`, solo este equipo; `0.0.0.0`, también la red de casa. |
| `PUBLIC_URL` | La dirección con la que se entra, por ejemplo `http://192.168.1.50:8080`. |
| `CONTENT_HOST_DIR` | La carpeta con los cursos instalados (`./content`). |
| `DB_PORT` | El puerto de la base en este equipo (5434, que no choca con el 5433 del Code Academy original). |
| `CURSOS_DIR` | La carpeta con los repositorios de los cursos (`../Cursos`). |
| `HOST_UID`, `HOST_GID` | Tu usuario y tu grupo (en Linux, `id -u` e `id -g`), para que lo que instalan las herramientas sea tuyo. |

No cambies `DB_NAME`, `DB_USERNAME` ni `DB_PASSWORD` después de crear la base: PostgreSQL se queda con los primeros.

## 3. Arrancar (un solo comando)

```sh
docker compose up -d --build
```

La primera vez construye la aplicación (unos minutos). Después levanta la base y la academia, que sirve las pantallas y la API en un solo puerto. Abre `http://127.0.0.1:8080` (o tu `PUBLIC_URL`). Al arrancar, el registro de la aplicación (`docker compose logs app`) dice dónde está y avisa si se abre a la red sin HTTPS.

La primera vez no hay ningún curso: la academia arranca igual, avisa en su registro y el catálogo dice que no hay cursos y quién los instala (apartado 5).

## 4. Crear el administrador

No hay ningún usuario ni contraseña por defecto. Crea el administrador desde una terminal:

```sh
docker compose run --rm app --create-admin=TuUsuario
```

Pide la contraseña dos veces, sin mostrarla. Si el usuario ya existía, pasa a ser administrador y conserva su contraseña. El administrador ve «Administración»: registro abierto o cerrado, nombre de la academia, usuarios y cursos instalados.

**Si la academia se va a publicar en internet, cierra el registro** en «Administración» y crea tú las cuentas (decisión del autor). En casa puede quedarse abierto.

## 5. Instalar y actualizar cursos

Los cursos se instalan por consola, nunca desde la web, con las herramientas de la academia, que van en su imagen: no hace falta Java en el equipo. Se ejecutan con `docker compose run --rm herramientas …`, que ve la carpeta de la academia (con `content/`) y, en `/cursos`, la de los repositorios de los cursos. El recorrido completo y lo que comprueba cada paso está en [instalar y actualizar cursos](actualizacion-contenido.md). En resumen, para instalar o actualizar un curso desde **la etiqueta o el commit que quieras instalar**:

```sh
# 1. Preparar la copia nueva (no toca la que está en uso). --tag vX.Y.Z para una versión publicada, o --commit con el commit completo.
docker compose run --rm herramientas content-import generate --active content --source /cursos/curso-inicial \
    --commit <commit completo> --output trabajo/candidata --report trabajo/generacion.json

# 2. Compararla con la que está en uso y revisar el informe (trabajo/comparacion.json).
docker compose run --rm herramientas content-update compare --active content --candidate trabajo/candidata \
    --source /cursos/curso-inicial --report trabajo/comparacion.json

# 3. Parar la aplicación ANTES de aplicar: la instalación sustituye la carpeta content/ entera.
docker compose stop app

# 4. Aplicar, anotar la lista de cursos instalados y volver a arrancar.
docker compose run --rm herramientas content-update apply --active content --candidate trabajo/candidata \
    --source /cursos/curso-inicial --report trabajo/comparacion.json
docker compose run --rm herramientas content-courses list --active content --output cursos-instalados.json
docker compose start app
```

La copia anterior queda en `content.previous` por si hay que volver atrás; archívala o bórrala tú antes de la siguiente instalación (no se borra sola). `trabajo/`, `content.previous/` y `content.staged/` no van a Git.

## 6. En la red de casa

1. En `.env`, `APP_BIND=0.0.0.0` y, en `PUBLIC_URL`, la dirección de este equipo en la red: su IP (`http://192.168.1.50:8080`) o su nombre en la red local (`http://mi-equipo.local:8080`, si el equipo lo anuncia con mDNS, como hacen macOS y casi todos los Linux de escritorio).
2. Para que la IP no cambie, resérvala en el router (casi todos lo permiten en su apartado DHCP: «reserva» o «IP fija» para la dirección MAC del equipo).
3. `docker compose up -d` para aplicarlo. Si el equipo tiene cortafuegos, abre el puerto de `APP_PORT` para la red local.
4. Recuerda: sin HTTPS, las contraseñas viajan sin cifrar. En la red de casa, con gente de confianza, vale; fuera, no.

## 7. Contraseñas olvidadas

- **Un alumno:** el administrador le pone una contraseña temporal en «Administración»; al entrar con ella tiene que elegir una nueva, que solo sabrá él o ella.
- **El administrador** (o si no hay otro): desde una terminal, `docker compose run --rm app --reset-password=Usuario`.

## 8. Parar sin perder datos

```sh
docker compose stop
```

Los datos (cuentas y progreso) quedan en el volumen de la base y los cursos, en `content/`. **Nunca** uses `docker compose down -v` ni limpiezas `prune`: borrarían las cuentas y el progreso.
