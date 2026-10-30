package dev.codeacademy;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ProgressTestSupport.ok;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP, synthetic content and a disposable database; each subclass owns its server and temporary copy. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class StageTestSupport {

	StageFixture fixture;
	ConfigurableApplicationContext context;
	JdbcTemplate jdbc;
	int port;

	@BeforeAll
	void start(@TempDir Path temp) throws Exception {
		fixture = new StageFixture(temp);
		String[] args = {"--server.port=0", "--code-academy.content-dir=" + fixture.content.root};
		var application = CodeAcademyApplication.application(args);
		application.addPrimarySources(List.of(TestcontainersConfiguration.class));
		context = application.run(args);
		port = ((WebServerApplicationContext) context).getWebServer().getPort();
		jdbc = context.getBean(JdbcTemplate.class);
	}

	@AfterAll
	void stop() {
		if (context != null) context.close();
	}

	ApiClient learner() throws Exception {
		return ProgressTestSupport.learner(port, RegistrationApiTest.unique("Stage_"));
	}

	static HttpResponse<String> stageProgress(ApiClient browser, String course) throws Exception {
		return browser.get("/api/me/courses/" + course + "/stage-progress");
	}

	static HttpResponse<String> mark(ApiClient browser, String stage, String state) throws Exception {
		return browser.send("PUT", "/api/me/stages/" + stage + "/state", ProgressTestSupport.state(state));
	}

	static HttpResponse<String> open(ApiClient browser, String course, String stage) throws Exception {
		return browser.send("PUT", "/api/me/courses/" + course + "/stage-continuation",
			ApiClient.JSON.createObjectNode().put("stageId", stage).toString());
	}

	static void stageContinuation(JsonNode value, String id) {
		assertThat(value.propertyNames()).containsExactly("lastOpenedStageId");
		if (id == null) assertThat(value.path("lastOpenedStageId").isNull()).isTrue();
		else assertThat(value.path("lastOpenedStageId").asString()).isEqualTo(id);
	}

	static void stageCounts(JsonNode counts, int completed, int available, int planned) {
		assertThat(counts.propertyNames()).containsExactlyInAnyOrder("completedStages", "availableStages",
			"plannedStages", "percentage");
		for (String field : List.of("completedStages", "availableStages", "plannedStages")) {
			assertThat(counts.path(field).isInt()).as(field).isTrue();
		}
		assertThat(counts.path("completedStages").asInt()).isEqualTo(completed);
		assertThat(counts.path("availableStages").asInt()).isEqualTo(available);
		assertThat(counts.path("plannedStages").asInt()).isEqualTo(planned);
		if (available == 0) assertThat(counts.path("percentage").isNull()).isTrue();
		else {
			assertThat(counts.path("percentage").isNumber()).isTrue();
			assertThat(counts.path("percentage").asDouble()).isEqualTo(100.0 * completed / available);
		}
	}

	static void stageProgressShape(JsonNode body, String course, boolean added, String last) {
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("courseId", "added", "stageStates", "continuation", "progress");
		assertThat(body.path("courseId").asString()).isEqualTo(course);
		assertThat(body.path("added").isBoolean()).isTrue();
		assertThat(body.path("added").asBoolean()).isEqualTo(added);
		assertThat(body.path("stageStates").isArray()).isTrue();
		stageContinuation(body.path("continuation"), last);
	}

	static void privateJson(HttpResponse<String> response) {
		assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store")
			.doesNotContain("public");
		assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
	}

	/** Both progress domains and course-list counters, including empty-course defaults, through public routes. */
	static List<JsonNode> personal(ApiClient browser) throws Exception {
		return List.of(ok(stageProgress(browser, "demo")), ok(stageProgress(browser, "other")),
			ok(ProgressTestSupport.progress(browser, "demo")), ok(ProgressTestSupport.progress(browser, "other")),
			ok(browser.get("/api/me/courses")));
	}

	void rejected(ApiClient browser, int status, String code, Request request) throws Exception {
		var publicBefore = personal(browser);
		var before = TestDatabase.snapshot(jdbc);
		var response = request.send();
		assertError(response, status, code);
		if (code.equals("STAGE_UNAVAILABLE")) {
			assertThat(ApiClient.json(response).path("message").asString()).isEqualTo("Stage is not available.");
		}
		assertThat(personal(browser)).isEqualTo(publicBefore);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	interface Request {
		HttpResponse<String> send() throws Exception;
	}
}
