package dev.codeacademy;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.codeacademy.content.ContentLoader;
import dev.codeacademy.content.Manifest;

import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.StageFixture.FIRST;
import static org.assertj.core.api.Assertions.assertThat;

/** A genuinely populated V5 database, not a clean V6 database or a fixture reconstructed from git history. */
class StageMigrationTest {

	@TempDir Path temp;
	static final Instant SAVED_AT = Instant.parse("2026-10-01T12:00:00Z");

	@Test
	void additiveV6PreservesLegacyCatalogAccountsAndTwoUsersProgressThroughPublicApis() throws Exception {
		var fixture = new StageFixture(temp);
		try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
			.withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
				new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))))) {
			postgres.start();
			var jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
			migrate(postgres, "5");
			for (Manifest manifest : ContentLoader.loadAll(fixture.content.root).stream().map(c -> c.manifest()).toList()) {
				seedV5Catalog(jdbc, manifest);
			}
			var passwordHash = new SecurityConfiguration().passwordEncoder().encode(PASSWORD);
			seedV5Learner(jdbc, "Legacy_A", passwordHash, "completed", "solved", INTRO);
			seedV5Learner(jdbc, "Legacy_B", passwordHash, "not_completed", "in_progress", NEXT);
			var legacy = TestDatabase.snapshot(jdbc, "users", "courses", "phases", "lessons", "exercises", "lesson_states", "exercise_states");
			var enrollments = jdbc.queryForList("select id, user_id, course_id, last_opened_lesson_id, created_at from code_academy.enrollments order by id");

			migrate(postgres, "6");

			assertThat(Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.load().info().current().getVersion().toString()).isEqualTo("6");
			assertThat(TestDatabase.snapshot(jdbc, "users", "courses", "phases", "lessons", "exercises", "lesson_states", "exercise_states")).isEqualTo(legacy);
			assertThat(jdbc.queryForList("select id, user_id, course_id, last_opened_lesson_id, created_at from code_academy.enrollments order by id")).isEqualTo(enrollments);
			assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns where table_schema = 'code_academy' "
				+ "and table_name = 'enrollments' and column_name = 'last_opened_stage_id'", String.class)).isEqualTo("YES");
			assertThat(jdbc.queryForList("select last_opened_stage_id from code_academy.enrollments"))
				.allSatisfy(row -> assertThat(row.get("last_opened_stage_id")).isNull());

			String[] args = {"--server.port=0", "--code-academy.content-dir=" + fixture.content.root,
				"--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword()};
			List<tools.jackson.databind.JsonNode> firstSaved;
			List<tools.jackson.databind.JsonNode> secondSaved;
			try (var context = CodeAcademyApplication.application(args).run(args)) {
				int port = ((WebServerApplicationContext) context).getWebServer().getPort();
				var first = new ApiClient(port);
				var second = new ApiClient(port);
				for (var entry : List.of(java.util.Map.entry(first, "Legacy_A"), java.util.Map.entry(second, "Legacy_B"))) {
					assertThat(entry.getKey().send("POST", "/api/auth/login", credentials(entry.getValue(), PASSWORD)).statusCode()).isEqualTo(200);
				}
				for (ApiClient browser : List.of(first, second)) {
					var h4 = ok(progress(browser, "demo"));
					assertThat(h4.propertyNames()).containsExactlyInAnyOrder("courseId", "added", "lessonStates", "exerciseStates", "continuation", "progress");
					assertThat(h4.path("courseId").asString()).isEqualTo("demo");
					assertThat(h4.path("added").asBoolean()).isTrue();
					assertThat(h4.path("lessonStates").size()).isEqualTo(1);
					assertThat(h4.path("lessonStates").get(0).path("lessonId").asString()).isEqualTo(INTRO);
					assertThat(h4.path("lessonStates").get(0).path("state").asString()).isEqualTo(browser == first ? "completed" : "not_completed");
					assertThat(utc(h4.path("lessonStates").get(0).path("updatedAt"))).isEqualTo(SAVED_AT);
					assertThat(h4.path("exerciseStates").size()).isEqualTo(1);
					assertThat(h4.path("exerciseStates").get(0).path("exerciseId").asString()).isEqualTo(E01);
					assertThat(h4.path("exerciseStates").get(0).path("state").asString()).isEqualTo(browser == first ? "solved" : "in_progress");
					assertThat(utc(h4.path("exerciseStates").get(0).path("updatedAt"))).isEqualTo(SAVED_AT);
					assertContinuation(h4.path("continuation"), browser == first ? INTRO : NEXT);
					assertCounts(h4.path("progress"), browser == first ? 1 : 0, 2, 3, browser == first ? 50.0 : 0.0);
					var stages = ok(StageTestSupport.stageProgress(browser, "demo"));
					StageTestSupport.stageProgressShape(stages, "demo", true, null);
					assertThat(stages.path("stageStates").size()).isZero();
					StageTestSupport.stageCounts(stages.path("progress"), 0, 2, 3);
					assertThat(ok(browser.get("/api/me/courses")).path("courses").size()).isEqualTo(1);
				}
				var secondBefore = StageTestSupport.personal(second);
				var h4Before = ok(progress(first, "demo"));
				var marked = ok(StageTestSupport.mark(first, FIRST, "completed"));
				assertThat(marked.path("courseAutoAdded").asBoolean()).isFalse();
				StageTestSupport.stageContinuation(marked.path("continuation"), null);
				assertThat(ok(progress(first, "demo"))).isEqualTo(h4Before);
				assertThat(StageTestSupport.personal(second)).isEqualTo(secondBefore);
				assertThat(TestDatabase.withoutAccountRoles(TestDatabase.snapshot(jdbc, "users", "courses", "phases", "lessons", "exercises", "lesson_states", "exercise_states"))).isEqualTo(legacy);
				assertThat(jdbc.queryForList("select distinct role || ' ' || active from code_academy.users", String.class)).containsExactly("user true");
				ok(StageTestSupport.open(first, "demo", FIRST));
				firstSaved = StageTestSupport.personal(first);
				secondSaved = StageTestSupport.personal(second);
			}
			// A new server reloads the same disposable database and synchronizes the catalog again.
			try (var restarted = CodeAcademyApplication.application(args).run(args)) {
				int port = ((WebServerApplicationContext) restarted).getWebServer().getPort();
				var first = new ApiClient(port);
				var second = new ApiClient(port);
				assertThat(first.send("POST", "/api/auth/login", credentials("Legacy_A", PASSWORD)).statusCode()).isEqualTo(200);
				assertThat(second.send("POST", "/api/auth/login", credentials("Legacy_B", PASSWORD)).statusCode()).isEqualTo(200);
				assertThat(StageTestSupport.personal(first)).isEqualTo(firstSaved);
				assertThat(StageTestSupport.personal(second)).isEqualTo(secondSaved);
			}
		}
	}

	private static void migrate(PostgreSQLContainer postgres, String target) {
		Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).target(target).load().migrate();
	}

	/** Inserts only columns present in V5, so this cannot silently seed the new schema instead. */
	private static void seedV5Catalog(JdbcTemplate jdbc, Manifest manifest) {
		String course = manifest.course().id();
		jdbc.update("insert into code_academy.courses (id, title) values (?, ?)", course, manifest.course().title());
		for (var phase : manifest.phases()) {
			jdbc.update("insert into code_academy.phases (id, course_id, title, position) values (?, ?, ?, ?)", phase.id(), course, phase.title(), phase.position());
		}
		for (var lesson : manifest.lessons()) {
			jdbc.update("insert into code_academy.lessons (id, course_id, title, position, phase_id, source_available, available) values (?, ?, ?, ?, ?, ?, ?)",
				lesson.id(), course, lesson.title(), lesson.position(), lesson.phaseId(), lesson.sourceAvailable(), lesson.available());
			for (int i = 0; i < lesson.exercises().size(); i++) {
				var exercise = lesson.exercises().get(i);
				jdbc.update("insert into code_academy.exercises (id, course_id, lesson_id, title, position) values (?, ?, ?, ?, ?)",
					exercise.id(), course, lesson.id(), exercise.title(), i + 1);
			}
		}
	}

	private static void seedV5Learner(JdbcTemplate jdbc, String name, String hash, String lessonState, String exerciseState, String last) {
		UUID id = UUID.randomUUID();
		Timestamp at = Timestamp.from(SAVED_AT);
		jdbc.update("insert into code_academy.users (id, username, username_canonical, password_hash, credentials_version, created_at) values (?, ?, ?, ?, 1, ?)",
			id, name, name.toLowerCase(java.util.Locale.ROOT), hash, at);
		Long enrollment = jdbc.queryForObject("insert into code_academy.enrollments (user_id, course_id, last_opened_lesson_id, created_at) values (?, 'demo', ?, ?) returning id", Long.class, id, last, at);
		jdbc.update("insert into code_academy.lesson_states (enrollment_id, course_id, lesson_id, state, updated_at) values (?, 'demo', ?, ?, ?)", enrollment, INTRO, lessonState, at);
		jdbc.update("insert into code_academy.exercise_states (enrollment_id, course_id, exercise_id, state, updated_at) values (?, 'demo', ?, ?, ?)", enrollment, E01, exerciseState, at);
	}
}
