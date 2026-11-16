package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * H7, T7: the list of installed courses and its reinstallation. The kit's two example courses are committed, tagged
 * with their version, into repositories named like the end of their address (curso-ejemplo, ejemplo-destino), and
 * installed by tag with the real importer, as an academy does.
 */
class ContentCoursesCliTest {

	static final JsonMapper JSON = JsonMapper.builder().build();

	@TempDir Path temp;
	Path sources;
	Path active;
	Map<String, String> commits = new TreeMap<>();

	@BeforeEach
	void install() throws Exception {
		sources = Files.createDirectory(temp.resolve("Cursos"));
		Path previous = Files.createDirectory(temp.resolve("vacio"));
		int i = 0;
		for (String[] course : List.of(new String[] { "bueno", "curso-ejemplo" },
				new String[] { "ejemplo-destino", "ejemplo-destino" })) {
			Path repository = sources.resolve(course[1]);
			git(null, "init", "--bare", "--quiet", repository.toString());
			Path folder = ExampleContent.COURSES.resolve(course[0]).toAbsolutePath();
			git(repository, "--work-tree=" + folder, "add", "--all");
			git(repository, "--work-tree=" + folder, "commit", "--quiet", "--message=ejemplo");
			String commit = git(repository, "rev-parse", "HEAD").strip();
			git(repository, "tag", "v1.0.0");
			commits.put(course[1], commit);
			Path next = temp.resolve("copia-" + ++i);
			var result = ContentImportGenerator.generate(new ContentImportCli.Paths(previous, repository,
				ContentImportSource.resolveTag(repository, "v1.0.0"), "v1.0.0", next, temp.resolve("informe-" + i + ".json")));
			assertThat(result.findings()).isEmpty();
			previous = next;
		}
		active = previous;
	}

	@Test
	void theListRecordsEachCourseWithItsRepositoryVersionTagAndCommit() throws Exception {
		Path list = temp.resolve("cursos.json");
		assertThat(run("list", "--active", active.toString(), "--output", list.toString()))
			.isEqualTo(new Result(0, "List written; courses: 2\n", ""));
		JsonNode data = JSON.readTree(list.toFile());
		assertThat(data.path("format").asInt()).isEqualTo(1);
		JsonNode ejemplo = data.path("courses").get(0);
		assertThat(ejemplo.path("id").asString()).isEqualTo("ejemplo");
		assertThat(ejemplo.path("repository").asString()).isEqualTo("https://github.com/myopenacademy/curso-ejemplo");
		assertThat(ejemplo.path("version").asString()).isEqualTo("1.0.0");
		assertThat(ejemplo.path("tag").asString()).isEqualTo("v1.0.0");
		assertThat(ejemplo.path("commit").asString()).isEqualTo(commits.get("curso-ejemplo"));
		assertThat(data.path("courses").get(1).path("id").asString()).isEqualTo("ejemplo-destino");
		// Written again, it replaces the file (kept in Git and updated with every installation).
		assertThat(run("list", "--active", active.toString(), "--output", list.toString()).code()).isZero();
	}

	@Test
	void reinstallingTheListGivesTheSameCopyByteForByte() throws Exception {
		Path list = temp.resolve("cursos.json");
		run("list", "--active", active.toString(), "--output", list.toString());
		Path output = temp.resolve("reinstalada");
		Path report = temp.resolve("reinstalacion.json");
		// The sources as the common plan has them: relative and with .. (../Cursos).
		Path relative = Path.of("").toAbsolutePath().relativize(sources);
		assertThat(run("reinstall", "--list", list.toString(), "--sources", relative.toString(), "--output",
			output.toString(), "--report", report.toString())).isEqualTo(new Result(0, "Copy written; courses: 2\n", ""));
		assertThat(snapshot(output)).isEqualTo(snapshot(active));
		assertThat(JSON.readTree(report.toFile()).path("written").asBoolean()).isTrue();
		assertThat(Files.list(temp).map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith(".reinstall-"));
	}

	@Test
	void aTagThatMovedOrAMissingRepositoryStopsTheReinstallation() throws Exception {
		Path list = temp.resolve("cursos.json");
		run("list", "--active", active.toString(), "--output", list.toString());
		Path repository = sources.resolve("ejemplo-destino");
		String tree = git(repository, "rev-parse", "HEAD^{tree}").strip();
		String other = git(repository, "commit-tree", tree, "-p", "HEAD", "-m", "otra").strip();
		git(repository, "tag", "-f", "v1.0.0", other);
		Path output = temp.resolve("reinstalada");
		Path report = temp.resolve("movida.json");
		assertThat(run("reinstall", "--list", list.toString(), "--sources", sources.toString(), "--output",
			output.toString(), "--report", report.toString()).code()).isEqualTo(3);
		assertThat(report(report)).contains("tag v1.0.0 now names " + other + ", the list says " + commits.get("ejemplo-destino"));
		assertThat(Files.exists(output)).isFalse();

		git(null, "-C", repository.toString(), "tag", "-d", "v1.0.0");
		Path deleted = temp.resolve("borrada.json");
		assertThat(run("reinstall", "--list", list.toString(), "--sources", sources.toString(), "--output",
			output.toString(), "--report", deleted.toString()).code()).isEqualTo(3);
		assertThat(report(deleted)).contains("source tag unavailable v1.0.0");

		Files.move(repository, temp.resolve("apartado"));
		Path missing = temp.resolve("sin-repositorio.json");
		assertThat(run("reinstall", "--list", list.toString(), "--sources", sources.toString(), "--output",
			output.toString(), "--report", missing.toString()).code()).isEqualTo(3);
		assertThat(report(missing)).contains("repository not found " + repository);
		assertThat(Files.exists(output)).isFalse();
	}

	@Test
	void helpAndInvalidArgumentsAreExact() throws Exception {
		assertThat(run("--help")).isEqualTo(new Result(0, ContentCoursesCli.HELP, ""));
		String invalid = "content-courses: invalid arguments; use --help\n";
		assertThat(run("list")).isEqualTo(new Result(2, "", invalid));
		assertThat(run("list", "--active", active.toString(), "--report", "x")).isEqualTo(new Result(2, "", invalid));
		assertThat(run("reinstall", "--list", "a", "--sources", "b", "--output", "c", "--output", "d"))
			.isEqualTo(new Result(2, "", invalid));
		Path list = temp.resolve("cursos.json");
		run("list", "--active", active.toString(), "--output", list.toString());
		assertThat(run("reinstall", "--list", list.toString(), "--sources", sources.toString(), "--output",
			active.toString(), "--report", temp.resolve("r.json").toString()))
			.isEqualTo(new Result(2, "", "content-courses: output must not exist\n"));
	}

	record Result(int code, String out, String err) {
	}

	static Result run(String... args) throws Exception {
		PrintStream out = System.out;
		PrintStream err = System.err;
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();
		int code;
		try {
			System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
			try {
				code = ContentCoursesCli.execute(args);
			}
			catch (IllegalArgumentException ex) {
				System.err.println("content-courses: " + ex.getMessage());
				code = 2;
			}
		}
		finally {
			System.setOut(out);
			System.setErr(err);
		}
		return new Result(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
	}

	static String report(Path report) throws Exception {
		return Files.readString(report);
	}

	static Map<String, String> snapshot(Path root) throws Exception {
		Map<String, String> result = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				result.put(root.relativize(path).toString(),
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
			}
		}
		return result;
	}

	static String git(Path repository, String... args) throws Exception {
		List<String> command = new ArrayList<>(List.of("git"));
		if (repository != null) command.add("--git-dir=" + repository);
		command.addAll(List.of(args));
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().putAll(Map.of("GIT_CONFIG_GLOBAL", "/dev/null", "GIT_CONFIG_NOSYSTEM", "1",
			"GIT_AUTHOR_NAME", "Fixture", "GIT_AUTHOR_EMAIL", "fixture@example.invalid",
			"GIT_COMMITTER_NAME", "Fixture", "GIT_COMMITTER_EMAIL", "fixture@example.invalid",
			"GIT_AUTHOR_DATE", "2026-01-01T00:00:00 +0000", "GIT_COMMITTER_DATE", "2026-01-01T00:00:00 +0000"));
		builder.redirectErrorStream(true);
		Process process = builder.start();
		process.getOutputStream().close();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertThat(process.waitFor()).as(output).isZero();
		return output;
	}
}
