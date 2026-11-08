package dev.codeacademy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import dev.codeacademy.content.ContentFixture;

import static dev.codeacademy.ApiClient.credentials;
import static dev.codeacademy.ApiClient.json;
import static dev.codeacademy.RegistrationApiTest.PASSWORD;
import static dev.codeacademy.RegistrationApiTest.unique;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * One server for the progress tests, synchronized from a copy of the real Java course plus the synthetic demo course:
 * java has all 199 lessons available (java-02.01 keeps its 9 exercises); demo has demo-01.01 (2 exercises) and
 * demo-01.02 available and demo-02.01 pending, the one genuinely pending lesson. Subclasses share this context and its
 * database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ProgressTestSupport {

	static final String JAVA_LESSON = "java-02.01";
	static final String JAVA_PENDING = "java-02.02";
	static final String INTRO = "demo-01.01";
	static final String NEXT = "demo-01.02";
	static final String PENDING = "demo-02.01";
	static final String E01 = "demo-01.01-e01";
	static final String E02 = "demo-01.01-e02";

	/** java and demo side by side, removed when the test JVM exits. */
	static final Path CONTENT_ROOT = copyContent();

	@DynamicPropertySource
	static void content(DynamicPropertyRegistry registry) {
		registry.add("code-academy.content-dir", CONTENT_ROOT::toString);
	}

	@Autowired JdbcTemplate jdbc;
	@LocalServerPort int port;

	/** A new browser with a newly registered, logged-in learner. */
	ApiClient learner() throws Exception {
		return learner(port, unique("Alumno_"));
	}

	static ApiClient learner(int port, String username) throws Exception {
		var browser = new ApiClient(port);
		assertThat(browser.send("POST", "/api/auth/register", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(201);
		login(browser, username);
		return browser;
	}

	static void login(ApiClient browser, String username) throws Exception {
		assertThat(browser.send("POST", "/api/auth/login", credentials(username, PASSWORD)).statusCode())
				.isEqualTo(200);
	}

	/** Fetches the CSRF token up front, so parallel requests of one browser all send the same one. */
	static ApiClient ready(ApiClient browser) throws Exception {
		if (browser.cookie("XSRF-TOKEN").isEmpty()) browser.send("GET", "/actuator/health", null, null);
		assertThat(browser.cookie("XSRF-TOKEN")).isPresent();
		return browser;
	}

	/** Releases every call at the same moment; the responses come back in call order. */
	static List<HttpResponse<String>> parallel(List<Callable<HttpResponse<String>>> calls) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(calls.size());
		try {
			List<Future<HttpResponse<String>>> futures = new ArrayList<>();
			for (var call : calls) {
				futures.add(pool.submit(() -> {
					start.await();
					return call.call();
				}));
			}
			start.countDown();
			List<HttpResponse<String>> responses = new ArrayList<>();
			for (var future : futures) responses.add(future.get(30, TimeUnit.SECONDS));
			return responses;
		}
		finally {
			pool.shutdownNow();
		}
	}

	static String userId(ApiClient browser) throws Exception {
		return ok(browser.get("/api/me")).path("id").asString();
	}

	static HttpResponse<String> lessonState(ApiClient browser, String lessonId, String state) throws Exception {
		return browser.send("PUT", "/api/me/lessons/" + lessonId + "/state", state(state));
	}

	static HttpResponse<String> exerciseState(ApiClient browser, String exerciseId, String state) throws Exception {
		return browser.send("PUT", "/api/me/exercises/" + exerciseId + "/state", state(state));
	}

	static HttpResponse<String> continuation(ApiClient browser, String courseId, String lessonId) throws Exception {
		return browser.send("PUT", "/api/me/courses/" + courseId + "/continuation",
				ApiClient.JSON.createObjectNode().put("lessonId", lessonId).toString());
	}

	static HttpResponse<String> add(ApiClient browser, String courseId) throws Exception {
		return browser.send("PUT", "/api/me/courses/" + courseId, null);
	}

	static HttpResponse<String> progress(ApiClient browser, String courseId) throws Exception {
		return browser.get("/api/me/courses/" + courseId + "/progress");
	}

	static String state(String state) {
		return ApiClient.JSON.createObjectNode().put("state", state).toString();
	}

	/** A 200 JSON response, parsed. */
	static JsonNode ok(HttpResponse<String> response) {
		assertThat(response.statusCode()).as(response.uri() + " " + response.body()).isEqualTo(200);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
		return json(response);
	}

	/** Counts as the contract names them; a null percentage means no available lessons. */
	static void assertCounts(JsonNode progress, int completed, int available, int planned, Double percentage) {
		assertThat(progress.propertyNames()).containsExactlyInAnyOrder("completedLessons", "availableLessons",
				"plannedLessons", "percentage");
		assertThat(progress.path("completedLessons").isInt()).isTrue();
		assertThat(progress.path("completedLessons").asInt()).isEqualTo(completed);
		assertThat(progress.path("availableLessons").asInt()).isEqualTo(available);
		assertThat(progress.path("plannedLessons").asInt()).isEqualTo(planned);
		if (percentage == null) {
			assertThat(progress.path("percentage").isNull()).as(progress.toString()).isTrue();
		}
		else {
			assertThat(progress.path("percentage").isNumber()).as(progress.toString()).isTrue();
			assertThat(progress.path("percentage").asDouble()).isEqualTo(percentage);
		}
	}

	static void assertContinuation(JsonNode continuation, String lessonId) {
		assertThat(continuation.propertyNames()).containsExactly("lastOpenedLessonId");
		if (lessonId == null) assertThat(continuation.path("lastOpenedLessonId").isNull()).isTrue();
		else assertThat(continuation.path("lastOpenedLessonId").asString()).isEqualTo(lessonId);
	}

	/** An Enrollment: courseId, added, continuation and progress, nothing else. */
	static void assertEnrollment(JsonNode enrollment, String courseId, String lastOpenedLessonId) {
		assertThat(enrollment.propertyNames()).containsExactlyInAnyOrder("courseId", "added", "continuation",
				"progress");
		assertThat(enrollment.path("courseId").asString()).isEqualTo(courseId);
		assertThat(enrollment.path("added").asBoolean()).isTrue();
		assertThat(enrollment.path("added").isBoolean()).isTrue();
		assertContinuation(enrollment.path("continuation"), lastOpenedLessonId);
	}

	/** UTC ISO 8601 with a Z offset, for example 2026-01-01T12:00:00Z or with fractional seconds. */
	static Instant utc(JsonNode updatedAt) {
		assertThat(updatedAt.isString()).as(String.valueOf(updatedAt)).isTrue();
		assertThat(updatedAt.asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z");
		return Instant.parse(updatedAt.asString());
	}

	List<Map<String, Object>> snapshot() {
		return TestDatabase.snapshot(jdbc);
	}

	List<Map<String, Object>> enrollmentsOf(String userId) {
		return jdbc.queryForList("select * from code_academy.enrollments where user_id = ?::uuid order by course_id",
				userId);
	}

	/** Every progress row of one user: enrollments, lesson states and exercise states. */
	List<Map<String, Object>> progressRowsOf(String userId) {
		return jdbc.queryForList("""
				select 'enrollment' as kind, e.course_id as item, e.last_opened_lesson_id as value,
				       e.created_at as at, e.id as enrollment_id
				from code_academy.enrollments e where e.user_id = ?::uuid
				union all
				select 'lesson', s.lesson_id, s.state, s.updated_at, s.enrollment_id
				from code_academy.lesson_states s join code_academy.enrollments e on e.id = s.enrollment_id
				where e.user_id = ?::uuid
				union all
				select 'exercise', s.exercise_id, s.state, s.updated_at, s.enrollment_id
				from code_academy.exercise_states s join code_academy.enrollments e on e.id = s.enrollment_id
				where e.user_id = ?::uuid
				order by 1, 2
				""", userId, userId, userId);
	}

	/** Temporarily changes a lesson's availability in the stored catalog, as a later import could. */
	void withAvailability(String lessonId, boolean available, ThrowingRunnable body) throws Exception {
		boolean before = jdbc.queryForObject("select available from code_academy.lessons where id = ?",
				Boolean.class, lessonId);
		jdbc.update("update code_academy.lessons set available = ? where id = ?", available, lessonId);
		try {
			body.run();
		}
		finally {
			jdbc.update("update code_academy.lessons set available = ? where id = ?", before, lessonId);
		}
	}

	interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static Path copyContent() {
		try {
			Path temp = Files.createTempDirectory("code-academy-progress-");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(temp)));
			Path root = ContentFixture.copyInto(temp).root;
			Path java = CatalogApiTest.CONTENT.resolve("java");
			try (Stream<Path> files = Files.walk(java)) {
				for (Path source : files.toList()) {
					Path target = root.resolve("java").resolve(java.relativize(source).toString());
					if (Files.isDirectory(source)) Files.createDirectories(target);
					else Files.copy(source, target);
				}
			}
			return root;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void deleteTree(Path root) {
		try (Stream<Path> files = Files.walk(root)) {
			files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
		catch (IOException ignored) {
			// Best effort at JVM exit; the directory lives in the system temp area.
		}
	}
}
