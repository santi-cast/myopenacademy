package dev.codeacademy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalEnvironmentConfigurationTest {

	@TempDir Path directory;

	@Test
	void localProfileImportsSyntheticDatabaseConfiguration() throws Exception {
		Path fixture = fixture("DB_NAME=fixture_database\nDB_USERNAME=fixture_user\nDB_PASSWORD=fixture_only\n");
		runner(fixture, true).run(context -> {
			assertThat(context).hasNotFailed();
			var environment = context.getEnvironment();
			assertThat(environment.getRequiredProperty("spring.datasource.url"))
					.isEqualTo("jdbc:postgresql://127.0.0.1:5434/fixture_database");
			assertThat(environment.getRequiredProperty("spring.datasource.username")).isEqualTo("fixture_user");
			assertThat(environment.getRequiredProperty("spring.datasource.password")).isEqualTo("fixture_only");
		});
	}

	/** H10: everything that changes between installations comes from the environment (.env.example). */
	@Test
	void theInstallationIsConfiguredOnlyWithVariables() throws Exception {
		Path fixture = fixture("DB_NAME=academia\nDB_USERNAME=academia\nDB_PASSWORD=fixture_only\nDB_HOST=db\nDB_PORT=5432\n"
				+ "APP_ADDRESS=0.0.0.0\nAPP_PORT=9090\nPUBLIC_URL=http://mi-equipo.local:9090\nCONTENT_DIR=/content\n");
		runner(fixture, true).run(context -> {
			assertThat(context).hasNotFailed();
			var environment = context.getEnvironment();
			assertThat(environment.getRequiredProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db:5432/academia");
			assertThat(environment.getRequiredProperty("server.address")).isEqualTo("0.0.0.0");
			assertThat(environment.getRequiredProperty("server.port")).isEqualTo("9090");
			assertThat(environment.getRequiredProperty("code-academy.public-url")).isEqualTo("http://mi-equipo.local:9090");
			assertThat(environment.getRequiredProperty("code-academy.content-dir")).isEqualTo("/content");
		});
	}

	@Test
	void localProfileRejectsMissingSpecifiedFile() {
		runner(directory.resolve("missing.properties"), true).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasStackTraceContaining("missing.properties");
		});
	}

	@Test
	void missingPasswordFailsExplicitlyDuringConfigurationResolution() throws Exception {
		Path fixture = fixture("DB_NAME=fixture_database\nDB_USERNAME=fixture_user\n");
		runner(fixture, true).run(context -> {
			assertThat(context).hasNotFailed();
			// Resolve the real production property without starting a DataSource or making a connection.
			assertThatThrownBy(() -> context.getEnvironment().getRequiredProperty("spring.datasource.password"))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("DB_PASSWORD")
					.hasMessageNotContaining("fixture_user");
		});
	}

	@Test
	void defaultProfileNeverImportsLocalFile() {
		runner(directory.resolve("missing.properties"), false).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain("local");
			assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
					.isEqualTo("jdbc:postgresql://127.0.0.1:5434/code_academy");
		});
	}

	private Path fixture(String content) throws Exception {
		return Files.writeString(directory.resolve("ordinary.properties"), content);
	}

	private ApplicationContextRunner runner(Path fixture, boolean local) {
		return new ApplicationContextRunner()
				.withPropertyValues("CODE_ACADEMY_ENV_FILE=" + fixture.toAbsolutePath(),
						"spring.profiles.active=" + (local ? "local" : ""))
				.withInitializer(context -> {
					// Synthetic fixtures must not inherit real credentials from the shell or JVM.
					var sources = context.getEnvironment().getPropertySources();
					sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
					new ConfigDataApplicationContextInitializer().initialize(context);
				});
	}
}
