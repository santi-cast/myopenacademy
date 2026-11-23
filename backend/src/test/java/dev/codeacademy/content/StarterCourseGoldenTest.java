package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H12, T8 (S9, S10): the starter course v1.0.0 regenerated with today's importer has exactly the bytes academy 1.0.0
 * installed. The oracle is not a copy of the old writer but the real installation: {@code content/inicial/} as 1.0.0
 * wrote it, recorded in {@code content/inicial-v1.0.0.sha256} (SHA-256 and path of every file, sorted by path).
 * The course repository lives outside this one: it is {@code -Dacademia.cursoInicial}, else the environment variable
 * {@code ACADEMIA_CURSO_INICIAL}, else {@code ../../Cursos/curso-inicial} from {@code backend/}. Without it, or without
 * its tag v1.0.0, the test is skipped and says why; it never passes without comparing.
 */
class StarterCourseGoldenTest {

	static final String TAG = "v1.0.0";
	static final String GOLDEN = "/content/inicial-v1.0.0.sha256";

	@TempDir Path temp;

	@Test
	void theStarterCourseV100IsRegeneratedWithTheBytesAcademy100Installed() throws Exception {
		Path source = source();
		assumeTrue(Files.isDirectory(source.resolve(".git")) || Files.isRegularFile(source.resolve("HEAD")),
			"starter course repository not found at " + source
				+ " (set -Dacademia.cursoInicial or ACADEMIA_CURSO_INICIAL): byte comparison with v1.0.0 not run");
		assumeTrue(ContentUpdateComparison.git(source, "rev-parse", "--verify", "--quiet", TAG + "^{commit}") != null,
			"tag " + TAG + " not found in " + source + ": byte comparison with v1.0.0 not run");
		Path active = Files.createDirectory(temp.resolve("vacia"));
		Path output = temp.resolve("candidata");
		Result result = cli("generate", "--active", active.toString(), "--source", source.toString(), "--tag", TAG,
			"--output", output.toString(), "--report", temp.resolve("informe.json").toString());
		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.err()).isEmpty();
		Map<String, String> golden = golden();
		assertThat(golden).hasSize(876).containsKey("manifest.json");
		assertThat(hashes(output.resolve("inicial"))).containsExactlyEntriesOf(golden);
	}

	static Path source() {
		String configured = System.getProperty("academia.cursoInicial");
		if (configured == null || configured.isBlank()) configured = System.getenv("ACADEMIA_CURSO_INICIAL");
		if (configured == null || configured.isBlank()) configured = "../../Cursos/curso-inicial";
		return Path.of(configured).toAbsolutePath().normalize();
	}

	/** The recorded hashes by path, in the order of the file (sorted by path). */
	static Map<String, String> golden() throws Exception {
		Map<String, String> result = new TreeMap<>();
		try (var input = StarterCourseGoldenTest.class.getResourceAsStream(GOLDEN)) {
			assertThat(input).as(GOLDEN).isNotNull();
			for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				if (line.isEmpty()) continue;
				assertThat(line).matches("[0-9a-f]{64}  .+");
				assertThat(result.put(line.substring(66), line.substring(0, 64))).isNull();
			}
		}
		return result;
	}

	/** SHA-256 of every file under the course folder, by path relative to it. */
	static Map<String, String> hashes(Path course) throws Exception {
		Map<String, String> result = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(course)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				result.put(course.relativize(path).toString(), ContentUpdateComparison.sha(Files.readAllBytes(path)));
			}
		}
		return result;
	}

	record Result(int code, String out, String err) {
	}

	/** content-import in its own process, as the maintainer runs it. */
	static Result cli(String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of("sh", "-c", "umask 022; exec \"$@\"", "content-import-test",
			Path.of(System.getProperty("java.home"), "bin/java").toString(),
			"-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-Duser.home=" + System.getProperty("user.home"),
			"-cp", System.getProperty("java.class.path"), "dev.codeacademy.content.ContentImportCli"));
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command);
		for (String variable : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
			builder.environment().remove(variable);
		}
		Process process = builder.start();
		String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor(180, TimeUnit.SECONDS)).isTrue();
		return new Result(process.exitValue(), out, err);
	}
}
