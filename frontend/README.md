# Frontend técnico de Code Academy

Shell técnico de H3: muestra el estado de conexión con el backend. No incluye cuentas, cursos ni progreso de aprendizaje.

## Comandos locales

Desde `frontend/`, con Node 24.22.1 y npm 11.19.1:

| Comando | Propósito |
| --- | --- |
| `npm ci` | Reproducir las dependencias del lockfile existente. |
| `npm run test -- --run` | Pruebas unitarias con fetch simulado, sin servidores ni red. |
| `npm run lint` | Oxlint del template oficial. |
| `npm run build` | Comprobar TypeScript y construir con Vite. |
| `npm run dev` | Servidor local en `127.0.0.1:5173`, sin cambiar de puerto si está ocupado. |
| `npm run preview` | Previsualización del build, solo loopback. |
| `npm run test:e2e` | Smoke Chromium con backend real ya iniciado en `127.0.0.1:8080`. |

Los resultados y pendientes se registran únicamente en [las tareas de H3](../odd/tasks/h3-base.md).

El smoke requiere Chromium de Playwright previamente instalado; no instala navegadores, no inicia el backend ni usa respuestas simuladas. Puede reutilizar el servidor Vite local existente. Detenerse si el navegador requiere paquetes del sistema.

## Interfaz técnica

- Título: `Code Academy`.
- Aviso: `Base técnica. Todavía no hay cursos disponibles.`
- Estado accesible (`role="status"`): `Comprobando conexión con el backend…`, luego `Backend disponible.` solo ante HTTP exitoso con un objeto JSON cuyo `status` sea `UP`; en otros casos, `Backend no disponible.`
- Única consulta: `GET /actuator/health` del mismo origen. Sin escrituras de aprendizaje, reintentos ni detalles del error en pantalla. StrictMode puede repetir este GET seguro durante el desarrollo.

Vite conserva los paths de `/api` y del endpoint exacto `/actuator/health` al reenviarlos al backend loopback. La configuración del frontend no carga el entorno de la raíz; nunca colocar contraseñas en variables `VITE_*`.
