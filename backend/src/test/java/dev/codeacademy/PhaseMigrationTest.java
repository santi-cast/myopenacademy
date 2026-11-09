package dev.codeacademy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.codeacademy.content.ContentLoader;
import dev.codeacademy.content.ExampleContent;
import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Lesson;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 on a database that already has the catalog and progress written at V4, like the owner's local one: the migration
 * succeeds with lessons in place, the next startup fills the phases, and no progress row changes.
 */
class PhaseMigrationTest {

	private static final List<String> PROGRESS = List.of("users", "lesson_states", "exercise_states");

	static PostgreSQLContainer postgres;
	static JdbcTemplate jdbc;

	@BeforeAll
	static void startDatabase() {
		postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
				.withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
						new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))));
		postgres.start();
		jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()));
	}

	@AfterAll
	static void stopDatabase() {
		postgres.stop();
	}

	@Test
	void upgradingFromV4KeepsProgressAndTheStartupFillsThePhases(@TempDir Path temp) throws IOException {
		// Only the course that V4 knew: a second course would be a new course at startup, not an upgrade.
		Path content = Files.createDirectory(temp.resolve("content"));
		ExampleContent.copyCourseInto(ExampleContent.COURSE, content);
		migrateTo("4");
		Manifest ejemplo = ContentLoader.load(content, "ejemplo/manifest.json").manifest();
		synchronizeAsV4(ejemplo);
		markProgressAsV4();
		var progress = progressSnapshot();
		var enrollments = legacyEnrollmentsSnapshot();
		List<Map<String, Object>> lessons = jdbc.queryForList(
				"select id, course_id, title, position, available, source_available from code_academy.lessons order by id");
		assertThat(lessons).hasSize(2);

		migrateTo("5");

		assertThat(jdbc.queryForObject("select count(*) from code_academy.phases", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lessons where phase_id is null",
				Integer.class)).isEqualTo(2);

		String[] args = { "--server.port=0", "--code-academy.content-dir=" + content,
				"--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword() };
		CodeAcademyApplication.application(args).run(args).close();

		assertThat(jdbc.queryForList("select id || ' ' || position || ' ' || title from code_academy.phases "
				+ "where course_id = 'ejemplo' order by position", String.class))
			.containsExactly("ejemplo.fase.primeros-pasos 1 Primeros pasos");
		List<String> expected = new ArrayList<>();
		ejemplo.lessons().forEach(lesson -> expected.add(lesson.id() + " " + lesson.phaseId()));
		assertThat(jdbc.queryForList("select id || ' ' || phase_id from code_academy.lessons", String.class))
			.containsExactlyInAnyOrderElementsOf(expected);
		assertThat(jdbc.queryForList(
				"select id, course_id, title, position, available, source_available from code_academy.lessons order by id"))
			.isEqualTo(lessons);
		assertThat(progressSnapshot()).isEqualTo(progress);
		assertThat(legacyEnrollmentsSnapshot()).isEqualTo(enrollments);
		assertThat(jdbc.queryForList("select last_opened_stage_id from code_academy.enrollments order by id",
				String.class)).hasSize(enrollments.size()).containsOnlyNulls();
	}

	private static void migrateTo(String version) {
		Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.target(version).load().migrate();
	}

	/** The rows the V4 synchronizer wrote: courses, lessons and exercises, with no phase. */
	private static void synchronizeAsV4(Manifest manifest) {
		jdbc.update("insert into code_academy.courses (id, title) values (?, ?)", manifest.course().id(),
				manifest.course().title());
		for (Lesson lesson : manifest.lessons()) {
			jdbc.update("insert into code_academy.lessons (id, course_id, title, position, available, source_available) "
					+ "values (?, ?, ?, ?, ?, ?)", lesson.id(), manifest.course().id(), lesson.title(), lesson.position(),
					lesson.available(), lesson.sourceAvailable());
			for (int i = 0; i < lesson.exercises().size(); i++) {
				jdbc.update("insert into code_academy.exercises (id, course_id, lesson_id, title, position) "
						+ "values (?, ?, ?, ?, ?)", lesson.exercises().get(i).id(), manifest.course().id(), lesson.id(),
						lesson.exercises().get(i).title(), i + 1);
			}
		}
	}

	private static void markProgressAsV4() {
		UUID user = UUID.randomUUID();
		Timestamp at = Timestamp.from(Instant.parse("2026-10-01T12:00:00Z"));
		jdbc.update("insert into code_academy.users (id, username, username_canonical, password_hash, "
				+ "credentials_version, created_at) values (?, 'Alumna_V4', 'alumna_v4', 'hash-no-usado', 1, ?)", user, at);
		Long enrollment = jdbc.queryForObject("insert into code_academy.enrollments (user_id, course_id, "
				+ "last_opened_lesson_id, created_at) values (?, 'ejemplo', 'ejemplo.ficheros-json', ?) returning id", Long.class, user, at);
		jdbc.update("insert into code_academy.lesson_states (enrollment_id, course_id, lesson_id, state, updated_at) "
				+ "values (?, 'ejemplo', 'ejemplo.ficheros-json', 'completed', ?)", enrollment, at);
		jdbc.update("insert into code_academy.exercise_states (enrollment_id, course_id, exercise_id, state, updated_at) "
				+ "values (?, 'ejemplo', 'ejemplo.ficheros-json.un-pedido', 'solved', ?), "
				+ "(?, 'ejemplo', 'ejemplo.ficheros-json.la-coma-de-mas', 'in_progress', ?)",
				enrollment, at, enrollment, at);
	}

	/** Every V4 enrollment column, without the independent stage continuation added at V6. */
	private static List<Map<String, Object>> legacyEnrollmentsSnapshot() {
		return jdbc.queryForList("select id, user_id, course_id, last_opened_lesson_id, created_at "
				+ "from code_academy.enrollments order by id");
	}

	private static List<Map<String, Object>> progressSnapshot() {
		var snapshot = TestDatabase.snapshot(jdbc, PROGRESS.toArray(String[]::new));
		assertThat(snapshot).hasSize(PROGRESS.size());
		return snapshot;
	}
}
