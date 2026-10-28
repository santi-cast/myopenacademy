package dev.codeacademy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.NestedExceptionUtils;

import dev.codeacademy.account.PasswordResetCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** S15: content is loaded only by the web server; the console recovery never depends on it. */
class ContentStartupTest {

	@TempDir
	Path tempDir;

	private Path missing() {
		return tempDir.resolve("missing");
	}

	private Path broken() throws IOException {
		Path root = tempDir.resolve("broken");
		Files.createDirectories(root.resolve("java"));
		Files.writeString(root.resolve("java/manifest.json"), "{ \"schemaVersion\": 1,");
		return root;
	}

	@Test
	void resetModeStartsAndProvidesTheCommandWhateverTheContentDirectory() throws IOException {
		for (Path contentDir : List.of(missing(), broken())) {
			String[] args = { "--reset-password=Ana_1", "--code-academy.content-dir=" + contentDir };
			var application = CodeAcademyApplication.application(args);
			application.addPrimarySources(List.of(TestcontainersConfiguration.class));

			try (var context = application.run(args)) {
				assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
				assertThat(context.getBeansOfType(PasswordResetCommand.class)).hasSize(1);
			}
		}
	}

	@Test
	void webServerRefusesToStartWithAMissingContentDirectory() {
		assertThat(startFailure(missing())).contains("Invalid content", "content root", "cannot be read");
	}

	@Test
	void webServerRefusesToStartWithABrokenManifest() throws IOException {
		assertThat(startFailure(broken())).contains("Invalid content", "manifest is not valid JSON");
	}

	private static String startFailure(Path contentDir) {
		String[] args = { "--server.port=0", "--code-academy.content-dir=" + contentDir };
		var application = CodeAcademyApplication.application(args);
		application.addPrimarySources(List.of(TestcontainersConfiguration.class));

		Throwable failure = catchThrowable(() -> application.run(args).close());

		assertThat(failure).isNotNull();
		return NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
	}
}
