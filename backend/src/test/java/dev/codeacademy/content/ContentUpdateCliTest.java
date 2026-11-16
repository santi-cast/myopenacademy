package dev.codeacademy.content;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Public process boundary: no Spring, database or real source repository. */
class ContentUpdateCliTest {
    @TempDir Path temp;
    ContentFixture active;
    ContentFixture candidate;
    Path source;
    Path report;
    static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeEach void prepare() throws Exception {
        active = ContentFixture.copyInto(temp.resolve("active"));
        candidate = ContentFixture.copyInto(temp.resolve("candidate"));
        source = Files.createDirectory(temp.resolve("source.git"));
        git(null, "init", "--bare", "--quiet", source.toString());
        // Build immutable objects without a checkout, index, branch or git commit command.
        Map<String, byte[]> files = new TreeMap<>();
        Manifest manifest = active.load().manifest();
        manifest.lessons().stream().filter(Manifest.Lesson::available).forEach(l -> {
            try { files.put(l.sourcePath(), Files.readAllBytes(active.root.resolve(l.bodyPath()))); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        for (var resource : manifest.resources()) {
            files.put(resource.sourcePath(), Files.readAllBytes(active.root.resolve(resource.localPath())));
        }
        String tree = tree(files);
        String ref = git("fixture\n".getBytes(StandardCharsets.UTF_8), "commit-tree", tree).strip();
        active.editManifest(m -> m.withObject("/source").put("versionRef", ref));
        candidate.editManifest(m -> m.withObject("/source").put("versionRef", ref));
        report = temp.resolve("comparison.json");
    }

    String tree(Map<String, byte[]> files) throws Exception {
        Map<String, byte[]> direct = new TreeMap<>();
        Map<String, Map<String, byte[]>> directories = new TreeMap<>();
        for (var entry : files.entrySet()) {
            int slash = entry.getKey().indexOf('/');
            if (slash < 0) direct.put(entry.getKey(), entry.getValue());
            else directories.computeIfAbsent(entry.getKey().substring(0, slash), k -> new TreeMap<>())
                .put(entry.getKey().substring(slash + 1), entry.getValue());
        }
        StringBuilder input = new StringBuilder();
        for (var entry : direct.entrySet()) {
            String blob = git(entry.getValue(), "hash-object", "-w", "--stdin").strip();
            input.append("100644 blob ").append(blob).append('\t').append(entry.getKey()).append('\0');
        }
        for (var entry : directories.entrySet()) {
            input.append("040000 tree ").append(tree(entry.getValue())).append('\t').append(entry.getKey()).append('\0');
        }
        return git(input.toString().getBytes(StandardCharsets.UTF_8), "mktree", "-z").strip();
    }

    String git(byte[] input, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", source.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().putAll(Map.of("GIT_AUTHOR_NAME", "Fixture", "GIT_AUTHOR_EMAIL", "fixture@example.invalid",
            "GIT_COMMITTER_NAME", "Fixture", "GIT_COMMITTER_EMAIL", "fixture@example.invalid"));
        Process p = builder.start();
        if (input != null) p.getOutputStream().write(input);
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).as(err).isZero();
        return out;
    }

    record Result(int code, String out, String err) {}
    Result cli(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
            "-Duser.home=" + System.getProperty("user.home"),
            "-cp", System.getProperty("java.class.path"), "dev.codeacademy.content.ContentUpdateCli"));
        command.addAll(List.of(args));
        command.addAll(0, List.of("sh", "-c", "umask 022; exec \"$@\"", "content-update-test"));
        ProcessBuilder builder = new ProcessBuilder(command);
        // Preserve assigned scratch locations explicitly, without ambient launcher banners on stderr.
        for (String variable : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
            builder.environment().remove(variable);
        }
        Process p = builder.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(10, TimeUnit.SECONDS)).isTrue();
        return new Result(p.exitValue(), out, err);
    }
    Result run(String operation) throws Exception {
        return cli(operation, "--active", active.root.toString(), "--candidate", candidate.root.toString(),
            "--source", source.toString(), "--report", report.toString());
    }
    Map<String, String> snapshot(Path root) throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                String relative = root.relativize(path).toString();
                result.put(relative, Files.isSymbolicLink(path) ? "link:" + Files.readSymbolicLink(path)
                    : Files.isDirectory(path) ? "directory" : java.util.HexFormat.of().formatHex(Files.readAllBytes(path)));
            }
        }
        return result;
    }
    void rejected(String operation, String error) throws Exception {
        var before = snapshot(temp);
        assertThat(run(operation)).isEqualTo(new Result(2, "", "content-update: " + error + "\n"));
        assertThat(snapshot(temp)).isEqualTo(before);
    }

    @Test void helpHasNoFilesystemEffects() throws Exception {
        var before = snapshot(temp);
        Result help = cli("--help");
        assertThat(help).isEqualTo(new Result(0, "Usage: content-update compare|apply --active DIR --candidate DIR --source GIT --report FILE\n"
            + "Compare prepared copies offline; apply requires the server stopped. No database writes.\n", ""));
        assertThat(snapshot(temp)).isEqualTo(before);
        assertThat(cli("--force")).isEqualTo(new Result(2, "", "content-update: invalid arguments; use --help\n"));
        assertThat(cli("compare", "--active", active.root.toString(), "--candidate", candidate.root.toString(),
            "--source", source.toString(), "--report", temp.resolve(".env.example").toString()))
            .isEqualTo(new Result(2, "", "content-update: protected paths are not allowed\n"));
        assertThat(snapshot(temp)).isEqualTo(before);
    }

    @Test void comparesAndAppliesWithRetainedBackupAndComplements() throws Exception {
        Files.writeString(active.root.resolve("notes.txt"), "Academy complement");
        candidate.editManifest(m -> {
            ContentFixture.lesson(m, "demo.01.01").put("position", 2);
            ContentFixture.lesson(m, "demo.01.02").put("position", 1);
        });
        var before = snapshot(active.root);
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(snapshot(active.root)).isEqualTo(before);
        var data = JSON.readTree(Files.readString(report));
        assertThat(data.path("baseFingerprint").asString()).matches("[0-9a-f]{64}");
        assertThat(data.path("candidateFingerprint").asString()).matches("[0-9a-f]{64}");
        assertThat(data.path("changes").toString()).contains("demo.01.01", "metadata");
        assertThat(run("apply")).isEqualTo(new Result(0, "Applied; previous root retained at "
            + active.root.resolveSibling(active.root.getFileName() + ".previous") + "\n", ""));
        assertThat(Files.readString(active.root.resolve("notes.txt"))).isEqualTo("Academy complement");
        assertThat(snapshot(active.root.resolveSibling("content.previous"))).isEqualTo(before);
        assertThat(ContentLoader.loadAll(active.root).getFirst().manifest().lessons().getFirst().position()).isEqualTo(2);
    }

    @Test void applyPreservesPrivateDirectoriesAndComplementModesUnderUmask022() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Files.setPosixFilePermissions(active.root, PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(active.root.resolve("demo"), PosixFilePermissions.fromString("rwx--x---"));
        Files.setPosixFilePermissions(active.root.resolve("demo/resources"), PosixFilePermissions.fromString("rwxr-x---"));
        Files.writeString(active.root.resolve("notes.txt"), "private through its parent");
        Files.setPosixFilePermissions(active.root.resolve("notes.txt"), PosixFilePermissions.fromString("rw-r--r--"));
        Files.writeString(active.root.resolve("demo/resources/local.txt"), "nested complement");
        Files.setPosixFilePermissions(active.root.resolve("demo/resources/local.txt"), PosixFilePermissions.fromString("rw-------"));
        candidate.write("demo/resources/local.txt", "nested complement");
        Files.setPosixFilePermissions(candidate.root.resolve("demo/resources/local.txt"), PosixFilePermissions.fromString("rw-r--r--"));
        candidate.addResource("demo.new-resource", "new.txt", "text/plain", "new bytes".getBytes(StandardCharsets.UTF_8));
        pinCandidate();
        var before = snapshot(active.root);
        var candidateBefore = snapshot(candidate.root);
        var sourceBefore = snapshot(source);
        Map<String, String> modes = new TreeMap<>();
        for (String path : List.of("", "demo", "demo/resources", "notes.txt", "demo/resources/local.txt")) {
            modes.put(path, PosixFilePermissions.toString(Files.getPosixFilePermissions(active.root.resolve(path))));
        }
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        Path backup = active.root.resolveSibling("content.previous");
        assertThat(run("apply")).isEqualTo(new Result(0, "Applied; previous root retained at " + backup + "\n", ""));
        var expected = new TreeMap<>(candidateBefore);
        expected.put("notes.txt", before.get("notes.txt"));
        expected.put("demo/resources/local.txt", before.get("demo/resources/local.txt"));
        assertThat(snapshot(active.root)).isEqualTo(expected);
        assertThat(snapshot(backup)).isEqualTo(before);
        assertThat(snapshot(candidate.root)).isEqualTo(candidateBefore);
        assertThat(snapshot(source)).isEqualTo(sourceBefore);
        for (var entry : modes.entrySet()) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(active.root.resolve(entry.getKey()))))
                .as("installed permissions: %s", entry.getKey()).isEqualTo(entry.getValue());
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(backup.resolve(entry.getKey()))))
                .as("backup permissions: %s", entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test void sourceMismatchRejectsV1BootstrapEvenIfBothCopiesAgree() throws Exception {
        active.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Adapted body.");
        candidate.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Adapted body.");
        var before = snapshot(active.root);
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 2\n", ""));
        assertThat(Files.readString(report)).contains("source bytes differ", "demo/lessons/demo.01.01.md");
        assertThat(snapshot(active.root)).isEqualTo(before);
        rejected("apply", "comparison contains conflicts");
    }

    @Test void retitlesAreChangesAndReportIsNotAuthority() throws Exception {
        // The ID is the identity (curso.json): a new title is a change to review, not a conflict (FORMATO.md 11.2).
        candidate.editManifest(m -> ContentFixture.lesson(m, "demo.01.01").put("title", "Retitled"));
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(Files.readString(report)).contains("retitle demo.01.01: «Introducción» -> «Retitled»");
        String forged = Files.readString(report).replace("retitle demo.01.01", "approved");
        Files.writeString(report, forged);
        rejected("apply", "report does not match current comparison");
    }

    @Test void candidateAndBaseDriftAreRejected() throws Exception {
        assertThat(run("compare").code()).isZero();
        candidate.editManifest(m -> ContentFixture.lesson(m, "demo.01.01").put("position", 2));
        rejected("apply", "invalid content: lesson positions must be 1..3 without gaps or repeats");
    }

    @Test void baseComplementDriftAndReportPlacementAreRejected() throws Exception {
        assertThat(run("compare").code()).isZero();
        Files.writeString(active.root.resolve("notes.txt"), "new");
        rejected("apply", "report does not match current comparison");
        report = active.root.resolve("report.json");
        rejected("compare", "paths must not overlap");
    }

    @Test void additiveV2ContentUsesImmutableSourceAndReportsBytes() throws Exception {
        candidate.replace("demo/lessons/demo.01.02.md", "Texto A.", "Updated body.");
        candidate.addResource("demo.new-resource", "new.txt", "text/plain", "new educational file".getBytes(StandardCharsets.UTF_8));
        pinCandidate();
        upgradeCandidate();
        // A checkout is neither necessary nor consulted; immutable blobs are the sole input.
        Files.writeString(source.resolve("untracked.txt"), "ignored");
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(Files.readString(report)).contains("demo.new-resource", "bytes", "demo/lessons/demo.01.02.md");
        assertThat(run("apply").code()).isZero();
        assertThat(ContentLoader.loadAll(active.root).getFirst().manifest().schemaVersion()).isEqualTo(2);
    }

    void pinCandidate() throws Exception {
        var manifest = candidate.load().manifest();
        Map<String, byte[]> files = new TreeMap<>();
        for (var lesson : manifest.lessons()) if (lesson.available()) {
            files.put(lesson.sourcePath(), Files.readAllBytes(candidate.root.resolve(lesson.bodyPath())));
        }
        if (manifest.projectStages() != null) for (var stage : manifest.projectStages()) if (stage.available()) {
            files.put(stage.sourcePath(), Files.readAllBytes(candidate.root.resolve(stage.bodyPath())));
        }
        for (var resource : manifest.resources()) if (!"zip".equals(resource.kind())) {
            files.put(resource.sourcePath(), Files.readAllBytes(candidate.root.resolve(resource.localPath())));
        }
        String ref = git("updated fixture\n".getBytes(StandardCharsets.UTF_8), "commit-tree", tree(files)).strip();
        candidate.editManifest(m -> m.withObject("/source").put("versionRef", ref));
    }

    void upgradeCandidate() throws Exception {
        var manifest = candidate.load().manifest();
        Map<String, String> files = new TreeMap<>();
        manifest.lessons().stream().filter(Manifest.Lesson::available).forEach(l -> files.put(l.bodyPath(), l.sourcePath()));
        manifest.resources().forEach(r -> files.put(r.localPath(), r.sourcePath()));
        candidate.editManifest(m -> {
            m.put("schemaVersion", 2);
            m.putArray("projectStages");
            for (var resource : m.path("resources")) ((tools.jackson.databind.node.ObjectNode) resource).put("kind", "file");
            var provenance = m.putObject("provenance");
            provenance.putArray("generated");
            var copies = provenance.putArray("files");
            files.forEach((local, src) -> {
                try {
                    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(candidate.root.resolve(local))));
                    copies.addObject().put("sourcePath", src).put("localPath", local)
                        .put("sourceSha256", hash).put("copySha256", hash).putNull("transformation");
                }
                catch (Exception ex) { throw new RuntimeException(ex); }
            });
        });
    }

    @Test void forgedV2HashesCannotBlessAnAdaptation() throws Exception {
        candidate.replace(ContentFixture.INTRO, "Texto de cuerpo.", "Adapted body.");
        upgradeCandidate();
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(Files.readString(report)).contains("source bytes differ");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void allIdentityConflictsAreReportedTogether() throws Exception {
        candidate.editManifest(m -> {
            m.withObject("/course").put("title", "Retitled course");
            ContentFixture.phase(m, "demo.phase-01").put("title", "Retitled phase");
            ContentFixture.lesson(m, "demo.01.01").put("title", "Retitled lesson");
            ContentFixture.resource(m, "demo.01.01-r-solo").put("lessonId", "demo.01.02");
            ContentFixture.lesson(m, "demo.01.02").put("available", false).putNull("bodyPath");
        });
        // The three retitles are changes; only owner, availability and the unmanaged path are conflicts.
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 3\n", ""));
        assertThat(Files.readString(report)).contains("retitle demo", "retitle demo.phase-01", "retitle demo.01.01",
            "owner move demo.01.01-r-solo", "availability loss demo.01.02", "unmanaged candidate path demo/lessons/demo.01.02.md");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void removedExerciseAndReassignedIdentityAreNotReconciled() throws Exception {
        candidate.editManifest(m -> {
            var exercises = (tools.jackson.databind.node.ArrayNode) ContentFixture.lesson(m, "demo.01.01").path("exercises");
            ((tools.jackson.databind.node.ObjectNode) exercises.get(0)).put("id", "demo.new-exercise");
        });
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 2\n", ""));
        assertThat(Files.readString(report)).contains("removal demo.01.01.e01", "ambiguous identity");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void anExistingExerciseCanChangeItsTitleWithNewSourceBytes() throws Exception {
        candidate.replace(ContentFixture.INTRO, "Primer ejercicio", "Changed exercise");
        candidate.editManifest(m -> ((tools.jackson.databind.node.ObjectNode)
            ContentFixture.lesson(m, "demo.01.01").path("exercises").get(0)).put("title", "Changed exercise"));
        pinCandidate();
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(Files.readString(report)).contains("retitle demo.01.01.e01: «Primer ejercicio» -> «Changed exercise»");
    }

    @Test void complementsCannotBeClaimedOrChangedByCandidate() throws Exception {
        Files.writeString(active.root.resolve("notes.txt"), "local notes");
        Files.writeString(candidate.root.resolve("notes.txt"), "replacement");
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(Files.readString(report)).contains("unmanaged candidate path notes.txt");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void newResourceDirectoryCannotReplaceAnActiveComplementFile() throws Exception {
        candidate.addResource("demo.new-resource", "new.txt", "text/plain", "new bytes".getBytes(StandardCharsets.UTF_8));
        Files.createDirectory(candidate.root.resolve("demo/resources/local"));
        Files.move(candidate.root.resolve("demo/resources/demo.new-resource"), candidate.root.resolve("demo/resources/local/new.txt"));
        candidate.editManifest(m -> ContentFixture.resource(m, "demo.new-resource")
            .put("localPath", "demo/resources/local/new.txt"));
        pinCandidate();
        assertThat(ContentLoader.loadAll(candidate.root)).hasSize(1);
        Files.writeString(active.root.resolve("demo/resources/local"), "preserved complement");
        directoryCollisionRejected("demo/resources/local");
    }

    @Test void aCourseFromAnotherRepositoryIsCarriedUnchangedAndOnlyThat() throws Exception {
        // H7: each course comes from its own repository. Both copies hold a second course whose commit is not in this
        // source: it cannot be verified here, so it may only travel byte for byte while demo is updated.
        for (ContentFixture copy : List.of(active, candidate)) secondCourse(copy.root, "otro");
        pinCandidate();
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        report = temp.resolve("changed-carried.json");
        Path body = candidate.root.resolve("otro/lessons/otro.01.01.md");
        Files.writeString(body, Files.readString(body).replace("Texto de cuerpo.", "Cambiado a mano."));
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(Files.readString(report)).contains("course otro changed without its source: otro/lessons/otro.01.01.md");
        rejected("apply", "comparison contains conflicts");
    }

    /** A copy of the demo course under another ID, pinned to a commit that this test's source does not have. */
    private static void secondCourse(Path root, String id) throws Exception {
        try (var paths = Files.walk(root.resolve("demo"))) {
            for (Path path : paths.sorted().toList()) {
                Path target = root.resolve(root.relativize(path).toString().replace("demo", id));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else if (path.getFileName().toString().equals("manifest.json")) {
                    var manifest = (tools.jackson.databind.node.ObjectNode) JSON.readTree(
                        Files.readString(path).replace("demo", id));
                    manifest.withObject("/source").put("versionRef", "0123456789abcdef0123456789abcdef01234567");
                    Files.writeString(target, JSON.writeValueAsString(manifest));
                }
                else Files.copy(path, target);
            }
        }
    }

    @Test void newCourseDirectoryCannotReplaceAnActiveComplementFile() throws Exception {
        pinCandidate();
        // A second course needs distinct source identities as well as distinct IDs and local paths.
        var manifest = candidate.load().manifest();
        Map<String, byte[]> files = new TreeMap<>();
        for (var lesson : manifest.lessons()) if (lesson.available()) {
            files.put("extra/" + lesson.sourcePath(), Files.readAllBytes(candidate.root.resolve(lesson.bodyPath())));
        }
        for (var resource : manifest.resources()) {
            files.put("extra/" + resource.sourcePath(), Files.readAllBytes(candidate.root.resolve(resource.localPath())));
        }
        String ref = git("extra fixture\n".getBytes(StandardCharsets.UTF_8), "commit-tree", tree(files)).strip();
        var extra = (tools.jackson.databind.node.ObjectNode) JSON.readTree(candidate.read(ContentFixture.MANIFEST).replace("demo", "extra"));
        for (String field : List.of("lessons", "resources", "sourceToId")) {
            for (var entry : extra.path(field)) {
                ((tools.jackson.databind.node.ObjectNode) entry).put("sourcePath", "extra/" + entry.path("sourcePath").asString());
            }
        }
        extra.withObject("/source").put("versionRef", ref);
        try (var paths = Files.walk(candidate.root.resolve("demo"))) {
            for (Path path : paths.sorted().toList()) {
                Path target = candidate.root.resolve(candidate.root.relativize(path).toString().replace("demo", "extra"));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else if (path.getFileName().toString().equals("manifest.json")) {
                    Files.writeString(target, JSON.writeValueAsString(extra));
                }
                else Files.copy(path, target);
            }
        }
        assertThat(ContentLoader.loadAll(candidate.root)).hasSize(2);
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(JSON.readTree(Files.readString(report)).path("conflicts").toString()).isEqualTo("[]");
        report = temp.resolve("collision.json"); // Use fresh authority; compare never overwrites reports.
        Files.writeString(active.root.resolve("extra"), "preserved complement");
        directoryCollisionRejected("extra");
    }

    void directoryCollisionRejected(String path) throws Exception {
        var activeBefore = snapshot(active.root);
        var candidateBefore = snapshot(candidate.root);
        var sourceBefore = snapshot(source);
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(JSON.readTree(Files.readString(report)).path("conflicts").toString())
            .isEqualTo("[\"candidate claims complement " + path + "\"]");
        assertThat(snapshot(active.root)).isEqualTo(activeBefore);
        assertThat(snapshot(candidate.root)).isEqualTo(candidateBefore);
        assertThat(snapshot(source)).isEqualTo(sourceBefore);
        rejected("apply", "comparison contains conflicts");
    }

    @Test void validCandidateDriftAndBackupCollisionRejectBeforeWrites() throws Exception {
        assertThat(run("compare").code()).isZero();
        Files.createDirectory(active.root.resolveSibling("content.previous"));
        rejected("apply", "installation paths already exist");
        candidate.editManifest(m -> m.withObject("/course").put("title", "Changed"));
        rejected("apply", "report does not match current comparison");
    }

    @Test void reportSymlinkAndPathEscapesRejectBeforeWrites() throws Exception {
        Files.createSymbolicLink(report, temp.resolve("missing"));
        rejected("compare", "symbolic links are not allowed");
    }

    @Test void manifestEscapeRejectsWithoutReadingOutsideRoot() throws Exception {
        candidate.editManifest(m -> ContentFixture.lesson(m, "demo.01.02").put("bodyPath", "../outside.md"));
        rejected("compare", "invalid content: bodyPath of demo.01.02: ../outside.md must be a plain relative path inside the content root");
    }

    @Test void newExerciseKeepsExistingIdentitiesAndAddsNoPersonalState() throws Exception {
        String body = candidate.read(ContentFixture.INTRO);
        String marker = "---\n\n## 6. Chuleta";
        candidate.write(ContentFixture.INTRO, body.replace(marker,
            "#### Ejercicio 3 · New exercise\n\nNew statement.\n\n<details>\n<summary>Solution</summary>\n\nAnswer.\n\n</details>\n\n" + marker));
        candidate.editManifest(m -> ((tools.jackson.databind.node.ArrayNode) ContentFixture.lesson(m, "demo.01.01").path("exercises"))
            .addObject().put("id", "demo.new-exercise").put("lessonId", "demo.01.01").put("title", "New exercise"));
        pinCandidate();
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(Files.readString(report)).contains("addition demo.new-exercise");
        assertThat(run("apply").code()).isZero();
        assertThat(ContentLoader.loadAll(active.root).getFirst().manifest().lessons().getFirst().exercises())
            .extracting(Manifest.Exercise::id).containsExactly("demo.01.01.e01", "demo.01.01.e02", "demo.new-exercise");
    }

    @Test void wrapperHelpAndMissingJarAreOfflineAndDoNotBuild() throws Exception {
        Path wrapper = temp.resolve("content-update");
        Files.copy(Path.of("content-update"), wrapper);
        var before = snapshot(temp);
        Process help = new ProcessBuilder("sh", wrapper.toString(), "--help").start();
        assertThat(new String(help.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(cli("--help").out());
        assertThat(new String(help.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
        assertThat(help.waitFor()).isZero();
        Process missing = new ProcessBuilder("sh", wrapper.toString(), "compare").start();
        assertThat(new String(missing.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEmpty();
        assertThat(new String(missing.getErrorStream().readAllBytes(), StandardCharsets.UTF_8))
            .isEqualTo("content-update: packaged backend jar missing; build first: cd backend && ./mvnw -DskipTests package\n");
        assertThat(missing.waitFor()).isEqualTo(2);
        assertThat(snapshot(temp)).isEqualTo(before);
    }

    @Test void lexicalEscapeAndCandidateClaimOfComplementAreRejected() throws Exception {
        assertThat(cli("compare", "--active", active.root.resolve("..").resolve("content").toString(),
            "--candidate", candidate.root.toString(), "--source", source.toString(), "--report", report.toString()))
            .isEqualTo(new Result(2, "", "content-update: path escapes are not allowed\n"));
        Files.writeString(active.root.resolve("demo/resources/demo.new-resource"), "local complement");
        candidate.addResource("demo.new-resource", "new.txt", "text/plain", "source content".getBytes(StandardCharsets.UTF_8));
        pinCandidate();
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(Files.readString(report)).contains("candidate claims complement demo/resources/demo.new-resource");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void sourceAvailabilityLossAlsoConflictsForAnUnimportedLesson() throws Exception {
        active.editManifest(m -> ContentFixture.lesson(m, "demo.02.01").put("sourceAvailable", true));
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(Files.readString(report)).contains("availability loss demo.02.01");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void verifiesStagesAndCanonicalGeneratedZipWithTheExistingLoader() throws Exception {
        ZipFixture.install(candidate);
        candidate.write("demo/lessons/stage.md", "# Stage\n\nEducational project.\n");
        candidate.editManifest(m -> {
            ((tools.jackson.databind.node.ArrayNode) m.path("projectStages")).addObject()
                .put("id", "demo.stage").put("title", "Stage").put("position", 1)
                .put("sourcePath", "project/stage.md").put("sourceAvailable", true).put("available", true)
                .put("bodyPath", "demo/lessons/stage.md");
            ((tools.jackson.databind.node.ArrayNode) m.path("sourceToId")).addObject()
                .put("sourcePath", "project/stage.md").put("stageId", "demo.stage");
        });
        ZipFixture.refresh(candidate);
        pinCandidate();
        assertThat(run("compare")).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
        assertThat(run("apply").code()).isZero();
        var loaded = ContentLoader.loadAll(active.root).getFirst();
        assertThat(loaded.stages()).containsKey("demo.stage");
        assertThat(Files.readAllBytes(active.root.resolve(ZipFixture.LOCAL)))
            .isEqualTo(Files.readAllBytes(candidate.root.resolve(ZipFixture.LOCAL)));
    }

    @Test void missingImmutableCommitIsAConflict() throws Exception {
        candidate.editManifest(m -> m.withObject("/source").put("versionRef", "1111111111111111111111111111111111111111"));
        assertThat(run("compare")).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
        assertThat(Files.readString(report)).contains("source commit unavailable");
        rejected("apply", "comparison contains conflicts");
    }

    @Test void overlappingRootsAndSymlinkAreRejectedBeforeReading() throws Exception {
        Path original = candidate.root;
        Result overlap = cli("compare", "--active", active.root.toString(), "--candidate", active.root.toString(),
            "--source", source.toString(), "--report", report.toString());
        assertThat(overlap).isEqualTo(new Result(2, "", "content-update: paths must not overlap\n"));
        Files.createSymbolicLink(original.resolve("extra-link"), temp.resolve("missing"));
        rejected("compare", "symbolic links are not allowed");
    }
}
