# Licencias

La academia se publica con la licencia **MIT** (`LICENSE`). Cada curso lleva la suya en su propio repositorio.

## Dependencias que se distribuyen

Revisadas el 16 de noviembre de 2026. Solo cuentan las que llegan a la aplicación instalada; las de desarrollo y pruebas no se distribuyen.

**Frontend** (`frontend/package-lock.json`, sin las de desarrollo): 126 paquetes. Casi todos MIT, ISC, Apache-2.0, BSD-2-Clause, BSD-3-Clause o Unlicense. Dos piden atención, y ninguna obliga a cambiar la licencia de la academia:

| Paquete | Licencia | Por qué llega | Qué supone |
|---|---|---|---|
| `elkjs` | EPL-2.0 o GPL-3.0-or-later | Mermaid lo usa para colocar algunos diagramas | Se usa sin modificar. Si alguien lo modifica y lo distribuye, tiene que publicar esos cambios con la EPL-2.0. |
| `dompurify` | MPL-2.0 o Apache-2.0 | Mermaid lo usa para sanear | Se elige la Apache-2.0. |

`khroma` (también de Mermaid) no declara licencia en su `package.json`, pero su fichero `license` es MIT.

**Backend** (dependencias de ejecución de Maven): 104. Casi todas Apache-2.0; el resto MIT, BSD-2-Clause (el controlador de PostgreSQL y commonmark), BSD-3-Clause, Eclipse Distribution License 1.0 y EPL-2.0 (las API de Jakarta, AspectJ), EPL-1.0 o LGPL-2.1 (Logback) y la licencia de Bouncy Castle (de tipo MIT). Todas permiten distribuirlas sin modificar dentro de una aplicación MIT.

## Vulnerabilidades conocidas

Revisadas el 16 de noviembre de 2026: `npm audit` sin ninguna, tampoco en las de desarrollo, y las 104 dependencias de ejecución de Maven, consultadas en la base de datos pública OSV (osv.dev), sin ninguna.
