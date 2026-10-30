# H5 — Available content and controlled updates
Objective: implement the owner-approved H5 scope. Branch: `feat/h5-content-import`; local commits only, merge requires separate approval.
Delivery: local reviewable work units, no remotes/PR/publication. Runners: backend Maven verify; frontend test/lint/build; isolated journey.

## Specs

The following exact proposal text was approved by the owner's «si». No additional product requirements are implied.

S1. «Origen fijo: commit `7e71300`, sin cambios sin commit.» Full approved source: `7e713008e2e04e9993f827ced8a3fe679a35185c`. Check provenance against that immutable tree, never moving HEAD.
S2. «Contenido: importar las 164 lecciones marcadas disponibles, validando ejercicios, enlaces y recursos.» Validate the inventory against the fixed source; report discrepancies rather than fabricate content.
S3. «Proyecto evolutivo: sección propia dentro de Java; 12 etapas escritas y 2 pendientes, con progreso separado.» Owner separately approved the section and separate progress; S9–S10 define states and continuation.
S4. «Recursos: imágenes seguras, tachado y carpetas descargables como ZIP.» Verify rendering, authenticated contained resource delivery and folder archive behavior with focused tests.
S5. «Actualizaciones: comparación previa y conflictos explícitos; nunca borrar ni reasignar progreso silenciosamente.» Verify a successful incremental update and rejected conflicts against isolated state, including unchanged progress on rejection.
S6. «Verificación: pruebas aisladas de importación, actualización y conservación del progreso de dos usuarios; documentación actualizada.» Existing lesson/exercise states must survive. No local owner database use for verification.
S7. «Quedan fuera búsqueda, personalización y publicación. También corregiremos el estado desactualizado de H4 en `PLAN.md`.»
S8. «Los commits serían locales en una rama de trabajo; el merge requerirá tu aprobación aparte.» Do not read/change `.env` or `.env.example`, change source repository files, remove owner volumes, create remotes, push or merge.

S9. Owner selected «No completada / completada (recomendado)» for «¿Cómo querés marcar el progreso de cada etapa del Proyecto evolutivo? Siempre manual, sin corrección automática.»
S10. Owner selected «Sí, continuación independiente (recomendado)» for «¿Querés recordar la última etapa abierta, por separado de la última lección?»

## Tasks

T1 | S1,S3,S5,S7,S8,S9,S10 | route: delegated contracts writer (context backstop) | done: structural checks passed | commit: 0cc6748.
T2 | S4,S6 | route: delegated bounded writer (context backstop), independent verifier for security/dependency risk | in_progress: safe Markdown images and strikethrough first; ZIP follows manifest extensions | commit: none.
T3 | S1,S2,S5,S6 | route: delegated bounded writer (context backstop), independent verifier for update/data risk | pending | commit: none.
T4 | S3,S6,S9,S10 | route: delegated vertical-slice writer (context backstop), independent verifier for new persistence/auth surfaces | pending | commit: none.
T5 | S1,S2,S3,S4,S5 | route: delegated source inventory/import writer (context backstop), isolated validation | pending | commit: none.
T6 | S2,S3,S5,S6 | route: delegated independent integration verification for preservation/security risk | pending | commit: none.
T7 | S6,S7,S8 | route: parent final reconciliation and documentation | pending | commit: none.

## Log

L1. 2026-10-30: Owner's implementation authorization, verbatim: «si», replying to «¿Autorizás implementar H5 con este alcance? Los commits serían locales en una rama de trabajo; el merge requerirá tu aprobación aparte.» The approved proposal is quoted in S1–S8.
L2. Owner previously requested «mira como esta ahora el proyecto fuente del curso java, para arrancar con el mas actual» and approved pinning `7e713008e2e04e9993f827ced8a3fe679a35185c`. At observation time source index had 199 lessons/21 phases, 164 marked available, 12 stage Markdown files out of 14 planned. Source working changes are excluded; source stays read-only via Git immutable reads.
L3. Read-only mapping task mvuxzh99-1-508i: ContentLoader validates copies, not source imports; CatalogConfiguration couples loading and sync; CatalogSynchronizer transactionally rejects missing IDs, moved ownership and retitled exercises. LessonPipeline extracts by exact exercise title. Markdown supports tables only; sanitizer rejects images. Progress denominator includes every catalogue lesson, so stages must not be ordinary counted lessons. Manifest.java spot-check confirms no stage model.
L4. Existing baseline: main 9920abe, worktree clean before feature branch creation. No H5 source edits or tests yet. No services started. Tracking is large because source inventory, new stage semantics and safe update design cannot be resumed from an implementation diff alone.
L5. Forecast: roughly 2,000–4,000 authored implementation/test/doc changed lines, plus much larger copied educational content (separately measured). Reviewable local slices; protect review workload by bounded candidates. No PR delivery requested. Stage states/continuation were clarified in L6 before shared contracts implementation.
L6. Owner choices, verbatim: «No completada / completada (recomendado)» and «Sí, continuación independiente (recomendado)». No further product decision is currently open; destructive identity changes remain explicit rejected conflicts, not automatic reconciliation.
L7. T1 worker mvuye8jz-2-viil changed only PLAN.md/docs/manifiesto.md/docs/api.md. Parent read complete diff; git diff --check passed. Worker structural check: 28 links/anchors, 5 JSON examples, balanced fences. Commit 0cc6748 includes tracking. Native committed assessment against 9920abe: passive, reviewDue=false, structural readback only; no review started. First precommit assess was unassessable due untracked tracking file; not an approval. No functional tests applicable to passive docs.
L8. T2 is sequenced into a first safe images/strikethrough unit and ZIP after manifest v2 support; keep v1 folder output identical. Use focused RED/GREEN and compatibility checks; security/dependency changes require independent verification.
L9. T2 first unit: writer RED job-1 exit 1 (91 tests: 5 failures,10 errors), GREEN 91/91 in 14.5s; parent full verify job-2 exit 0, 450/450 in 64s. Independent verifier mvuyzj4s-6-xq71: PASS S4 images/strike and S6/S8 preservation, 91/91 isolated tests, unchanged base/candidate fixture HTML hash, live backend Chromium authenticated PNG/JPEG/SVG render and anonymous 401 with original headers. No candidate blockers; optional extra probes are not scope expansion. Maven metadata/default JVM tmp artifacts are ordinary permitted test effects, not owner-data changes; no verification rerun. Risk items 2/5; escalated assess selected independent verification. Images unit is 561 authored lines mostly tests; keep coherent rather than omit tests/code-golf. Native review pending after unit commit; ZIP not done.
