# H5 technical debt

Resolve the six technical-debt items recorded at the end of H5 (odd/tasks/h5-content-import.md L65).
Branch: feat/h5-tech-debt (local only; no push). Merge into main needs separate owner approval.

## Specs

S1. «9 advertencias de la revisión nativa, la mayoría en ManifestValidator.»
    Advisories of review-80a79dd56fea718d at 8cb1798: R1-001 ManifestValidator:415-416, R1-002 :271-273, R3-001 :270-273, R3-002 :405-411, R3-003 :392-394, R3-004 LessonPipeline:451-455, R3-005 LessonPipeline:277, R3-006 ContentUpdateComparison:179, R3-007 FolderZip:47-58 (suggestion). The advisory texts were not stored; each one is re-assessed from the code and either fixed or recorded with the reason it needs no change.
S2. «Un NPE cuando memberResourceIds llega en null.»
S3. «Los enlaces a archivos protegidos bloquean la generación.»
S4. «Reglas de entorno de Git duplicadas en ContentImportSource.»
S5. «CatalogSynchronizationTest es lento.»
S6. «IDs de recursos con acentos descompuestos.»

## Tasks

T1. S2, S1 (R1-002, R3-001): ZIP metadata check must report, not throw, on null memberResourceIds; message matches the subset rule. Inline, test-first. Commit: see L3.
T2. S1 (R3-004, R3-005, R3-006, R1-001, R3-002, R3-003, R3-007): re-assess each advisory; fix real defects test-first, record the rest. Inline. Commit: pending.
T3. S3: a direct link to a protected file is reported and rendered as plain text instead of blocking generation; protected blobs are still never read. Inline, test-first. Risk item 2 → independent verifier. Commit: pending.
T4. S4: one shared Git process builder for ContentImportSource and ContentUpdateComparison, byte-identical environment. Inline. Risk item 2 → independent verifier (with T3). Commit: pending.
T5. S5: CatalogSynchronizationTest loads the real course once. Inline. Commit: pending.
T6. S6: the manifest validator rejects resource IDs that are not NFC or contain combining marks (\p{M}), so validation and the link rule agree; current content stays valid. Inline, test-first. Commit: pending.
T7. Docs (docs/manifiesto.md, docs/actualizacion-contenido.md, H5 L65 pointer), full backend suite, close. Commit: pending.

## Log

L1. Owner request, verbatim (answer to the list of open work after H5):
«Deuda técnica de H5 (registro de H5, L65)
 - 9 advertencias de la revisión nativa, la mayoría en ManifestValidator.
 - Un NPE cuando memberResourceIds llega en null.
 - Los enlaces a archivos protegidos bloquean la generación.
 - Reglas de entorno de Git duplicadas en ContentImportSource.
 - CatalogSynchronizationTest es lento.
 - IDs de recursos con acentos descompuestos.»
L2. Explorer mw7doly8-1-gocb mapped the items at HEAD 97d80e2. Advisory texts are not stored (searched .git/gentle-ai and Engram); R1-001, R3-002, R3-003 concerns are low-confidence guesses. NPE confirmed at ManifestValidator:272 (`containsAll(null)` after the null was already reported at :268). Generated resource IDs never contain \p{M} (HeadingIds.isWordCharacter excludes marks, ContentImportGenerator:527), so S6 only affects hand-edited manifests; decision: reject non-NFC / \p{M} IDs in the validator instead of admitting marks in ContentSanitizer.HREF (marks would allow invisible or lookalike sequences) or normalizing at load (would change stored IDs).
L3. T1: RED ZipContentTest.reportsAnArchiveWithNullMembersInsteadOfFailing failed with NullPointerException (`containsAll(null)`, ManifestValidator:272). Fix: a null member list is already reported as «invalid ZIP metadata» and no longer runs the decoration check, so it is reported once; the decoration message now states the subset rule it checks («ZIP must decorate the folder that lists its members», was «…its exact ordered folder», R1-002/R3-001), tests updated. Snapshot of the fixture unchanged. Focused GREEN 202/202 (ZipContentTest, ProjectStageContentTest, ContentLoaderTest, ContentImportCliTest, ContentImportGeneratorTest).
L4. T2 re-assessment (advisory texts unavailable, judged from code at HEAD):
- R1-001 (:415-416) null members guarded there; consistent with T1 now. No change.
- R3-002 (:405-411) "two lessons or two stages" and "does not list exactly" are independent rules; reporting both is correct. No change.
- R3-003 (:392-394) unknown member IDs are skipped there because unknown references are reported by the reference check. No change.
- R3-004 (LessonPipeline:451-455) inert removals are warnings keyed by source file on purpose: the import report tells the maintainer which file to edit (ContentImportCliTest:488-506 pins it). No change.
- R3-005 (LessonPipeline:277) thematic breaks outside exercises are section separators dropped since H4 (8ec8416); not authored content. No change.
- R3-006 (ContentUpdateComparison:179) duplicate identity inside one copy is unreachable after validation (probe: repeated exercise title is refused by the loader before comparison, «exercise demo-01.01-e02 of demo-01.01 repeats the title»). Defensive only; message change reverted with its probe.
- R3-007 (FolderZip:47-58) an I/O failure keeps earlier archives, but the importer writes into an output that must not exist (ContentImportCli:60) and the output is discarded on failure; javadoc corrected to say so instead of "no partial archive is ever produced".
