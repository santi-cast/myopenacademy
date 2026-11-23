package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * H12, T8 (S9, S10): the starter course v1.0.0 regenerated with today's importer has exactly the bytes academy 1.0.0
 * installed. The oracle is not a copy of the old writer but the real installation: {@code content/inicial/} as 1.0.0
 * wrote it, recorded in {@code content/inicial-v1.0.0.sha256} (SHA-256 and path of every file, sorted by path).
 * <p>
 * The course repository lives outside this one: it is {@code -Dacademia.cursoInicial}, else the environment variable
 * {@code ACADEMIA_CURSO_INICIAL}, else {@code ../Cursos/curso-inicial} from the root of this project, found from the
 * project and not from the working directory (H12, T9, S11). Without it, or without its tag v1.0.0, the test is skipped
 * and says why; it never passes without comparing. With {@code -Dacademia.cursoInicial.required=true} or
 * {@code ACADEMIA_CURSO_INICIAL_REQUIRED=true} the comparison is required: it fails instead of skipping.
 */
class StarterCourseGoldenTest {

	static final String TAG = "v1.0.0";
	static final String GOLDEN = "/content/inicial-v1.0.0.sha256";
	static final String PROPERTY = "academia.cursoInicial";
	static final String VARIABLE = "ACADEMIA_CURSO_INICIAL";
	static final String REQUIRED_PROPERTY = PROPERTY + ".required";
	static final String REQUIRED_VARIABLE = VARIABLE + "_REQUIRED";
	/** The default, relative to the root of this project (the parent of the backend module). */
	static final String DEFAULT = "../Cursos/curso-inicial";
	static final Duration TIMEOUT = Duration.ofSeconds(180);

	@TempDir Path temp;

	@Test
	void theStarterCourseV100IsRegeneratedWithTheBytesAcademy100Installed() throws Exception {
		Path source = source(setting(PROPERTY, VARIABLE), projectRoot());
		boolean required = Boolean.parseBoolean(setting(REQUIRED_PROPERTY, REQUIRED_VARIABLE));
		requireOrSkip(isRepository(source), required, "starter course repository not found at " + source
			+ " (set -D" + PROPERTY + " or " + VARIABLE + "): byte comparison with v1.0.0 not run");
		requireOrSkip(ContentUpdateComparison.git(source, "rev-parse", "--verify", "--quiet", TAG + "^{commit}") != null,
			required, "tag " + TAG + " not found in " + source + ": byte comparison with v1.0.0 not run");
		Path active = Files.createDirectory(temp.resolve("vacia"));
		Path output = temp.resolve("candidata");
		Result result = cli(temp, "generate", "--active", active.toString(), "--source", source.toString(), "--tag", TAG,
			"--output", output.toString(), "--report", temp.resolve("informe.json").toString());
		assertThat(result.code()).as(result.err()).isZero();
		assertThat(result.err()).isEmpty();
		Map<String, String> golden = golden();
		assertThat(golden).hasSize(876).containsKey("manifest.json");
		assertThat(hashes(output.resolve("inicial"))).containsExactlyEntriesOf(golden);
	}

	// H12, T9 (S11): the helpers that find the repository, decide between skipping and failing, and run the CLI.

	@Test
	void theDefaultRepositoryIsFoundFromMavensBasedirNotFromTheWorkingDirectory() throws Exception {
		Path module = Files.createDirectories(temp.resolve("proyecto/backend"));
		Path elsewhere = Files.createDirectories(temp.resolve("otro/target/test-classes"));
		assertThat(projectRoot(module.toString(), elsewhere)).isEqualTo(temp.resolve("proyecto"));
		assertThat(source(null, projectRoot(module.toString(), elsewhere)))
			.isEqualTo(temp.resolve("Cursos/curso-inicial"));
	}

	@Test
	void withoutBasedirTheProjectIsFoundFromTheCompiledTestClasses() throws Exception {
		Path module = Files.createDirectories(temp.resolve("proyecto/backend"));
		Files.writeString(module.resolve("pom.xml"), "<project/>");
		Path classes = Files.createDirectories(module.resolve("target/test-classes"));
		assertThat(projectRoot(null, classes)).isEqualTo(temp.resolve("proyecto"));
		assertThat(projectRoot(" ", classes)).isEqualTo(temp.resolve("proyecto"));
		assertThatThrownBy(() -> projectRoot(null, Files.createDirectories(temp.resolve("suelta/clases"))))
			.hasMessageStartingWith("no pom.xml above ");
	}

	@Test
	void theRealProjectRootIsThisRepository() throws Exception {
		Path root = projectRoot();
		assertThat(root.resolve("backend/pom.xml")).isRegularFile();
		assertThat(root.resolve("backend/src/test/java/dev/codeacademy/content/StarterCourseGoldenTest.java")).isRegularFile();
		assertThat(source(null, root)).isEqualTo(root.getParent().resolve("Cursos/curso-inicial"));
	}

	@Test
	void aConfiguredRepositoryWinsOverTheDefault() throws Exception {
		Path configured = temp.resolve("mi-curso");
		assertThat(source(configured.toString(), temp.resolve("proyecto"))).isEqualTo(configured);
	}

	@Test
	void aRepositoryIsAGitDirectoryAGitFileOrABareRepository() throws Exception {
		Path directory = Files.createDirectories(temp.resolve("normal/.git")).getParent();
		Path worktree = Files.createDirectories(temp.resolve("worktree"));
		Files.writeString(worktree.resolve(".git"), "gitdir: ../normal/.git/worktrees/worktree\n");
		Path bare = Files.createDirectories(temp.resolve("bare.git"));
		Files.writeString(bare.resolve("HEAD"), "ref: refs/heads/main\n");
		Path empty = Files.createDirectories(temp.resolve("vacia"));
		assertThat(isRepository(directory)).isTrue();
		assertThat(isRepository(worktree)).isTrue();
		assertThat(isRepository(bare)).isTrue();
		assertThat(isRepository(empty)).isFalse();
		assertThat(isRepository(temp.resolve("no-existe"))).isFalse();
	}

	@Test
	void aMissingRepositorySkipsUnlessTheComparisonIsRequired() {
		assertThatCode(() -> requireOrSkip(true, true, "x")).doesNotThrowAnyException();
		assertThatCode(() -> requireOrSkip(true, false, "x")).doesNotThrowAnyException();
		assertThatThrownBy(() -> requireOrSkip(false, false, "no está"))
			.isInstanceOf(TestAbortedException.class).hasMessageContaining("no está");
		assertThatThrownBy(() -> requireOrSkip(false, true, "no está"))
			.isInstanceOf(AssertionFailedError.class)
			.hasMessage("no está (required by -D" + REQUIRED_PROPERTY + " or " + REQUIRED_VARIABLE + ")");
	}

	@Test
	void theRequiredSettingReadsThePropertyFirst() {
		String key = "academia.prueba." + System.nanoTime();
		assertThat(setting(key, "ACADEMIA_VARIABLE_QUE_NO_EXISTE_" + System.nanoTime())).isNull();
		System.setProperty(key, "true");
		try {
			assertThat(Boolean.parseBoolean(setting(key, "ACADEMIA_VARIABLE_QUE_NO_EXISTE"))).isTrue();
		}
		finally {
			System.clearProperty(key);
		}
	}

	@Test
	@Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
	void aChildThatFillsBothStreamsCannotHangTheRun() throws Exception {
		// More than any pipe buffer on each stream, the errors first: reading one stream to the end would never return.
		Result result = run("ruidoso", List.of("sh", "-c",
			"head -c 1000000 /dev/zero | tr '\\0' e >&2; head -c 1000000 /dev/zero | tr '\\0' o; exit 7"),
			Duration.ofSeconds(30), temp);
		assertThat(result.code()).isEqualTo(7);
		assertThat(result.out()).hasSize(1_000_000).matches("o+");
		assertThat(result.err()).hasSize(1_000_000).matches("e+");
	}

	@Test
	@Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
	void aChildThatOutlivesTheTimeoutIsStoppedAndFails() throws Exception {
		long start = System.nanoTime();
		assertThatThrownBy(() -> run("dormido", List.of("sh", "-c", "echo empieza; exec sleep 300"), Duration.ofSeconds(1), temp))
			.isInstanceOf(AssertionFailedError.class)
			.hasMessageStartingWith("dormido did not finish in 1 s and was stopped");
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(30));
	}

	/** The value of a system property, else of an environment variable; null when neither is set or both are blank. */
	static String setting(String property, String variable) {
		String value = System.getProperty(property);
		if (value == null || value.isBlank()) value = System.getenv(variable);
		return value == null || value.isBlank() ? null : value;
	}

	/** The configured repository, else the default next to this project. */
	static Path source(String configured, Path projectRoot) {
		if (configured != null) return Path.of(configured).toAbsolutePath().normalize();
		return projectRoot.resolve(DEFAULT).toAbsolutePath().normalize();
	}

	/** The root of this project: the parent of the backend module. */
	static Path projectRoot() throws URISyntaxException {
		Path classes = Path.of(StarterCourseGoldenTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		return projectRoot(System.getProperty("basedir"), classes);
	}

	/**
	 * The backend module is Maven's {@code basedir}; without it (an IDE), the nearest folder with a pom.xml above the
	 * compiled test classes. Never the working directory, which depends on where the run was started.
	 */
	static Path projectRoot(String basedir, Path classes) {
		Path module = null;
		if (basedir != null && !basedir.isBlank()) module = Path.of(basedir);
		else {
			for (Path folder = classes.toAbsolutePath().normalize(); folder != null && module == null; folder = folder.getParent()) {
				if (Files.isRegularFile(folder.resolve("pom.xml"))) module = folder;
			}
			if (module == null) throw new IllegalStateException("no pom.xml above " + classes);
		}
		return module.toAbsolutePath().normalize().getParent();
	}

	/** A Git repository: a work tree whose .git is a folder or a file (worktree, submodule), or a bare repository. */
	static boolean isRepository(Path path) {
		Path git = path.resolve(".git");
		return Files.isDirectory(git) || Files.isRegularFile(git) || Files.isRegularFile(path.resolve("HEAD"));
	}

	/** Goes on when {@code present}; otherwise skips with {@code reason}, or fails with it when the run is required. */
	static void requireOrSkip(boolean present, boolean required, String reason) {
		if (present) return;
		if (required) fail(reason + " (required by -D" + REQUIRED_PROPERTY + " or " + REQUIRED_VARIABLE + ")");
		assumeTrue(false, reason);
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
	static Result cli(Path logs, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of("sh", "-c", "umask 022; exec \"$@\"", "content-import-test",
			Path.of(System.getProperty("java.home"), "bin/java").toString(),
			"-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"), "-Duser.home=" + System.getProperty("user.home"),
			"-cp", System.getProperty("java.class.path"), "dev.codeacademy.content.ContentImportCli"));
		command.addAll(List.of(args));
		return run("content-import", command, TIMEOUT, logs);
	}

	/** Runs {@code command} with its two streams captured; it fails, and stops the process, if it outlives {@code timeout}. */
	static Result run(String name, List<String> command, Duration timeout, Path logs) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(command);
		for (String variable : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) {
			builder.environment().remove(variable);
		}
		// The streams go to files, so the child never blocks on a full pipe and the wait below is the only one.
		Path out = Files.createTempFile(logs, name + "-", ".out");
		Path err = Files.createTempFile(logs, name + "-", ".err");
		builder.redirectOutput(out.toFile()).redirectError(err.toFile());
		Process process = builder.start();
		if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
			process.waitFor(10, TimeUnit.SECONDS);
			fail(name + " did not finish in " + timeout.toSeconds() + " s and was stopped; its output so far:\n"
				+ Files.readString(out) + Files.readString(err));
		}
		return new Result(process.exitValue(), Files.readString(out), Files.readString(err));
	}
}
