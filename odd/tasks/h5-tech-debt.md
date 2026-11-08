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
T3. S3: a direct link to a protected file is reported and rendered as plain text instead of blocking generation; protected blobs are still never read. Inline, test-first. Risk item 2 → independent verifier. Commit: see L7.
T4. S4: one shared Git process builder for ContentImportSource and ContentUpdateComparison, byte-identical environment. Inline. Risk item 2 → independent verifier (with T3). Commit: 93fec7c.
T5. S5: CatalogSynchronizationTest loads the real course once. Inline. Commit: c44735c.
T6. S6: the manifest validator rejects resource IDs that are not NFC or contain combining marks (\p{M}), so validation and the link rule agree; current content stays valid. Inline, test-first. Commit: see L8.
T7. Docs (docs/manifiesto.md, docs/actualizacion-contenido.md, H5 L65 pointer), full backend suite, close. Commit: 0829d85 + this log entry (L9, L10).

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
L5. T5: CatalogSynchronizationTest keeps one static load of the real course (every use is read-only; synchronize still runs per test). 13/13 in 16.4s, was about 13 loads of 9s. Commit c44735c.
L6. T4: ContentUpdateComparison.gitProcess(Path, String...) is now the single builder of Git source processes (GIT_* removed, GIT_LITERAL_PATHSPECS=1, GIT_CONFIG_NOSYSTEM=1, GIT_CONFIG_GLOBAL=/dev/null, stderr discarded, --no-replace-objects --no-pager -C); ContentUpdateComparison.git and ContentImportSource's cat-file --batch reader use it. Pure refactor, no RED; ContentImportCliTest 34/34, ContentUpdateCliTest 25/25, ContentImportGeneratorTest 8/8. Commit 93fec7c. Risk item 2 (process boundary) → verifier with T3.
L7. T3: RED ContentImportCliTest.aDirectLinkToAProtectedFileIsReportedAndKeepsOnlyItsText: exit 3 «Candidate not written; findings: 1» (loader: unresolved link). Cause: the importer excludes and reports the protected file, but the copied document still links it and LessonPipeline treats any unresolved link as a problem. Documents are copied byte for byte and the manifest must never name a protected path, so the fix is in the loader: a relative link whose resolved target has a .git, .env or .env.* segment (ContentUpdateComparison.safeNames, the importer's own policy) keeps only its text without a problem. Paths climbing above the root, look-alikes (.envrc, env) and every other unresolved link still fail (ContentLoaderTest.stillRejectsUnresolvedLinksThatAreNotProtected, passed before and after); ContentLoaderTest.keepsOnlyTheTextOfLinksToProtectedSourcePaths was RED without the fix. Import report: exactly one «protected source path excluded by policy» line, exit 0, no secret bytes in the output, source and active copies unchanged. Focused 372/372 (MaliciousContentTest 107 alone takes about 8.5 min).
L8. T6: RED ContentLoaderTest.rejectsAResourceIdWithCombiningMarks (a manifest with resource id «demo-01.01-r-an\u0303o» loaded without error). ManifestValidator now reports «resource id X contains combining marks; use composed (NFC) characters» for any \p{M} in a resourceId; the composed «demo-01.01-r-año» still loads (acceptsAResourceIdWithComposedLetters). Real content unaffected (RealLessonContentTest 15/15); ContentLoaderTest 73/73, ZipContentTest 48/48, ProjectStageContentTest 45/45. docs/manifiesto.md states the rule; docs/actualizacion-contenido.md states the T3 protected-link behaviour. Risk: item 3 (manifests are a consumed format), limited to IDs the link rule could never serve and the importer never generates.
L9. Full backend suite at 27b547e: 695/695 BUILD SUCCESS in 13:59 (686 at H5 close + 9 new). Independent verifier mw7eep5x-2-304s (Risk item 2, scratch copy, repository untouched): S3 met, S4 met, no blockers. Encoded, relative, fragment and trailing-slash protected links keep only text; case variants (.ENV, .GIT) are not protected in loader nor importer (consistent with safeNames); look-alikes, climbing above the root, paths leaving a protected segment, '?', absolute and scheme links still fail; a chmod 000 .env is never read; importer reports one line for several links. gitProcess is the old code moved line for line. Advisories: A1 a dangling protected link still blocks in the importer (stricter; docs sentence narrowed to an existing file); A2 `.env.x?y` kept as text by the loader, refused by the importer; A3 `%2Fx/.env` kept as text (was already treated as relative); A4 images to protected paths still rejected (outside S3). Its probe file lived in its own scratch and was not recoverable; the main cases were rewritten as durable tests: ContentLoaderTest keepsOnlyTheTextOfEncodedOrRelativeProtectedLinks (11), stillRejectsUnresolvedLinksThatAreNotProtected (10), stillDisallowsAbsoluteOrSchemeLinksToProtectedNames (3) and ContentUpdateComparisonTest.everyGitSourceProcessGetsTheSameCommandAndEnvironment; 95/95 GREEN.
L10. Native review of main..0829d85 (assess: high, item 2 escalation, reviewDue=true; 272 lines, 14 paths). Lineage review-a743d47132ffb9d4, lenses risk + reliability, forecast 2 model runs (pi_host_relay): APPROVED with 6 non-blocking advisories, recorded as later work and never as a re-review reason: R3-001 WARNING LessonPipeline:521-527, R3-002 WARNING LessonPipeline:517-519, R3-003 WARNING ManifestValidator:282, R3-004 SUGGESTION ManifestValidator:261-265, R3-005 SUGGESTION ContentLoaderTest:409-417, R3-006 SUGGESTION CatalogSynchronizationTest:53-54. Exact acknowledge-approved: native-approved-acknowledgement-completed, authority burned, consumed revision sha256:74fcf44cf56b336a722e2b2d2d0a1622a203841f3ff2a31451dad60715812d86. All tasks T1-T7 complete; merge into main needs separate owner approval.
