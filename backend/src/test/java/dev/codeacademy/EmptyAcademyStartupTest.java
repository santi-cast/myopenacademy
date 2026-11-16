package dev.codeacademy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * H10: an academy with no course yet starts, says so, and shows an empty catalog; and when its public address is plain
 * HTTP on the network, it warns that passwords travel unencrypted.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class EmptyAcademyStartupTest {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) throws Exception {
		Path empty = Files.createTempDirectory("sin-cursos");
		registry.add("code-academy.content-dir", empty::toString);
		registry.add("code-academy.public-url", () -> "http://192.168.1.50:8080");
	}

	@LocalServerPort int port;

	@Test
	void startsWithNoCourseSaysSoAndWarnsAboutPlainHttpOnTheNetwork(CapturedOutput output) throws Exception {
		var browser = ProgressTestSupport.learner(port, unique("Primera_"));
		var courses = browser.get("/api/courses");
		assertThat(courses.statusCode()).isEqualTo(200);
		assertThat(ApiClient.JSON.readTree(courses.body()).path("courses").size()).isZero();
		assertThat(browser.get("/api/me/courses").statusCode()).isEqualTo(200);
		// The startup notices were logged when this context started (it may have started before the capture).
		assertThat(StartupNotice.NO_COURSES).contains("No hay ningún curso instalado");
		var notice = new StartupNotice("http://192.168.1.50:8080", "MyOpenAcademy",
			dev.codeacademy.catalog.Catalog.load(Files.createTempDirectory("vacia")));
		notice.started();
		assertThat(output).contains("MyOpenAcademy, en http://192.168.1.50:8080")
			.contains(StartupNotice.PLAIN_HTTP).contains(StartupNotice.NO_COURSES);
	}
}
