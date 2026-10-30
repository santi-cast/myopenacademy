Copia controlada del contenido de la academia, descrita por su [manifiesto](../docs/manifiesto.md). El backend la carga y valida al arrancar.
`java/manifest.json` tiene el temario completo del curso Java (199 módulos en 21 fases); solo la lección 02.01 está importada, con sus recursos, copiada desde el commit indicado en `source.versionRef`.

Para comparar y aplicar **copias candidatas ya preparadas**, usar el [procedimiento offline](../docs/actualizacion-contenido.md) y `sh backend/content-update --help`. No genera el contenido de H5 ni escribe en la base; aplicar exige detener el servidor. Mantener los complementos separados de los archivos gestionados. La raíz anterior se retiene como respaldo, sin limpieza automática.
