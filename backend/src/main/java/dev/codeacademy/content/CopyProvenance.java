package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks the controlled copy only. The offline importer must attest source hashes against the pinned Git tree. */
final class CopyProvenance {
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

	private CopyProvenance() {
	}

	static void validate(Manifest manifest, Path root, List<String> problems) {
		if (!Integer.valueOf(2).equals(manifest.schemaVersion())) return;
		Manifest.Provenance provenance = manifest.provenance();
		if (provenance == null || provenance.files() == null || provenance.generated() == null) {
			problems.add("schemaVersion 2 needs provenance.files and provenance.generated");
			return;
		}
		if (!provenance.generated().isEmpty()) problems.add("generated files are not supported yet");
		Map<String, String> expected = new HashMap<>();
		manifest.lessons().stream().filter(Manifest.Lesson::available)
			.forEach(lesson -> expect(expected, lesson.bodyPath(), lesson.sourcePath(), problems));
		manifest.projectStages().stream().filter(Manifest.Stage::available)
			.forEach(stage -> expect(expected, stage.bodyPath(), stage.sourcePath(), problems));
		manifest.resources().forEach(resource -> expect(expected, resource.localPath(), resource.sourcePath(), problems));
		Set<String> seen = new HashSet<>();
		for (Manifest.CopiedFile file : provenance.files()) {
			if (file == null) {
				problems.add("provenance file must not be null");
				continue;
			}
			if (!seen.add(file.localPath())) problems.add("duplicate provenance localPath " + file.localPath());
			if (!ContentPaths.isPlainRelative(file.sourcePath()) || !ContentPaths.isPlainRelative(file.localPath())
					|| !file.sourcePath().equals(expected.get(file.localPath()))) {
				problems.add("provenance path does not match managed copy " + file.localPath());
				continue;
			}
			if (!hash(file.sourceSha256()) || !hash(file.copySha256())) {
				problems.add("provenance needs SHA-256 hashes for " + file.localPath());
				continue;
			}
			// No reproducible transformation is registered in this content-only work unit.
			if (file.transformation() != null) problems.add("unsupported transformation " + file.transformation());
			else if (!file.sourceSha256().equals(file.copySha256())) {
				problems.add("literal copy has different source and copy hashes: " + file.localPath());
			}
			Path copy = ContentPaths.file(root, file.localPath(), "provenance copy", problems);
			if (copy == null) continue;
			try {
				String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(copy)));
				if (!actual.equals(file.copySha256())) problems.add("copySha256 mismatch: " + file.localPath());
			}
			catch (IOException ex) { problems.add("provenance copy is not readable: " + file.localPath()); }
			catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
		}
		if (!seen.equals(expected.keySet())) problems.add("provenance.files must cover exactly the managed copies");
	}

	private static boolean hash(String value) {
		return value != null && SHA256.matcher(value).matches();
	}

	private static void expect(Map<String, String> expected, String local, String source, List<String> problems) {
		if (expected.putIfAbsent(local, source) != null) problems.add("duplicate managed localPath " + local);
	}
}
