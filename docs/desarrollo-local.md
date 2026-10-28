# Desarrollo local

Esta guía arranca la base técnica H3, validada y completada. Todavía no permite estudiar ni crear cuentas: H4 no está iniciado y requiere aprobación del propietario. Los resultados y pendientes viven en el [archivo único de tareas](../odd/tasks/h3-base.md), no en esta guía.

## Requisitos

- Java 25; Spring Boot está fijado en 4.1.2. El Maven Wrapper incluido descarga Maven 3.10.0: no necesitás Maven global.
- Docker con Compose y el motor activo. La imagen de base de datos es `postgres:18.6`.
- fnm con Node 24.22.1 y npm 11.19.1. La raíz fija Node en `.node-version`; con la integración `fnm --use-on-cd`, entrar en `frontend/` usa esa versión. Si hace falta, ejecutá allí `fnm use 24.22.1`.
- Puertos locales libres: 5173 (frontend), 8080 (backend) y 5433 (PostgreSQL).

## Preparar la configuración privada una sola vez

La plantilla `.env.example` está versionada. Solo para una configuración nueva, copiala vos a `.env` desde la raíz, sin sobrescribir un archivo existente:

```sh
cp -n .env.example .env
```

**No arranques con la contraseña vacía.** Completala en privado con una contraseña larga y alfanumérica, sin comillas, compatible con Compose y el lector de propiedades. No la pegues en comandos, registros, chats ni documentación; ningún agente debe leerla o escribirla.

`.env` está ignorado por Git y no se versiona. `.env.example` es la plantilla compartida; nunca guardes contraseñas reales en ella. Si ya tenés `.env` configurado, conservá sus valores.

Compose consume `.env` desde la raíz. El backend con perfil `local`, ejecutado desde `backend/`, importa obligatoriamente `../.env`, sin exportar variables a mano. Si falta el archivo o `DB_PASSWORD` está ausente o vacío, el arranque debe fallar explícitamente. Las pruebas de Testcontainers no activan ese perfil ni necesitan ese archivo.

**Después de inicializar el volumen, no cambies `DB_NAME`, `DB_USERNAME` ni `DB_PASSWORD`.** PostgreSQL conserva la configuración inicial: editar `.env` no cambia las credenciales almacenadas. No soluciones un error de autenticación borrando el volumen. Un cambio requiere mantenimiento y recuperación coordinados, fuera de H3.

## Arrancar en tres terminales

Cada terminal empieza en la raíz del proyecto. Esperá a que la base esté sana antes de iniciar el backend.

**1. PostgreSQL — desde la raíz**

```sh
docker compose up -d --wait db
```

Compose usa el proyecto `code-academy` y su volumen persistente `code-academy_postgres_data`, separado de las prácticas del curso. PostgreSQL queda en `127.0.0.1:5433` (puerto interno 5432).

**2. Backend — desde `backend/`**

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

La salud mínima en http://127.0.0.1:8080/actuator/health debe responder `{"status":"UP"}` antes de continuar.

**3. Frontend — desde `frontend/`**

```sh
cd frontend
fnm use 24.22.1
npm ci
npm run dev
```

Abrí http://127.0.0.1:5173. Vite fija loopback y puerto estricto: si está ocupado, falla en vez de elegir otro. El proxy conserva el prefijo `/api` y también reenvía `/actuator/health` al backend. No hace falta abrir CORS; el proxy no sustituye autorización ni CSRF. El frontend no carga el `.env` de la raíz ni recibe sus secretos.

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

Para las pruebas de navegador, mantené la base y el backend sanos. Playwright puede iniciar Vite o reutilizar el servidor de `127.0.0.1:5173`. Desde `frontend/`:

```sh
npx --no-install playwright install chromium
npm run test:e2e
```

Si Chromium necesita bibliotecas del sistema, **detenete y pedí aprobación explícita**. No uses `--with-deps`, instalaciones globales ni paquetes del sistema como arreglo automático.

## Parar sin perder datos

Usá `Ctrl+C` en las terminales del frontend y backend. Después, desde la raíz:

```sh
docker compose stop db
```

Los servicios quedaron detenidos así al cerrar las comprobaciones de H3, con el volumen conservado. Nunca uses `down -v` ni limpiezas `prune` como parte de este flujo. No hay despliegue de producción en H3; se abordará en H7.
