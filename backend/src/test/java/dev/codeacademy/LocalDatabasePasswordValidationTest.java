package dev.codeacademy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDatabasePasswordValidationTest {

	@TempDir Path directory;

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(DataSourceProperties.class)
	static class Binding {
	}

	@Test
	void missingPasswordFailsBeforeDataSourceCreation() throws Exception {
		assertRejected("");
	}

	@Test
	void emptyPasswordFailsBeforeDataSourceCreation() throws Exception {
		assertRejected("DB_PASSWORD=\n");
	}

	@Test
	void whitespacePasswordFailsBeforeDataSourceCreation() throws Exception {
		assertRejected("DB_PASSWORD=\\u0020\\u0020\n");
	}

	@Test
	void validSyntheticPasswordStillBindsUnchanged() throws Exception {
		Path fixture = fixture("DB_PASSWORD=fixture_only\n");
		var created = new AtomicBoolean();
		runner(fixture, true, created).run(context -> {
			assertThat(context).hasNotFailed();
			var properties = context.getBean(DataSourceProperties.class);
			assertThat(properties.determinePassword()).isEqualTo("fixture_only");
			assertThat(properties.determineUsername()).isEqualTo("fixture_user");
			assertThat(properties.determineUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:5434/fixture_database");
			assertThat(created).isTrue();
		});
	}

	@Test
	void defaultProfileDoesNotRequireLocalPasswordOrReadLocalFile() {
		var created = new AtomicBoolean();
		runner(directory.resolve("missing.properties"), false, created).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeansOfType(LocalDatabaseConfiguration.class)).isEmpty();
			assertThat(created).isTrue();
		});
	}

	private void assertRejected(String passwordProperty) throws Exception {
		Path fixture = fixture(passwordProperty);
		var created = new AtomicBoolean();
		runner(fixture, true, created).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure()).hasMessageContaining("DB_PASSWORD")
					.hasMessageNotContaining("fixture_user")
					.hasMessageNotContaining("fixture_database");
			assertThat(created).as("Validation must precede DataSource creation and JDBC authentication").isFalse();
		});
	}

	private Path fixture(String passwordProperty) throws Exception {
		return Files.writeString(directory.resolve("ordinary.properties"),
				"DB_NAME=fixture_database\nDB_USERNAME=fixture_user\n" + passwordProperty);
	}

	private ApplicationContextRunner runner(Path fixture, boolean local, AtomicBoolean created) {
		return new ApplicationContextRunner()
				.withUserConfiguration(LocalDatabaseConfiguration.class, Binding.class)
				// Eager DataSource creation sentinel, never opened or connected to any database.
				.withBean("dataSource", DataSource.class, () -> {
					created.set(true);
					return new DriverManagerDataSource();
				})
				.withPropertyValues("spring.profiles.active=" + (local ? "local" : ""),
						"CODE_ACADEMY_ENV_FILE=" + fixture.toAbsolutePath())
				.withInitializer(context -> {
					var sources = context.getEnvironment().getPropertySources();
					sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
					sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
					new ConfigDataApplicationContextInitializer().initialize(context);
				});
	}
}
