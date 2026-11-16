# Desarrollo local

Esta guía arranca la academia en local: cuentas, los cursos instalados en `content/` y el progreso de cada usuario. Cómo se instalan los cursos está en [instalar y actualizar cursos](actualizacion-contenido.md). Estado: H7 (16 de noviembre de 2026).

## Requisitos

- Java 25; Spring Boot está fijado en 4.1.2. El Maven Wrapper incluido descarga Maven 3.10.0: no necesitas Maven global.
- Docker con Compose y el motor activo. La imagen de base de datos es `postgres:18.6`.
- fnm con Node 24.22.1 y npm 11.19.1. La raíz fija Node en `.node-version`; con la integración `fnm --use-on-cd`, entrar en `frontend/` usa esa versión. Si hace falta, ejecuta allí `fnm use 24.22.1`.
- Puertos locales libres: 5173 (frontend), 8080 (backend) y 5433 (PostgreSQL).

## Preparar la configuración privada una sola vez

La plantilla `.env.example` está versionada. Solo para una configuración nueva, cópiala tú a `.env` desde la raíz, sin sobrescribir un archivo existente:

```sh
cp -n .env.example .env
```

**No arranques con la contraseña vacía.** Complétala en privado con una contraseña larga y alfanumérica, sin comillas, compatible con Compose y el lector de propiedades. No la pegues en comandos, registros, chats ni documentación; ningún agente debe leerla o escribirla.

`.env` está ignorado por Git y no se versiona. `.env.example` es la plantilla compartida; nunca guardes contraseñas reales en ella. Si ya tienes `.env` configurado, conserva sus valores.

Compose consume `.env` desde la raíz. El backend con perfil `local`, ejecutado desde `backend/`, importa obligatoriamente `../.env`, sin exportar variables a mano. Si falta el archivo o `DB_PASSWORD` está ausente o vacío, el arranque debe fallar explícitamente. Las pruebas de Testcontainers no activan ese perfil ni necesitan ese archivo.

**Después de inicializar el volumen, no cambies `DB_NAME`, `DB_USERNAME` ni `DB_PASSWORD`.** PostgreSQL conserva la configuración inicial: editar `.env` no cambia las credenciales almacenadas. No soluciones un error de autenticación borrando el volumen. Un cambio requiere mantenimiento y recuperación coordinados.

## Arrancar los servicios

Necesitas dos terminales abiertas: una para el backend y otra para el frontend. PostgreSQL funciona en segundo plano, así que puedes reutilizar su terminal para arrancar el backend.

Los comandos de cada paso parten de la raíz del proyecto. Si abres otra terminal, sitúate primero en esa raíz. Espera a que la base esté sana antes de iniciar el backend.

**1. PostgreSQL — desde la raíz**

```sh
docker compose up -d --wait db
```

Cuando el comando termina correctamente y vuelve a aparecer el prompt, PostgreSQL queda funcionando en segundo plano (`-d`). No necesitas mantener esta terminal abierta: puedes usarla para el paso 2 o abrir otra.

Compose usa el proyecto `myopenacademy` y su volumen persistente `myopenacademy_postgres_data`, separado de las prácticas del curso y del Code Academy original (proyecto `code-academy`), que no se usa nunca. PostgreSQL queda en `127.0.0.1:5433` (puerto interno 5432).

**2. Backend — desde `backend/`**

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Deja esta terminal abierta y el comando ejecutándose: el backend funciona en primer plano. Para el paso 3, abre otra terminal en la raíz del proyecto.

La salud mínima en http://127.0.0.1:8080/actuator/health debe responder `{"status":"UP"}` antes de continuar.

Las migraciones crean las tablas en el esquema `code_academy`, y el historial de Flyway (`flyway_schema_history`) queda siempre en `public` (`spring.flyway.default-schema=public`). Así el backend arranca igual las veces que haga falta, aunque el usuario de la base se llame `code_academy`, y las bases ya migradas conservan su historial sin repetir ninguna migración.

**Cursos.** `content/` no está en Git: hay que instalar los cursos con `content-import` y `content-update` ([cómo](actualizacion-contenido.md#recorrido-habitual)); la lista de los instalados está en `cursos-instalados.json`. Con `content/` vacío el backend no arranca.

Al arrancar, el backend carga y valida el contenido de `code-academy.content-dir` (por defecto `../content`, relativo al directorio desde el que se ejecuta el backend, o sea `backend/`) y sincroniza el catálogo de la base en una sola transacción: añade cursos, lecciones y ejercicios nuevos y actualiza títulos, posiciones y disponibilidad, sin borrar filas. Si el contenido no es válido, o si un ID ya guardado desapareció del manifiesto o cambió de curso o de lección, el servidor no arranca y el mensaje indica el problema; la base queda sin cambios. Para usar otra copia del contenido, pasa `--code-academy.content-dir=<ruta>` al arrancar. La recuperación de contraseña por consola no carga el contenido, así que funciona aunque esté roto.

**3. Frontend — desde `frontend/`**

```sh
cd frontend
fnm use 24.22.1
npm ci
npm run dev
```

Deja también esta terminal abierta y el comando ejecutándose mientras usas la academia.

Abre http://127.0.0.1:5173. Vite fija loopback y puerto estricto: si está ocupado, falla en vez de elegir otro. El proxy conserva el prefijo `/api` y también reenvía `/actuator/health` al backend. No hace falta abrir CORS; el proxy no sustituye autorización ni CSRF. El frontend no carga el `.env` de la raíz ni recibe sus secretos.

La primera vez, crea una cuenta en la pantalla de registro y después inicia sesión.

## Ejecutar pruebas

**Desde `backend/`**, con Docker activo:

```sh
./mvnw -B verify
```

Testcontainers crea PostgreSQL desechable y aislado; no usa el volumen de desarrollo.

**Desde `frontend/`**:

```sh
npm run test -- --run
npm run lint
npm run build
```

Para las pruebas de navegador, mantén la base y el backend sanos. Playwright puede iniciar Vite o reutilizar el servidor de `127.0.0.1:5173`. Desde `frontend/`:

```sh
npx --no-install playwright install chromium
npm run test:e2e
```

Si Chromium necesita bibliotecas del sistema, **detente y pide aprobación explícita**. No uses `--with-deps`, instalaciones globales ni paquetes del sistema como arreglo automático.

**Recorrido de dos usuarios.** No necesita la base ni el backend de las tres terminales, ni toca `.env` ni el volumen `myopenacademy_postgres_data`. Requiere Docker activo con la imagen `postgres:18.6` ya descargada y el jar del backend construido (`./mvnw -B package -DskipTests` desde `backend/`). Desde `frontend/`:

```sh
npm run test:e2e:journey
```

El script crea un contenedor `code-academy-e2e-<aleatorio>` con datos en memoria y una contraseña generada que no se muestra, instala los cursos de ejemplo del kit (`cursos-ejemplo/`) por su etiqueta con el importador real, arranca el jar con el perfil por defecto y Vite apuntando a ese backend (variable `CODE_ACADEMY_BACKEND_URL`, que por defecto sigue siendo `http://127.0.0.1:8080`), todo en puertos libres de `127.0.0.1`. Al terminar, también ante un fallo o Ctrl+C, detiene el backend y el contenedor, que se borra solo. Los resultados quedan en `frontend/test-results/journey/<fecha>/`: capturas, registros del backend y `summary.json` con tiempos y el reinicio.

## Recuperar una contraseña

No hay recuperación por la web ni cuenta de administración: se cambia desde una consola local. Con la base sana, compila el jar desde `backend/` (o reutiliza uno ya construido):

```sh
cd backend
./mvnw -B package -DskipTests
```

Después, **en una terminal real** y desde `backend/`, para que el perfil `local` encuentre `../.env`:

```sh
java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --reset-password=<usuario>
```

El usuario no distingue mayúsculas. La herramienta pide la contraseña nueva dos veces sin mostrarla; nunca la pases como argumento. Debe tener entre 15 y 128 caracteres y se guarda tal cual, con sus espacios. Si las dos no coinciden, la contraseña no cumple la regla o el usuario no existe, no cambia nada y termina con error.

Si no hay terminal interactiva (entrada redirigida, scripts o `./mvnw spring-boot:run`, que arranca Java en otro proceso sin terminal propia), se niega antes de arrancar: no pregunta ni cambia nada. Por eso no funciona con `./mvnw spring-boot:run`. Este modo no levanta el servidor web, así que puede ejecutarse con el backend en marcha.

El hash nuevo y la versión de credenciales se guardan juntos en una sola operación, conservando la cuenta y su progreso. Las sesiones ya abiertas de ese usuario se cierran en su siguiente solicitud y hay que volver a iniciar sesión.

## Parar sin perder datos

Usa `Ctrl+C` en las terminales del frontend y backend. Después, desde la raíz:

```sh
docker compose stop db
```

El volumen se conserva: cuentas y progreso siguen ahí al volver a arrancar. Nunca uses `down -v` ni limpiezas `prune` como parte de este flujo. No hay despliegue de producción; se aborda en H10 (instalación con un comando, variables de entorno y red local).
