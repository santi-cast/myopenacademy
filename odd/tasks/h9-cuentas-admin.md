# H9 — Cuentas y administración

Objetivo: que la academia tenga un administrador y un panel, y que cada usuario pueda cambiar su contraseña. Alcance: `PLAN.md`, apartado «H9. Cuentas y administración» (cambios 14, 15 y 16 de la lista de `ESTADO.md`). Rama `feat/h9-cuentas-admin`, desde `main` `9ae8115`. Commits locales; el autor revisa en el navegador antes de integrar. Sin remotos.

Pruebas: `./mvnw -B verify` en `backend/`; `npm run test -- --run`, `npm run lint` y `npm run build` en `frontend/`; el recorrido aislado `npm run test:e2e:journey` (con el JAR recién empaquetado). Siempre con bases desechables.

## Alcance

- A1. **Roles** de administrador y usuario (migración y seguridad por rol). El **administrador se crea por consola** al instalar, como `--reset-password`; nunca hay usuario ni contraseña por defecto.
- A2. **Panel de administración:** registro abierto o cerrado; usuarios (listar, desactivar y reactivar, restablecer la contraseña); cursos instalados y su versión (solo verlos: se instalan por consola); nombre de la instancia.
- A3. **Perfil:** cambiar la propia contraseña.
- Fuera de H9: registro por invitación (después del 1.0), el nombre y el logo por defecto (H10).

## Decisiones de diseño (propuestas; las revisa el autor con el resto de H9)

- D1. **Consola:** `--create-admin=<usuario>`, en una terminal real como `--reset-password`. Si el usuario no existe, lo crea como administrador y pide la contraseña dos veces (la misma regla que el registro); si existe, lo hace administrador sin tocar su contraseña. Nunca crea nada sin una terminal.
- D2. **Desactivar** a un usuario: no puede entrar, sus sesiones abiertas se cierran en su siguiente petición (se sube su versión de credenciales) y conserva su progreso; se puede reactivar. Un administrador no puede desactivarse a sí mismo ni quitar el último administrador activo.
- D3. **Restablecer la contraseña** desde el panel: el administrador escribe una contraseña nueva dos veces (la misma regla) y se la pasa al usuario por su cuenta; las sesiones de ese usuario se cierran. La academia no envía correos.
- D4. **Registro cerrado:** la API de registro responde 403 con un mensaje claro y la pantalla de entrada no ofrece «Crear una cuenta». Abierto por defecto.
- D5. **Nombre de la instancia:** se guarda en la base y, si está puesto, sustituye al nombre de la cabecera y del título; si no, se usa el de siempre (que H10 deja en un solo sitio).

## Tareas

T1 | A1 | Roles en la base, seguridad por rol, `--create-admin` por consola y usuarios desactivados | pendiente.
T2 | A2 | API de administración: ajustes (registro, nombre), usuarios (listar, desactivar, reactivar, restablecer contraseña) y cursos instalados | pendiente.
T3 | A2 | Pantallas del panel | pendiente.
T4 | A3 | Perfil: cambiar la propia contraseña (API y pantalla) | pendiente.
T5 | todos | Documentación, prueba en el navegador y cierre | pendiente.

## Registro

L1. 16 de noviembre de 2026: H8 integrada en `main` (`d08a5a5`) y rama borrada; el autor pide seguir con H9. Rama `feat/h9-cuentas-admin` desde `9ae8115`. Decisiones de diseño D1 a D5 propuestas, para la revisión del autor.
