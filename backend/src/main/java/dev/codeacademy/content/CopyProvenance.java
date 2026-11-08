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
		Map<String, Manifest.Resource> generated = new HashMap<>();
		Map<String, String> expected = new HashMap<>();
		manifest.lessons().stream().filter(Manifest.Lesson::available)
			.forEach(lesson -> expect(expected, lesson.bodyPath(), lesson.sourcePath(), problems));
		manifest.projectStages().stream().filter(Manifest.Stage::available)
			.forEach(stage -> expect(expected, stage.bodyPath(), stage.sourcePath(), problems));
		manifest.resources().forEach(resource -> {
			if ("zip".equals(resource.kind())) {
				if (generated.putIfAbsent(resource.localPath(), resource) != null) problems.add("duplicate managed localPath " + resource.localPath());
			}
			else expect(expected, resource.localPath(), resource.sourcePath(), problems);
		});
		generated.keySet().stream().filter(expected::containsKey).forEach(path -> problems.add("duplicate managed localPath " + path));
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
			verifyHash(copy, file.copySha256(), file.localPath(), problems);
		}
		if (!seen.equals(expected.keySet())) problems.add("provenance.files must cover exactly the managed copies");
		Set<String> generatedSeen = new HashSet<>();
		Map<String, Manifest.Resource> registered = FolderZip.resources(manifest);
		for (Manifest.GeneratedFile file : provenance.generated()) {
			if (file == null) {
				problems.add("generated provenance must not be null");
				continue;
			}
			if (!generatedSeen.add(file.localPath()) || seen.contains(file.localPath())) {
				problems.add("duplicate provenance localPath " + file.localPath());
			}
			Manifest.Resource archive = generated.get(file.localPath());
			if (archive == null || !archive.memberResourceIds().equals(file.inputResourceIds()) || !hash(file.copySha256())) {
				problems.add("generated provenance must match ZIP inputs and SHA-256: " + file.localPath());
				continue;
			}
			// Inspect bytes before hashing: never trust a matching attacker-supplied digest as archive validation.
			int before = problems.size();
			FolderZip.validate(registered, root, archive, problems);
			if (problems.size() != before) continue;
			Path copy = ContentPaths.file(root, file.localPath(), "generated copy", problems);
			if (copy != null) verifyHash(copy, file.copySha256(), file.localPath(), problems);
		}
		if (!generatedSeen.equals(generated.keySet())) problems.add("provenance.generated must cover exactly the ZIP resources");
	}

	private static void verifyHash(Path path, String hash, String local, List<String> problems) {
		try (var input = Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var checked = new java.security.DigestInputStream(input, digest)) {
				checked.transferTo(java.io.OutputStream.nullOutputStream());
			}
			if (!HexFormat.of().formatHex(digest.digest()).equals(hash)) problems.add("copySha256 mismatch: " + local);
		}
		catch (IOException ex) { problems.add("provenance copy is not readable: " + local); }
		catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
	}

	private static boolean hash(String value) {
		return value != null && SHA256.matcher(value).matches();
	}

	private static void expect(Map<String, String> expected, String local, String source, List<String> problems) {
		if (expected.putIfAbsent(local, source) != null) problems.add("duplicate managed localPath " + local);
	}
}
