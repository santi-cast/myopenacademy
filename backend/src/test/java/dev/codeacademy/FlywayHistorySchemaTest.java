package dev.codeacademy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;

import static dev.codeacademy.ProgressTestSupport.exerciseState;
import static dev.codeacademy.ProgressTestSupport.learner;
import static dev.codeacademy.ProgressTestSupport.lessonState;
import static dev.codeacademy.ProgressTestSupport.login;
import static dev.codeacademy.ProgressTestSupport.ok;
import static dev.codeacademy.ProgressTestSupport.progress;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway's history stays in public whatever the database user is called. PostgreSQL's search_path starts with
 * "$user": with a user named code_academy, an unpinned Flyway would look for its history in the code_academy schema
 * that V1 creates, and the second startup would refuse a non-empty schema without history.
 */
class FlywayHistorySchemaTest {

	private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

	private static final String HISTORY = """
			select installed_rank, version, description, type, script, checksum, installed_by, installed_on, success
			from public.flyway_schema_history order by installed_rank
			""";

	@Test
	void aUserNamedLikeTheSchemaStartsTwiceAndKeepsItsData() throws Exception {
		try (var postgres = database("code_academy")) {
			JdbcTemplate jdbc = jdbc(postgres);
			String username = unique("Alumno_");
			JsonNode java;
			List<Map<String, Object>> history;
			try (var server = start(postgres)) {
				assertThat(server.getEnvironment().getProperty("spring.jpa.properties.hibernate.default_schema"))
					.isEqualTo("code_academy");
				var browser = learner(port(server), username);
				ok(lessonState(browser, "java-02.01", "completed"));
				ok(exerciseState(browser, "java-02.01-e01", "solved"));
				java = ok(progress(browser, "java"));
				history = assertHistoryInPublicOnly(jdbc);
			}

			try (var server = start(postgres)) {
				var browser = new ApiClient(port(server));
				login(browser, username);
				assertThat(ok(progress(browser, "java"))).isEqualTo(java);
			}

			assertThat(jdbc.queryForList(HISTORY)).isEqualTo(history);
			assertThat(jdbc.queryForObject("select count(*) from code_academy.users where username = ?", Integer.class,
					username)).isEqualTo(1);
		}
	}

	/** Databases migrated before the history was pinned already keep it in public: nothing is applied again. */
	@ParameterizedTest
	@ValueSource(strings = { "alumno_db", "code_academy" })
	void aDatabaseMigratedWithTheDefaultFlywaySettingsIsUsedUnchanged(String user) throws Exception {
		try (var postgres = database(user)) {
			JdbcTemplate jdbc = jdbc(postgres);
			// The previous configuration: Flyway with Boot's default location and no schema setting.
			Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration").load().migrate();
			jdbc.update("insert into code_academy.users (id, username, username_canonical, password_hash, "
					+ "credentials_version, created_at) values (gen_random_uuid(), 'Alumna_Previa', 'alumna_previa', "
					+ "'hash-no-usado', 1, now())");
			var history = assertHistoryInPublicOnly(jdbc);
			var users = TestDatabase.snapshot(jdbc, "users");

			start(postgres).close();
			start(postgres).close();

			assertThat(jdbc.queryForList(HISTORY)).isEqualTo(history);
			assertThat(TestDatabase.snapshot(jdbc, "users")).isEqualTo(users);
		}
	}

	/** One successful row per migration file, in version order, and no history table outside public. */
	private static List<Map<String, Object>> assertHistoryInPublicOnly(JdbcTemplate jdbc) throws IOException {
		assertThat(jdbc.queryForList("select table_schema from information_schema.tables "
				+ "where table_name = 'flyway_schema_history'", String.class)).containsExactly("public");
		var history = jdbc.queryForList(HISTORY);
		assertThat(history).extracting(row -> row.get("version")).containsExactlyElementsOf(migrationVersions());
		assertThat(history).extracting(row -> row.get("success")).containsOnly(true);
		return history;
	}

	private static List<String> migrationVersions() throws IOException {
		try (Stream<Path> files = Files.list(MIGRATIONS)) {
			return files.map(file -> file.getFileName().toString())
				.filter(name -> name.matches("V\\d+__.*\\.sql"))
				.map(name -> name.substring(1, name.indexOf("__")))
				.sorted((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)))
				.toList();
		}
	}

	private static PostgreSQLContainer database(String user) {
		var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6")).withUsername(user)
				.withCreateContainerCmdModifier(command -> command.getHostConfig().withPortBindings(
						new PortBinding(Ports.Binding.bindIp("127.0.0.1"), new ExposedPort(5432))));
		postgres.start();
		return postgres;
	}

	private static JdbcTemplate jdbc(PostgreSQLContainer postgres) {
		return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword()));
	}

	/** The application with its own configuration on this database, as the backend starts locally. */
	private static ConfigurableApplicationContext start(PostgreSQLContainer postgres) {
		String[] args = { "--server.port=0", "--code-academy.content-dir=" + CatalogApiTest.CONTENT,
				"--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
				"--spring.datasource.password=" + postgres.getPassword() };
		return CodeAcademyApplication.application(args).run(args);
	}

	private static int port(ConfigurableApplicationContext server) {
		return ((WebServerApplicationContext) server).getWebServer().getPort();
	}
}
