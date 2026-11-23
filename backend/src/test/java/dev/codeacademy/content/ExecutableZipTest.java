package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * H12, T7 (S9): the executable bit of a file in a code folder ZIP. It comes only from the file mode in the Git tree of
 * the pinned commit (100755 against 100644), never from the disk: a 100755 entry gets Unix permissions 0755, and every
 * other entry keeps exactly the bytes java.util.zip wrote before, so a course without executables does not change.
 * The kit's example course is committed from a working tree whose disk modes deliberately disagree with the index.
 */
class ExecutableZipTest {

	static final JsonMapper JSON = JsonMapper.builder().build();
	/** A linked code folder of the example course (module ejemplo.ficheros-json). */
	static final String FOLDER = "fase-01-primeros-pasos/codigo/01-ficheros-json/ejemplos/2.2-contar-clientes/";
	/** Executable in the commit, not on disk. */
	static final String WRAPPER = FOLDER + "mvnw";
	/** Executable on disk, not in the commit. */
	static final String SCRIPT = FOLDER + "contar.sh";
	static final int UNIX = 3;

	@TempDir Path temp;
	Path tree;
	Path repository;

	@BeforeEach
	void prepare() throws Exception {
		tree = temp.resolve("arbol");
		Path course = ExampleContent.COURSES.resolve("bueno");
		try (Stream<Path> files = Files.walk(course)) {
			for (Path file : files.toList()) {
				Path copy = tree.resolve(course.relativize(file).toString());
				if (Files.isDirectory(file)) Files.createDirectories(copy);
				else Files.copy(file, copy);
			}
		}
		Files.writeString(tree.resolve(WRAPPER), "#!/bin/sh\necho wrapper\n");
		Files.setPosixFilePermissions(tree.resolve(WRAPPER), PosixFilePermissions.fromString("rw-r--r--"));
		Files.setPosixFilePermissions(tree.resolve(SCRIPT), PosixFilePermissions.fromString("rwxr-xr-x"));
		// The repository is named as the course's repositorio ends, so the list of installed courses can reinstall it.
		repository = temp.resolve("Cursos/curso-ejemplo");
		Files.createDirectories(repository.getParent());
		git(null, "init", "--bare", "--quiet", repository.toString());
	}

	@Test
	void aCommittedExecutableGetsUnixMode0755AndEveryOtherEntryKeepsTodaysBytes() throws Exception {
		Path output = generate(commit(true), "contenido");
		JsonNode manifest = manifest(output);
		assertThat(resource(manifest, WRAPPER).path("executable").asBoolean()).isTrue();
		// Only an executable carries the field: false is never written, so every other entry keeps its manifest bytes.
		for (JsonNode resource : manifest.path("resources")) {
			if (!resource.path("sourcePath").asString().equals(WRAPPER)) assertThat(resource.has("executable")).isFalse();
		}
		byte[] zip = zip(output, manifest);
		Central wrapper = central(zip).get("mvnw");
		assertThat(wrapper.madeBy() >> 8).isEqualTo(UNIX);
		assertThat((wrapper.external() >>> 16) & 0777).isEqualTo(0755);
		assertThat((wrapper.external() >>> 16) & 0170000).isEqualTo(0100000);
		// Apart from the version made by and the external attributes of that one entry, the bytes are today's.
		byte[] today = plain(output, manifest);
		assertThat(zip).hasSameSizeAs(today).isNotEqualTo(today);
		byte[] patched = zip.clone();
		System.arraycopy(today, wrapper.offset() + 4, patched, wrapper.offset() + 4, 2);
		System.arraycopy(today, wrapper.offset() + 38, patched, wrapper.offset() + 38, 4);
		assertThat(patched).isEqualTo(today);
		// The installation validation rebuilds the same bytes from the manifest and accepts the copy.
		assertThat(ContentLoader.loadAll(output)).hasSize(1);
	}

	@Test
	void theModeComesFromTheCommitNeverFromTheDisk() throws Exception {
		String commit = commit(true);
		// On disk it is the other way round: the script has the bit and the wrapper does not.
		assertThat(Files.getPosixFilePermissions(tree.resolve(SCRIPT))).contains(PosixFilePermission.OWNER_EXECUTE);
		assertThat(Files.getPosixFilePermissions(tree.resolve(WRAPPER))).doesNotContain(PosixFilePermission.OWNER_EXECUTE);
		assertThat(git(repository, "ls-tree", commit, "--", SCRIPT)).startsWith("100644 ");
		assertThat(git(repository, "ls-tree", commit, "--", WRAPPER)).startsWith("100755 ");
		Path output = generate(commit, "contenido");
		JsonNode manifest = manifest(output);
		Map<String, Central> entries = central(zip(output, manifest));
		assertThat(entries.get("mvnw").madeBy() >> 8).isEqualTo(UNIX);
		assertThat((entries.get("mvnw").external() >>> 16) & 0777).isEqualTo(0755);
		assertThat(entries.get("contar.sh").madeBy() >> 8).isZero();
		assertThat(entries.get("contar.sh").external()).isZero();
		assertThat(resource(manifest, SCRIPT).has("executable")).isFalse();
	}

	@Test
	void aCourseWithoutExecutablesKeepsExactlyTheBytesOfBefore() throws Exception {
		Path output = generate(commit(false), "contenido");
		JsonNode manifest = manifest(output);
		assertThat(Files.readString(output.resolve("ejemplo/manifest.json"))).doesNotContain("executable");
		for (JsonNode resource : manifest.path("resources")) {
			if (!"zip".equals(resource.path("kind").asString())) continue;
			byte[] zip = Files.readAllBytes(output.resolve(resource.path("localPath").asString()));
			assertThat(zip).as(resource.path("sourcePath").asString()).isEqualTo(plain(output, manifest, resource));
		}
	}

	@Test
	void generatingTwiceGivesTheSameBytes() throws Exception {
		String commit = commit(true);
		Path first = generate(commit, "primero");
		Path second = generate(commit, "segundo");
		assertThat(snapshot(second)).isEqualTo(snapshot(first));
	}

	@Test
	void theLoaderRefusesAnExecutableFlagThatDoesNotMatchTheZip() throws Exception {
		Path output = generate(commit(true), "contenido");
		edit(output, manifest -> ((ObjectNode) resource(manifest, WRAPPER)).remove("executable"));
		String zipId = resource(manifest(output), FOLDER).path("resourceId").asString();
		assertThat(rejected(output)).contains("ZIP does not match registered copied members: " + zipId);
	}

	@Test
	void anExecutableFlagIsOnlyTrueAndOnlyOnAFile() throws Exception {
		Path output = generate(commit(true), "contenido");
		edit(output, manifest -> ((ObjectNode) resource(manifest, WRAPPER)).put("executable", false));
		String wrapperId = resource(manifest(output), WRAPPER).path("resourceId").asString();
		assertThat(rejected(output)).contains("resource " + wrapperId + ": executable must be true or absent");

		Path other = generate(commit(true), "otro");
		edit(other, manifest -> ((ObjectNode) resource(manifest, FOLDER)).put("executable", true));
		String zipId = resource(manifest(other), FOLDER).path("resourceId").asString();
		assertThat(rejected(other)).contains("ZIP resource cannot be executable: " + zipId);
	}

	@Test
	void schemaVersion1DoesNotAllowExecutable() throws Exception {
		ContentFixture fixture = ContentFixture.copyInto(temp.resolve("v1"));
		fixture.editManifest(m -> ((ObjectNode) m.get("resources").get(0)).put("executable", true));
		fixture.rejected("schemaVersion 1 does not allow executable");
	}

	@Test
	void anUpdateThatMakesAFileExecutableIsAChangeAndTheAppliedZipKeepsTheMode() throws Exception {
		Path empty = Files.createDirectory(temp.resolve("vacio"));
		Path active = generate(commit(false), empty, "activa");
		Path candidate = generate(commit(true), active, "candidata");
		Path report = temp.resolve("informe-actualizacion.json");
		assertThat(update("compare", active, candidate, report)).isEqualTo(new Result(0, "Comparison written; conflicts: 0\n", ""));
		String wrapperId = resource(manifest(candidate), WRAPPER).path("resourceId").asString();
		List<String> changes = new ArrayList<>();
		JSON.readTree(report.toFile()).path("changes").forEach(change -> changes.add(change.asString()));
		assertThat(changes).anyMatch(change -> change.startsWith("metadata " + wrapperId + ": ")
			&& change.endsWith(",\"executable\":true}"));
		assertThat(update("apply", active, candidate, report).code()).isZero();
		Map<String, Central> entries = central(zip(active, manifest(active)));
		assertThat((entries.get("mvnw").external() >>> 16) & 0777).isEqualTo(0755);
		assertThat(ContentLoader.loadAll(active)).hasSize(1);
	}

	@Test
	void aCandidateWhoseFlagDisagreesWithItsCommitIsAConflict() throws Exception {
		Path empty = Files.createDirectory(temp.resolve("vacio"));
		String executable = commit(true);
		String plain = commit(false);
		Path active = generate(plain, empty, "activa");
		// A copy made from the commit without the bit that claims to be the one with it: same bytes, other mode.
		Path candidate = generate(plain, active, "candidata");
		edit(candidate, manifest -> ((ObjectNode) manifest.path("source")).put("versionRef", executable));
		String local = resource(manifest(candidate), WRAPPER).path("localPath").asString();
		Path report = temp.resolve("informe-actualizacion.json");
		assertThat(update("compare", active, candidate, report)).isEqualTo(new Result(3, "Comparison written; conflicts: 1\n", ""));
		List<String> conflicts = new ArrayList<>();
		JSON.readTree(report.toFile()).path("conflicts").forEach(conflict -> conflicts.add(conflict.asString()));
		assertThat(conflicts).containsExactly("candidate: source mode differs " + local + " at " + executable);
	}

	@Test
	void reinstallingTheListKeepsTheMode() throws Exception {
		String commit = commit(true);
		git(repository, "tag", "v1.0.0", commit);
		Path empty = Files.createDirectory(temp.resolve("vacio"));
		Path active = temp.resolve("activa");
		var result = ContentImportGenerator.generate(new ContentImportCli.Paths(empty, repository, commit, "v1.0.0",
			active, temp.resolve("informe-activa.json")));
		assertThat(result.findings()).isEmpty();
		Path list = temp.resolve("cursos.json");
		assertThat(ContentCoursesCliTest.run("list", "--active", active.toString(), "--output", list.toString()).code()).isZero();
		Path output = temp.resolve("reinstalada");
		assertThat(ContentCoursesCliTest.run("reinstall", "--list", list.toString(), "--sources",
			repository.getParent().toString(), "--output", output.toString(), "--report", temp.resolve("r.json").toString()))
			.isEqualTo(new ContentCoursesCliTest.Result(0, "Copy written; courses: 1\n", ""));
		assertThat(snapshot(output)).isEqualTo(snapshot(active));
		assertThat((central(zip(output, manifest(output))).get("mvnw").external() >>> 16) & 0777).isEqualTo(0755);
	}

	/** Commits the working tree with the wrapper as 100755 ({@code executable}) or 100644 and the script as 100644. */
	String commit(boolean executable) throws Exception {
		git(repository, "--work-tree=" + tree, "add", "--all");
		git(repository, "--work-tree=" + tree, "update-index", executable ? "--chmod=+x" : "--chmod=-x", "--", WRAPPER);
		git(repository, "--work-tree=" + tree, "update-index", "--chmod=-x", "--", SCRIPT);
		git(repository, "--work-tree=" + tree, "commit", "--quiet", "--allow-empty",
			"--message=" + (executable ? "ejecutable" : "sin ejecutable"));
		return git(repository, "rev-parse", "HEAD").strip();
	}

	Path generate(String commit, String name) throws Exception {
		return generate(commit, Files.createDirectories(temp.resolve("vacio-" + name)), name);
	}

	Path generate(String commit, Path active, String name) throws Exception {
		Path output = temp.resolve(name);
		var result = ContentImportGenerator.generate(new ContentImportCli.Paths(active, repository, commit, output,
			temp.resolve("informe-" + name + ".json")));
		assertThat(result.findings()).isEmpty();
		assertThat(result.written()).isTrue();
		return output;
	}

	static JsonNode manifest(Path root) throws Exception {
		return JSON.readTree(root.resolve("ejemplo/manifest.json").toFile());
	}

	static void edit(Path root, java.util.function.Consumer<ObjectNode> change) throws Exception {
		ObjectNode manifest = (ObjectNode) manifest(root);
		change.accept(manifest);
		Files.writeString(root.resolve("ejemplo/manifest.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
	}

	static String rejected(Path root) {
		try {
			ContentLoader.loadAll(root);
		}
		catch (ContentValidationException ex) {
			return ex.getMessage();
		}
		throw new AssertionError("the copy was accepted");
	}

	static JsonNode resource(JsonNode manifest, String sourcePath) {
		for (JsonNode resource : manifest.path("resources")) {
			if (sourcePath.equals(resource.path("sourcePath").asString())) return resource;
		}
		throw new IllegalArgumentException(sourcePath);
	}

	static byte[] zip(Path root, JsonNode manifest) throws Exception {
		return Files.readAllBytes(root.resolve(resource(manifest, FOLDER).path("localPath").asString()));
	}

	static byte[] plain(Path root, JsonNode manifest) throws Exception {
		return plain(root, manifest, resource(manifest, FOLDER));
	}

	/** The archive as FolderZip wrote it before T7: java.util.zip with fixed metadata and nothing else. */
	static byte[] plain(Path root, JsonNode manifest, JsonNode archive) throws Exception {
		Map<String, JsonNode> byId = new TreeMap<>();
		manifest.path("resources").forEach(resource -> byId.put(resource.path("resourceId").asString(), resource));
		String folder = archive.path("sourcePath").asString();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(out)) {
			for (JsonNode id : archive.path("memberResourceIds")) {
				JsonNode member = byId.get(id.asString());
				byte[] bytes = Files.readAllBytes(root.resolve(member.path("localPath").asString()));
				ZipEntry entry = new ZipEntry(member.path("sourcePath").asString().substring(folder.length()));
				entry.setTimeLocal(LocalDateTime.of(1980, 1, 2, 0, 0));
				entry.setMethod(ZipEntry.STORED);
				entry.setSize(bytes.length);
				CRC32 crc = new CRC32();
				crc.update(bytes);
				entry.setCrc(crc.getValue());
				zip.putNextEntry(entry);
				zip.write(bytes);
				zip.closeEntry();
			}
		}
		return out.toByteArray();
	}

	/** One central directory record: where it starts, its version made by and its external attributes. */
	record Central(int offset, int madeBy, long external) {
	}

	/** The central directory records by entry name, read straight from the bytes (the archives have no comment). */
	static Map<String, Central> central(byte[] zip) {
		ByteBuffer bytes = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
		int end = zip.length - 22;
		assertThat(bytes.getInt(end)).isEqualTo(0x06054b50);
		int count = bytes.getShort(end + 10) & 0xffff;
		int position = bytes.getInt(end + 16);
		Map<String, Central> records = new TreeMap<>();
		for (int i = 0; i < count; i++) {
			assertThat(bytes.getInt(position)).isEqualTo(0x02014b50);
			int name = bytes.getShort(position + 28) & 0xffff;
			int extra = bytes.getShort(position + 30) & 0xffff;
			int comment = bytes.getShort(position + 32) & 0xffff;
			records.put(new String(zip, position + 46, name, StandardCharsets.UTF_8),
				new Central(position, bytes.getShort(position + 4) & 0xffff, bytes.getInt(position + 38) & 0xffffffffL));
			position += 46 + name + extra + comment;
		}
		return records;
	}

	static Map<String, String> snapshot(Path root) throws Exception {
		return ContentCoursesCliTest.snapshot(root);
	}

	static String git(Path repository, String... args) throws Exception {
		return ContentCoursesCliTest.git(repository, args);
	}

	record Result(int code, String out, String err) {
	}

	/** content-update in its own process, as the maintainer runs it. */
	Result update(String operation, Path active, Path candidate, Path report) throws Exception {
		Path source = repository;
		List<String> command = new ArrayList<>(List.of("sh", "-c", "umask 022; exec \"$@\"", "content-update-test",
			Path.of(System.getProperty("java.home"), "bin/java").toString(),
			"-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-Duser.home=" + System.getProperty("user.home"),
			"-cp", System.getProperty("java.class.path"), "dev.codeacademy.content.ContentUpdateCli", operation,
			"--active", active.toString(), "--candidate", candidate.toString(), "--source", source.toString(),
			"--report", report.toString()));
		ProcessBuilder builder = new ProcessBuilder(command);
		for (String variable : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
			builder.environment().remove(variable);
		}
		Process process = builder.start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
		return new Result(process.exitValue(), out, err);
	}
}
