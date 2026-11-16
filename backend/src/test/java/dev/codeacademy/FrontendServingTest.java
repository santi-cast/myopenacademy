package dev.codeacademy;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.codeacademy.content.ExampleContent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H10, T2: one origin and one port. With FRONTEND_DIR, the backend serves the compiled frontend: files as they are, and
 * index.html for every path of the application, also those with a dot in an identifier; never for the API.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FrontendServingTest {

	static final Path DIST;

	static {
		try {
			DIST = Files.createTempDirectory("frontend-dist");
			Files.writeString(DIST.resolve("index.html"), "<!doctype html><title>MyOpenAcademy</title><div id=\"root\"></div>");
			Files.createDirectory(DIST.resolve("assets"));
			Files.writeString(DIST.resolve("assets/app-1234.js"), "console.log('academia')");
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("code-academy.content-dir", () -> ExampleContent.root().toString());
		registry.add("code-academy.frontend-dir", DIST::toString);
	}

	@LocalServerPort int port;

	@Test
	void everyPageOfTheApplicationIsTheIndexAndFilesAreThemselves() throws Exception {
		var browser = new ApiClient(port);
		for (String page : new String[] { "/", "/login", "/courses/inicial", "/lessons/inicial.markdown", "/lessons/ejemplo.ficheros-json",
				"/documents/inicial.fase.que-es-programar.retos", "/admin" }) {
			var response = browser.get(page);
			assertThat(response.statusCode()).as(page).isEqualTo(200);
			assertThat(response.headers().firstValue("Content-Type").orElse("")).as(page).startsWith("text/html");
			assertThat(response.body()).as(page).contains("<div id=\"root\"></div>");
		}
		var script = browser.get("/assets/app-1234.js");
		assertThat(script.statusCode()).isEqualTo(200);
		assertThat(script.body()).isEqualTo("console.log('academia')");
		// A missing file is a 404, never the page; the API keeps its own answers.
		assertThat(browser.get("/assets/no-existe.js").statusCode()).isEqualTo(404);
		assertThat(browser.get("/api/courses").statusCode()).isEqualTo(401);
		assertThat(browser.get("/api/no-existe").statusCode()).isNotEqualTo(200);
		// Only GET reaches the frontend: anything else to a page is refused.
		assertThat(browser.send("POST", "/lessons/inicial.markdown", "{}").statusCode()).isIn(401, 403);
	}
}
