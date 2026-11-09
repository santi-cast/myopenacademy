package dev.codeacademy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.catalog.CatalogConflictException;
import dev.codeacademy.content.ExampleContent;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * S5/S6 (T6), on the kit's example course: a real update of an installed course keeps two learners' progress, with a
 * loopback database of its own. Both versions are installed by the real importer: the earlier one is the same course
 * without its project, the later one is the course as the kit has it, which adds the project and its stage. A
 * candidate in which an exercise lost its ID is refused before any write.
 */
class ContentUpdateProgressTest {

	static final String COURSE = ExampleContent.COURSE;
	static final String LESSON = ExampleContent.LESSON;
	static final String PENDING = ExampleContent.PENDING;
	static final String E01 = ExampleContent.E01;
	static final String E02 = ExampleContent.E02;
	static final String STAGE = ExampleContent.STAGE;
	static final String[] PROGRESS_TABLES = {"users", "enrollments", "lesson_states", "exercise_states", "stage_states"};

	@TempDir Path temp;

	@Test
	void theUpdatedCourseKeepsTwoLearnersProgressAndAConflictingCandidateChangesNothing() throws Exception {
		Path before = ExampleContent.installVariant("bueno", curso -> curso.remove("proyecto"),
			Files.createDirectory(temp.resolve("antes")));
		Path after = onlyTheExampleCourse(temp.resolve("despues"));
		String ana = unique("Ana_");
		String bruno = unique("Bruno_");
		try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
			.withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
				new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))))) {
			postgres.start();
			var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));

			JsonNode anaBefore;
			JsonNode brunoBefore;
			try (var server = start(postgres, before)) {
				var first = learner(port(server), ana);
				var second = learner(port(server), bruno);
				ok(lessonState(first, LESSON, "completed"));
				ok(exerciseState(first, E01, "solved"));
				ok(exerciseState(first, E02, "in_progress"));
				ok(continuation(first, COURSE, LESSON));
				ok(lessonState(second, LESSON, "not_completed"));
				ok(exerciseState(second, E01, "pending"));
				ok(exerciseState(second, E02, "solved"));
				ok(continuation(second, COURSE, LESSON));
				// Really the earlier version: the pending module cannot be marked, and there is no stage yet.
				assertError(lessonState(first, PENDING, "completed"), 409, "LESSON_UNAVAILABLE");
				assertError(StageTestSupport.mark(first, STAGE, "completed"), 404, "NOT_FOUND");

				anaBefore = ok(progress(first, COURSE));
				brunoBefore = ok(progress(second, COURSE));
				assertCounts(anaBefore.path("progress"), 1, 1, 2, 100.0);
				assertCounts(brunoBefore.path("progress"), 0, 1, 2, 0.0);
				StageTestSupport.stageCounts(ok(StageTestSupport.stageProgress(first, COURSE)).path("progress"), 0, 0, 0);
			}
			var progressRows = TestDatabase.snapshot(jdbc, PROGRESS_TABLES);

			List<JsonNode> anaSaved;
			List<JsonNode> brunoSaved;
			try (var server = start(postgres, after)) {
				assertThat(TestDatabase.snapshot(jdbc, PROGRESS_TABLES)).isEqualTo(progressRows);
				var first = browser(server, ana);
				var second = browser(server, bruno);
				var anaAfter = ok(progress(first, COURSE));
				var brunoAfter = ok(progress(second, COURSE));
				assertKept(anaAfter, anaBefore, Map.of(LESSON, "completed"), Map.of(E01, "solved", E02, "in_progress"));
				assertKept(brunoAfter, brunoBefore, Map.of(LESSON, "not_completed"), Map.of(E01, "pending", E02, "solved"));
				assertCounts(anaAfter.path("progress"), 1, 1, 2, 100.0);
				assertCounts(brunoAfter.path("progress"), 0, 1, 2, 0.0);
				for (ApiClient browser : List.of(first, second)) {
					var stages = ok(StageTestSupport.stageProgress(browser, COURSE));
					StageTestSupport.stageProgressShape(stages, COURSE, true, null);
					assertThat(stages.path("stageStates").size()).isZero();
					StageTestSupport.stageCounts(stages.path("progress"), 0, 1, 1);
				}

				// The new stage, each learner apart; the other one's progress never moves.
				var brunoUntouched = personal(second);
				ok(StageTestSupport.mark(first, STAGE, "completed"));
				ok(StageTestSupport.open(first, COURSE, STAGE));
				assertThat(personal(second)).isEqualTo(brunoUntouched);
				ok(StageTestSupport.open(second, COURSE, STAGE));
				assertContinuation(ok(progress(second, COURSE)).path("continuation"), LESSON);
				anaSaved = personal(first);
				brunoSaved = personal(second);
			}

			try (var server = start(postgres, after)) {
				var first = browser(server, ana);
				var second = browser(server, bruno);
				assertThat(personal(first)).isEqualTo(anaSaved);
				assertThat(personal(second)).isEqualTo(brunoSaved);
				assertStages(first, Map.of(STAGE, "completed"), STAGE, 1);
				assertStages(second, Map.of(), STAGE, 0);
			}

			// A candidate in which an existing exercise lost its ID is refused at startup, before any write.
			Path conflict = conflictingCopy(temp.resolve("conflicto"));
			var unchanged = TestDatabase.snapshot(jdbc);
			Throwable failure = catchThrowable(() -> start(postgres, conflict).close());
			assertThat(NestedExceptionUtils.getMostSpecificCause(failure)).isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("nothing was changed")
				.hasMessageContaining("exercise " + E01 + " is no longer in the manifest");
			assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(unchanged);

			try (var server = start(postgres, after)) {
				var first = browser(server, ana);
				var second = browser(server, bruno);
				assertThat(personal(first)).isEqualTo(anaSaved);
				assertThat(personal(second)).isEqualTo(brunoSaved);
				assertCounts(ok(progress(first, COURSE)).path("progress"), 1, 1, 2, 100.0);
				assertCounts(ok(progress(second, COURSE)).path("progress"), 0, 1, 2, 0.0);
			}
		}
	}

	/** Same states, timestamps and continuation as before the update, and exactly this learner's own ones. */
	private static void assertKept(JsonNode after, JsonNode before, Map<String, String> lessons, Map<String, String> exercises) {
		assertThat(after.path("lessonStates")).isEqualTo(before.path("lessonStates"));
		assertThat(after.path("exerciseStates")).isEqualTo(before.path("exerciseStates"));
		assertThat(after.path("continuation")).isEqualTo(before.path("continuation"));
		assertThat(states(after.path("lessonStates"), "lessonId")).isEqualTo(lessons);
		assertThat(states(after.path("exerciseStates"), "exerciseId")).isEqualTo(exercises);
		assertContinuation(after.path("continuation"), LESSON);
	}

	private static void assertStages(ApiClient browser, Map<String, String> states, String last, int completed) throws Exception {
		var stages = ok(StageTestSupport.stageProgress(browser, COURSE));
		StageTestSupport.stageProgressShape(stages, COURSE, true, last);
		assertThat(states(stages.path("stageStates"), "stageId")).isEqualTo(states);
		StageTestSupport.stageCounts(stages.path("progress"), completed, 1, 1);
	}

	private static Map<String, String> states(JsonNode list, String idField) {
		Map<String, String> states = new TreeMap<>();
		list.forEach(item -> states.put(item.path(idField).asString(), item.path("state").asString()));
		return states;
	}

	/** Both progress domains of the course and the course list, through public routes only. */
	private static List<JsonNode> personal(ApiClient browser) throws Exception {
		return List.of(ok(progress(browser, COURSE)), ok(StageTestSupport.stageProgress(browser, COURSE)),
			ok(browser.get("/api/me/courses")));
	}

	/** A new browser logged in on a new server: sessions never outlive a restart. */
	private static ApiClient browser(ConfigurableApplicationContext server, String username) throws Exception {
		var browser = new ApiClient(port(server));
		login(browser, username);
		return browser;
	}

	/** Always an explicit content directory and this test's own database; nothing from the environment. */
	private static ConfigurableApplicationContext start(PostgreSQLContainer postgres, Path content) {
		String[] args = {"--server.port=0", "--code-academy.content-dir=" + content,
			"--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
			"--spring.datasource.password=" + postgres.getPassword()};
		return CodeAcademyApplication.application(args).run(args);
	}

	private static int port(ConfigurableApplicationContext server) {
		return ((WebServerApplicationContext) server).getWebServer().getPort();
	}

	/** The updated version alone, as the earlier one was installed alone: a second course is not part of the update. */
	private static Path onlyTheExampleCourse(Path root) throws IOException {
		Files.createDirectories(root);
		ExampleContent.copyCourseInto(COURSE, root);
		return root;
	}

	/** A copy of the updated course whose manifest gives the first exercise a new ID. */
	private static Path conflictingCopy(Path root) throws IOException {
		onlyTheExampleCourse(root);
		Path manifestPath = root.resolve(COURSE + "/manifest.json");
		JsonNode manifest = ApiClient.JSON.readTree(Files.readString(manifestPath));
		for (JsonNode lesson : manifest.path("lessons")) {
			if (!lesson.path("id").asString().equals(LESSON)) continue;
			// The same exercise under a new ID: its stored ID disappears, which only an equivalence could resolve.
			((ObjectNode) lesson.path("exercises").get(0)).put("id", LESSON + ".otro-pedido");
		}
		Files.writeString(manifestPath, ApiClient.JSON.writeValueAsString(manifest));
		return root;
	}
}
