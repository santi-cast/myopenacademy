package dev.codeacademy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.catalog.CatalogConflictException;
import dev.codeacademy.catalog.CatalogSynchronizer;
import dev.codeacademy.content.ContentFixture;
import dev.codeacademy.content.ContentLoader;
import dev.codeacademy.content.ContentValidationException;
import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Lesson;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Progress outlives sessions and server restarts, and a failed content import never touches it (L27). */
class ProgressPersistenceTest extends ProgressTestSupport {

	@Autowired HikariDataSource dataSource;
	@Autowired CatalogSynchronizer synchronizer;

	@TempDir
	Path tempDir;

	@Test
	void progressSurvivesLogoutAndANewLogin() throws Exception {
		String username = unique("Alumno_");
		var first = learner(port, username);
		markSomeProgress(first);
		JsonNode demo = ok(progress(first, "demo"));
		JsonNode courses = ok(first.get("/api/me/courses"));
		assertThat(first.send("POST", "/api/auth/logout", null).statusCode()).isEqualTo(204);
		assertError(progress(first, "demo"), 401, "AUTHENTICATION_REQUIRED");

		var second = new ApiClient(port);
		login(second, username);

		assertThat(ok(progress(second, "demo"))).isEqualTo(demo);
		assertThat(ok(second.get("/api/me/courses"))).isEqualTo(courses);
	}

	@Test
	void progressSurvivesAServerRestartWhileTheSessionDoesNot() throws Exception {
		String username = unique("Alumno_");
		JsonNode demo;
		JsonNode courses;
		String session;
		try (var server = start()) {
			var browser = learner(port(server), username);
			markSomeProgress(browser);
			demo = ok(progress(browser, "demo"));
			courses = ok(browser.get("/api/me/courses"));
			session = browser.cookie("JSESSIONID").orElseThrow().getValue();
		}

		try (var server = start()) {
			var stale = new ApiClient(port(server));
			stale.useSession(session);
			assertError(progress(stale, "demo"), 401, "AUTHENTICATION_REQUIRED");

			var browser = new ApiClient(port(server));
			login(browser, username);
			assertThat(ok(progress(browser, "demo"))).isEqualTo(demo);
			assertThat(ok(browser.get("/api/me/courses"))).isEqualTo(courses);
		}
	}

	@Test
	void aConflictingImportLeavesProgressAndTheWholeDatabaseUntouched() throws Exception {
		var browser = learner();
		markSomeProgress(browser);
		JsonNode demo = ok(progress(browser, "demo"));
		Manifest java = ContentLoader.load(CONTENT_ROOT, "java/manifest.json").manifest();
		Manifest base = ContentLoader.load(CONTENT_ROOT, "demo/manifest.json").manifest();
		ContentFixture swapped = ContentFixture.copyInto(tempDir.resolve("swapped"));
		swapped.editManifest(manifest -> {
			var exercises = ContentFixture.lesson(manifest, INTRO).get("exercises");
			String first = exercises.get(0).get("title").asString();
			String second = exercises.get(1).get("title").asString();
			((ObjectNode) exercises.get(0)).put("title", second);
			((ObjectNode) exercises.get(1)).put("title", first);
		});
		Manifest retitled = swapped.load().manifest();
		Manifest exerciseRemoved = withLessons(base, lesson -> lesson.id().equals(INTRO)
				? withExercises(lesson, List.of(lesson.exercises().get(0))) : lesson);
		Manifest lessonRemoved = new Manifest(base.schemaVersion(), base.source(), base.course(), base.phases(),
				base.lessons().stream().filter(lesson -> !lesson.id().equals(NEXT)).toList(), base.resources(),
				base.sourceToId());
		Manifest phaseRemoved = new Manifest(base.schemaVersion(), base.source(), base.course(),
				base.phases().subList(0, 1), base.lessons().stream().map(lesson -> withPhase(lesson, "demo-phase-01"))
					.toList(), base.resources(), base.sourceToId());
		var before = snapshot();

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, retitled)))
			.isInstanceOf(CatalogConflictException.class)
			.hasMessageContaining("exercise demo-01.01-e01 is titled «Primer ejercicio», not «Segundo ejercicio»");
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, exerciseRemoved)))
			.isInstanceOf(CatalogConflictException.class)
			.hasMessageContaining("exercise demo-01.01-e02 is no longer in the manifest");
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, lessonRemoved)))
			.isInstanceOf(CatalogConflictException.class)
			.hasMessageContaining("lesson demo-01.02 is no longer in the manifest");
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, phaseRemoved)))
			.isInstanceOf(CatalogConflictException.class)
			.hasMessageContaining("phase demo-phase-02 is no longer in the manifest");
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(base)))
			.isInstanceOf(CatalogConflictException.class)
			.hasMessageContaining("course java is no longer in the content");

		assertThat(snapshot()).isEqualTo(before);
		assertThat(ok(progress(browser, "demo"))).isEqualTo(demo);
	}

	/** S22: a lesson's phase is presentation, not identity; moving it never touches progress. */
	@Test
	void aLessonMovedToAnotherPhaseKeepsAllProgress() throws Exception {
		var browser = learner();
		markSomeProgress(browser);
		JsonNode demo = ok(progress(browser, "demo"));
		Manifest java = ContentLoader.load(CONTENT_ROOT, "java/manifest.json").manifest();
		Manifest base = ContentLoader.load(CONTENT_ROOT, "demo/manifest.json").manifest();
		Manifest moved = withLessons(base, lesson -> lesson.id().equals(NEXT) ? withPhase(lesson, "demo-phase-02")
				: lesson);
		var progressBefore = TestDatabase.snapshot(jdbc, "users", "enrollments", "lesson_states", "exercise_states");

		try {
			synchronizer.synchronize(List.of(java, moved));

			assertThat(jdbc.queryForObject("select phase_id from code_academy.lessons where id = ?", String.class, NEXT))
				.isEqualTo("demo-phase-02");
			assertThat(TestDatabase.snapshot(jdbc, "users", "enrollments", "lesson_states", "exercise_states"))
				.isEqualTo(progressBefore);
			assertThat(ok(progress(browser, "demo"))).isEqualTo(demo);
		}
		finally {
			synchronizer.synchronize(List.of(java, base));
		}
		assertThat(jdbc.queryForObject("select phase_id from code_academy.lessons where id = ?", String.class, NEXT))
			.isEqualTo("demo-phase-01");
	}

	@Test
	void anInvalidOrFailingImportLeavesProgressAndTheWholeDatabaseUntouched() throws Exception {
		var browser = learner();
		markSomeProgress(browser);
		JsonNode demo = ok(progress(browser, "demo"));
		Manifest java = ContentLoader.load(CONTENT_ROOT, "java/manifest.json").manifest();
		Manifest base = ContentLoader.load(CONTENT_ROOT, "demo/manifest.json").manifest();
		ContentFixture broken = ContentFixture.copyInto(tempDir.resolve("broken"));
		broken.replace(ContentFixture.INTRO, "#### Ejercicio 2 · Segundo `ejercicio`",
				"#### Ejercicio 2 · Segundo `ejercicio` cambiado");
		// Two lessons on one position only fail when the transaction commits, after every other write.
		Manifest clash = withLessons(base, lesson -> lesson.id().equals(PENDING)
				? new Lesson(lesson.id(), "Título nuevo", 1, lesson.phaseId(), lesson.sourcePath(),
						lesson.sourceAvailable(), lesson.available(), lesson.bodyPath(), lesson.exercises())
				: lesson);
		var before = snapshot();

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, broken.load().manifest())))
			.isInstanceOf(ContentValidationException.class)
			.hasMessageContaining("exercise demo-01.01-e02 «Segundo ejercicio» has no heading with that title");
		assertThat(catchThrowable(() -> synchronizer.synchronize(List.of(java, clash)))).isNotNull();

		assertThat(snapshot()).isEqualTo(before);
		assertThat(ok(progress(browser, "demo"))).isEqualTo(demo);
	}

	/** States on both demo lessons and both exercises, a continuation, and the Java course added explicitly. */
	private static void markSomeProgress(ApiClient browser) throws Exception {
		ok(lessonState(browser, INTRO, "completed"));
		ok(lessonState(browser, NEXT, "not_completed"));
		ok(exerciseState(browser, E01, "solved"));
		ok(exerciseState(browser, E02, "in_progress"));
		ok(continuation(browser, "demo", NEXT));
		ok(add(browser, "java"));
	}

	/** A separate application on the same database and content, as a restarted backend would be. */
	private ConfigurableApplicationContext start() {
		String[] args = { "--server.port=0", "--code-academy.content-dir=" + CONTENT_ROOT,
				"--spring.datasource.url=" + dataSource.getJdbcUrl(),
				"--spring.datasource.username=" + dataSource.getUsername(),
				"--spring.datasource.password=" + dataSource.getPassword() };
		return CodeAcademyApplication.application(args).run(args);
	}

	private static int port(ConfigurableApplicationContext server) {
		return ((WebServerApplicationContext) server).getWebServer().getPort();
	}

	private static Manifest withLessons(Manifest manifest, UnaryOperator<Lesson> change) {
		List<Lesson> lessons = new ArrayList<>();
		manifest.lessons().forEach(lesson -> lessons.add(change.apply(lesson)));
		return new Manifest(manifest.schemaVersion(), manifest.source(), manifest.course(), manifest.phases(),
				lessons, manifest.resources(), manifest.sourceToId());
	}

	private static Lesson withPhase(Lesson lesson, String phaseId) {
		return new Lesson(lesson.id(), lesson.title(), lesson.position(), phaseId, lesson.sourcePath(),
				lesson.sourceAvailable(), lesson.available(), lesson.bodyPath(), lesson.exercises());
	}

	private static Lesson withExercises(Lesson lesson, List<Manifest.Exercise> exercises) {
		return new Lesson(lesson.id(), lesson.title(), lesson.position(), lesson.phaseId(), lesson.sourcePath(),
				lesson.sourceAvailable(), lesson.available(), lesson.bodyPath(), exercises);
	}
}
