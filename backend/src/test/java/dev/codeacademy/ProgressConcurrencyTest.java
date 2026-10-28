package dev.codeacademy;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Simultaneous first marks of one learner on a course not yet added: one enrollment, every request succeeds. */
class ProgressConcurrencyTest extends ProgressTestSupport {

	private static final int REQUESTS = 8;

	@RepeatedTest(3)
	void parallelFirstMarksCreateExactlyOneEnrollment() throws Exception {
		var browser = learner();
		String user = userId(browser);
		// Fetch the CSRF token up front so every parallel request sends the same one.
		ok(progress(browser, "java"));
		browser.send("GET", "/actuator/health", null, null);

		List<HttpResponse<String>> responses = new ArrayList<>();
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
		try {
			List<Future<HttpResponse<String>>> futures = new ArrayList<>();
			for (int i = 1; i <= REQUESTS; i++) {
				String exercise = "java-02.01-e0" + i;
				futures.add(pool.submit(() -> {
					start.await();
					return exerciseState(browser, exercise, "in_progress");
				}));
			}
			start.countDown();
			for (var future : futures) responses.add(future.get(30, TimeUnit.SECONDS));
		}
		finally {
			pool.shutdownNow();
		}

		List<JsonNode> results = responses.stream().map(ProgressTestSupport::ok).toList();
		assertThat(results).filteredOn(result -> result.path("courseAutoAdded").asBoolean()).hasSize(1);
		assertThat(results).allSatisfy(result -> {
			assertThat(result.path("courseId").asString()).isEqualTo("java");
			assertThat(result.path("state").asString()).isEqualTo("in_progress");
			assertContinuation(result.path("continuation"), JAVA_LESSON);
		});
		var enrollments = enrollmentsOf(user);
		assertThat(enrollments).hasSize(1);
		assertThat(enrollments.getFirst().get("last_opened_lesson_id")).isEqualTo(JAVA_LESSON);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.exercise_states where enrollment_id = ?",
				Integer.class, enrollments.getFirst().get("id"))).isEqualTo(REQUESTS);
		JsonNode progress = ok(progress(browser, "java"));
		assertThat(progress.path("exerciseStates").size()).isEqualTo(REQUESTS);
	}
}
