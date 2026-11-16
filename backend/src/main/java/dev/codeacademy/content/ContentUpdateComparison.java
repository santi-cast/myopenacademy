package dev.codeacademy.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import tools.jackson.databind.json.JsonMapper;

/** Pure comparison of prepared copies, using the runtime loader and immutable Git blobs. */
final class ContentUpdateComparison {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Copy base;
    private final Copy candidate;
    private final List<String> conflicts;
    private final String report;

    private ContentUpdateComparison(Copy base, Copy candidate, List<String> conflicts, String report) {
        this.base = base;
        this.candidate = candidate;
        this.conflicts = List.copyOf(conflicts);
        this.report = report;
    }

    String report() { return report; }
    List<String> conflicts() { return conflicts; }
    Copy base() { return base; }
    Copy candidate() { return candidate; }

    static ContentUpdateComparison compare(ContentUpdateCli.Paths paths) throws IOException {
        Copy base = read(paths.active());
        Copy next = read(paths.candidate());
        List<String> conflicts = new ArrayList<>();
        // Every course whose commit is in this source is verified against it, and so is every course the update
        // changes. A course from another repository can only be carried byte for byte: nothing here can verify it.
        Set<String> updated = updatedCourses(base, next, conflicts);
        verifySource(base, paths.source(), "active", updated, conflicts);
        verifySource(next, paths.source(), "candidate", updated, conflicts);
        List<String> changes = new ArrayList<>();
        for (var entry : base.entities().entrySet()) {
            String id = entry.getKey();
            Entity old = entry.getValue();
            Entity replacement = next.entities().get(id);
            if (replacement == null) { conflicts.add("removal " + id); continue; }
            if (!old.kind().equals(replacement.kind())) conflicts.add("identity kind change " + id);
            // The ID is the identity (curso.json): a new title is a change, not a conflict (FORMATO.md 11.2).
            if (!old.title().equals(replacement.title())) {
                changes.add("retitle " + id + ": «" + old.title() + "» -> «" + replacement.title() + "»");
            }
            if (!old.owner().equals(replacement.owner())) conflicts.add("owner move " + id);
            if ((old.available() && !replacement.available()) || (old.sourceAvailable() && !replacement.sourceAvailable())) {
                conflicts.add("availability loss " + id);
            }
            if (!old.metadata().equals(replacement.metadata())) {
                changes.add("metadata " + id + ": " + old.metadata() + " -> " + replacement.metadata());
            }
        }
        for (var entry : next.entities().entrySet()) {
            if (!base.entities().containsKey(entry.getKey())) changes.add("addition " + entry.getKey() + ": " + entry.getValue().metadata());
        }
        // A new ID at a previously associated source (or exercise title/owner) is not an identity migration.
        Map<String, String> oldIdentity = identities(base, conflicts);
        Map<String, String> newIdentity = identities(next, conflicts);
        oldIdentity.forEach((key, id) -> {
            if (newIdentity.containsKey(key) && !id.equals(newIdentity.get(key))) conflicts.add("ambiguous identity " + id + " -> " + newIdentity.get(key));
        });
        for (var entry : next.files().entrySet()) {
            String path = entry.getKey();
            if (entry.getValue().equals("directory")) {
                if (base.files().containsKey(path) && !base.files().get(path).equals("directory")
                    && !base.managed().containsKey(path) && !base.manifests().contains(path)) {
                    conflicts.add("candidate claims complement " + path);
                }
                continue;
            }
            if (!next.managed().containsKey(path) && !next.manifests().contains(path)) {
                if (!entry.getValue().equals(base.files().get(path)) || base.managed().containsKey(path)
                    || base.manifests().contains(path)) conflicts.add("unmanaged candidate path " + path);
            }
            if ((next.managed().containsKey(path) || next.manifests().contains(path))
                && base.files().containsKey(path) && !base.managed().containsKey(path) && !base.manifests().contains(path)) {
                conflicts.add("candidate claims complement " + path);
            }
        }
        Set<String> all = new TreeSet<>(base.managed().keySet());
        all.addAll(next.managed().keySet());
        for (String path : all) if (!Objects.equals(base.files().get(path), next.files().get(path))) {
            changes.add("bytes " + path + ": " + base.files().get(path) + " -> " + next.files().get(path));
        }
        // Include complete manifests: ordering, mappings and provenance are visible even when no entity changes.
        for (String path : next.manifests()) if (!Objects.equals(base.files().get(path), next.files().get(path))) {
            changes.add("manifest metadata " + path + ": " + Files.readString(paths.candidate().resolve(path)));
        }
        changes.sort(String::compareTo);
        conflicts.sort(String::compareTo);
        Map<String, Object> data = new TreeMap<>();
        data.put("format", 1);
        data.put("active", paths.active().toString());
        data.put("candidate", paths.candidate().toString());
        data.put("source", paths.source().toString());
        data.put("report", paths.report().toString());
        data.put("baseFingerprint", fingerprint(base.files()));
        data.put("candidateFingerprint", fingerprint(next.files()));
        data.put("baseRefs", base.refs());
        data.put("candidateRefs", next.refs());
        data.put("baseVersions", versions(base));
        data.put("candidateVersions", versions(next));
        data.put("changes", changes);
        data.put("conflicts", conflicts);
        return new ContentUpdateComparison(base, next, conflicts, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n");
    }

    /** Per course, the curso.json version and the tag it was installed from, when the copy records them (H7, A5). */
    static Map<String, Map<String, String>> versions(Copy copy) {
        Map<String, Map<String, String>> versions = new TreeMap<>();
        for (CourseContent course : copy.courses()) {
            Manifest.Source source = course.manifest().source();
            Map<String, String> version = new TreeMap<>();
            if (source.courseVersion() != null) version.put("version", source.courseVersion());
            if (source.versionTag() != null) version.put("tag", source.versionTag());
            versions.put(course.manifest().course().id(), version);
        }
        return versions;
    }

    static Copy read(Path root) throws IOException {
        Map<String, String> files = snapshot(root);
        List<CourseContent> courses = ContentLoader.loadInstalled(root);
        Set<String> manifests = new TreeSet<>();
        try (var dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> manifests.add(root.relativize(dir.resolve("manifest.json")).toString()));
        }
        Map<String, String> managed = new TreeMap<>();
        Map<String, Entity> entities = new TreeMap<>();
        Map<String, String> refs = new TreeMap<>();
        for (CourseContent course : courses) {
            Manifest m = course.manifest();
            String owner = m.course().id();
            refs.put(owner, m.source().versionRef());
            add(entities, owner, "course", m.course().title(), "", "", true, m.course());
            for (var phase : m.phases()) add(entities, phase.id(), "phase", phase.title(), owner, "", true, phase);
            for (var lesson : m.lessons()) {
                safeSource(lesson.sourcePath());
                add(entities, lesson.id(), "lesson", lesson.title(), owner, lesson.sourcePath(), lesson.available(), lesson);
                if (lesson.available()) managed(managed, lesson.bodyPath(), lesson.sourcePath());
                for (var exercise : lesson.exercises()) {
                    add(entities, exercise.id(), "exercise", exercise.title(), lesson.id(), lesson.id() + "#" + exercise.title(), true, exercise);
                }
            }
            if (m.projectStages() != null) for (var stage : m.projectStages()) {
                safeSource(stage.sourcePath());
                add(entities, stage.id(), "stage", stage.title(), owner, stage.sourcePath(), stage.available(), stage);
                if (stage.available()) managed(managed, stage.bodyPath(), stage.sourcePath());
            }
            for (var resource : m.resources()) {
                String sourcePath = resource.sourcePath();
                boolean zip = "zip".equals(resource.kind());
                safeSource(zip ? sourcePath.substring(0, sourcePath.length() - 1) : sourcePath);
                add(entities, resource.resourceId(), "resource", "", resource.lessonId() == null ? resource.stageId() : resource.lessonId(),
                    sourcePath, true, resource);
                managed(managed, resource.localPath(), zip ? null : sourcePath);
            }
            for (var link : m.sourceToId()) {
                String path = link.sourcePath();
                safeSource(path.endsWith("/") ? path.substring(0, path.length() - 1) : path);
            }
        }
        for (String manifest : manifests) if (managed.containsKey(manifest)) throw new IllegalArgumentException("managed file overlaps manifest");
        return new Copy(root, courses, files, managed, manifests, entities, refs);
    }

    private static void managed(Map<String, String> paths, String local, String source) {
        if (paths.containsKey(local)) throw new IllegalArgumentException("duplicate managed localPath " + local);
        paths.put(local, source);
    }

    private static void add(Map<String, Entity> entities, String id, String kind, String title, String owner,
                            String source, boolean available, Object metadata) {
        boolean sourceAvailable = metadata instanceof Manifest.Lesson lesson ? lesson.sourceAvailable()
            : metadata instanceof Manifest.Stage stage ? stage.sourceAvailable() : available;
        entities.put(id, new Entity(kind, title, owner, source, available, sourceAvailable, JSON.writeValueAsString(metadata)));
    }

    /** Kind, owner and source path: a lesson and a stage may own the same path without becoming ambiguous. */
    private static Map<String, String> identities(Copy copy, List<String> conflicts) {
        Map<String, String> result = new TreeMap<>();
        copy.entities().forEach((id, entity) -> {
            if (!entity.source().isEmpty()) {
                String key = entity.kind() + ":" + entity.owner() + ":" + entity.source();
                if (result.putIfAbsent(key, id) != null) conflicts.add("ambiguous identity " + id);
            }
        });
        return result;
    }

    /** Fingerprints every file and empty directory without following links or opening protected names. */
    static Map<String, String> snapshot(Path root) throws IOException {
        ContentUpdateCli.checked(root);
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            // Validate all names and types before reading any bytes.
            List<Path> entries = paths.sorted().toList();
            for (Path path : entries) {
                if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("symbolic links are not allowed");
                String local = root.relativize(path).toString();
                if (!local.isEmpty()) safeNames(local);
                if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("only directories and regular files are allowed");
                }
            }
            for (Path path : entries) {
                String local = root.relativize(path).toString();
                if (!local.isEmpty()) result.put(local, Files.isDirectory(path) ? "directory" : sha(Files.readAllBytes(path)));
            }
        }
        return result;
    }

    // Package-private so the offline generator reuses the same protected-name and Git-object rules (visibility only).
    static void safeNames(String path) {
        if (protectedName(path)) throw new IllegalArgumentException("protected paths are not allowed");
    }

    /**
     * Whether any segment is .git, .env or .env.*: the names the protection policy never reads or copies. The one
     * exception is the exact name .env.example, the standard template without secrets that courses teach (author's
     * decision, 16 November 2026).
     */
    static boolean protectedName(String path) {
        for (String segment : path.split("/")) {
            if (segment.equals(".git") || segment.equals(".env")
                    || (segment.startsWith(".env.") && !segment.equals(".env.example"))) return true;
        }
        return false;
    }

    static void safeSource(String path) {
        if (!ContentPaths.isPlainRelative(path)) throw new IllegalArgumentException("invalid source path " + path);
        safeNames(path);
    }

    /**
     * The courses this update changes: those whose manifest differs between both copies, or that only one of them has.
     * A course with the same manifest in both must also keep every file; otherwise it changed without a source.
     */
    private static Set<String> updatedCourses(Copy base, Copy next, List<String> conflicts) {
        Set<String> ids = new TreeSet<>();
        base.courses().forEach(course -> ids.add(course.manifest().course().id()));
        next.courses().forEach(course -> ids.add(course.manifest().course().id()));
        Set<String> updated = new TreeSet<>();
        for (String id : ids) {
            String manifest = id + "/manifest.json";
            if (!Objects.equals(base.files().get(manifest), next.files().get(manifest)) || base.files().get(manifest) == null) {
                updated.add(id);
                continue;
            }
            Set<String> paths = new TreeSet<>(base.files().keySet());
            paths.addAll(next.files().keySet());
            for (String path : paths) {
                if (path.startsWith(id + "/") && !Objects.equals(base.files().get(path), next.files().get(path))) {
                    conflicts.add("course " + id + " changed without its source: " + path);
                }
            }
        }
        return updated;
    }

    private static void verifySource(Copy copy, Path source, String label, Set<String> updated, List<String> conflicts)
            throws IOException {
        for (CourseContent course : copy.courses()) {
            Manifest m = course.manifest();
            String ref = m.source().versionRef();
            byte[] type = git(source, "cat-file", "-t", ref);
            boolean fromThisSource = type != null && new String(type, StandardCharsets.UTF_8).strip().equals("commit");
            // A course from another repository cannot be verified here: it may only be carried unchanged.
            if (!fromThisSource && !updated.contains(m.course().id())) continue;
            if (type == null || !new String(type, StandardCharsets.UTF_8).strip().equals("commit")) {
                conflicts.add(label + ": source commit unavailable " + ref);
                continue;
            }
            Map<String, String> sources = new TreeMap<>();
            m.lessons().stream().filter(Manifest.Lesson::available).forEach(l -> sources.put(l.bodyPath(), l.sourcePath()));
            if (m.projectStages() != null) m.projectStages().stream().filter(Manifest.Stage::available)
                .forEach(s -> sources.put(s.bodyPath(), s.sourcePath()));
            m.resources().stream().filter(r -> !"zip".equals(r.kind())).forEach(r -> sources.put(r.localPath(), r.sourcePath()));
            for (var file : sources.entrySet()) {
                byte[] listing = git(source, "ls-tree", "-z", "--full-tree", ref, "--", file.getValue());
                String entry = listing == null ? "" : new String(listing, StandardCharsets.UTF_8);
                if (!(entry.startsWith("100644 blob ") || entry.startsWith("100755 blob "))
                    || entry.chars().filter(c -> c == 0).count() != 1) {
                    conflicts.add(label + ": source regular file unavailable " + file.getValue());
                    continue;
                }
                String blob = entry.substring(12, entry.indexOf('\t'));
                byte[] bytes = git(source, "cat-file", "blob", blob);
                if (bytes == null || !sha(bytes).equals(copy.files().get(file.getKey()))) {
                    conflicts.add(label + ": source bytes differ " + file.getKey() + " at " + ref);
                }
            }
        }
    }

    static byte[] git(Path source, String... args) throws IOException {
        Process process = gitProcess(source, args).start();
        process.getOutputStream().close();
        byte[] output = process.getInputStream().readAllBytes();
        try { return process.waitFor() == 0 ? output : null; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("source verification interrupted", ex);
        }
    }

    /**
     * The one set of rules for every Git process that reads a source repository: no inherited {@code GIT_*} override
     * may select another object database or read user configuration, and Git diagnostics never reach the caller.
     */
    static ProcessBuilder gitProcess(Path source, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "--no-replace-objects", "--no-pager", "-C", source.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().keySet().removeIf(key -> key.startsWith("GIT_"));
        builder.environment().put("GIT_LITERAL_PATHSPECS", "1");
        builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null");
        return builder;
    }

    static String fingerprint(Map<String, String> files) { return sha(JSON.writeValueAsBytes(files)); }
    static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    record Copy(Path root, List<CourseContent> courses, Map<String, String> files, Map<String, String> managed,
                Set<String> manifests, Map<String, Entity> entities, Map<String, String> refs) {}
    record Entity(String kind, String title, String owner, String source, boolean available, boolean sourceAvailable, String metadata) {}
}
