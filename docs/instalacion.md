# Instalar MyOpenAcademy

Esta guía instala la academia en un equipo con **Docker** y la deja lista para usarla en ese equipo o en la red de casa. Todo se configura en un fichero, `.env`; nada está escrito en el código. Estado: H10 (16 de noviembre de 2026).

> **Sin HTTPS, las contraseñas viajan sin cifrar por la red.** Úsala en este equipo o en una red de confianza, como la de casa. No la publiques en internet así.

## 1. Lo que hace falta

- Docker con Compose, con el motor en marcha.
- Para instalar cursos, de momento, **Java 25** en el equipo (las herramientas de instalación se ejecutan fuera de los contenedores; ver el apartado 5).
- Los repositorios de los cursos al lado de la academia (`../Cursos/curso-inicial`…).

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

No cambies `DB_NAME`, `DB_USERNAME` ni `DB_PASSWORD` después de crear la base: PostgreSQL se queda con los primeros.

## 3. Arrancar (un solo comando)

```sh
docker compose up -d --build
```

La primera vez construye la aplicación (unos minutos). Después levanta la base y la academia, que sirve las pantallas y la API en un solo puerto. Abre `http://127.0.0.1:8080` (o tu `PUBLIC_URL`). Al arrancar, el registro de la aplicación (`docker compose logs app`) dice dónde está y avisa si se abre a la red sin HTTPS.

**La academia no arranca sin cursos instalados.** Instala al menos uno (apartado 5) antes de abrirla.

## 4. Crear el administrador

No hay ningún usuario ni contraseña por defecto. Crea el administrador desde una terminal:

```sh
docker compose run --rm app --create-admin=TuUsuario
```

Pide la contraseña dos veces, sin mostrarla. Si el usuario ya existía, pasa a ser administrador y conserva su contraseña. El administrador ve «Administración»: registro abierto o cerrado, nombre de la academia, usuarios y cursos instalados.

**Si la academia se va a publicar en internet, cierra el registro** en «Administración» y crea tú las cuentas (decisión del autor). En casa puede quedarse abierto.

## 5. Instalar y actualizar cursos

Los cursos se instalan por consola, nunca desde la web, con las herramientas del backend. De momento necesitan Java 25 y el JAR construido en este equipo (`cd backend && ./mvnw -DskipTests package`). El recorrido completo está en [instalar y actualizar cursos](actualizacion-contenido.md); en resumen, con la academia **parada** (`docker compose stop app`):

```sh
sh backend/content-import generate --active content --source ../Cursos/curso-inicial --tag v1.0.0 \
    --output ../trabajo/candidata --report ../trabajo/generacion.json
sh backend/content-update compare --active content --candidate ../trabajo/candidata \
    --source ../Cursos/curso-inicial --report ../trabajo/comparacion.json
sh backend/content-update apply --active content --candidate ../trabajo/candidata \
    --source ../Cursos/curso-inicial --report ../trabajo/comparacion.json
sh backend/content-courses list --active content --output cursos-instalados.json
docker compose start app
```

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
