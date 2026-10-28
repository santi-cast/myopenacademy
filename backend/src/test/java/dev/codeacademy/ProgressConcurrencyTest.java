package dev.codeacademy;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.RepeatedTest;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simultaneous requests of one learner, and of two learners on the same exercise: one enrollment per learner, every
 * request succeeds and the stored result is one the requests asked for. Each repetition uses fresh learners.
 */
class ProgressConcurrencyTest extends ProgressTestSupport {

	private static final int REQUESTS = 8;

	@RepeatedTest(3)
	void parallelFirstMarksCreateExactlyOneEnrollment() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);

		List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
		for (int i = 1; i <= REQUESTS; i++) {
			String exercise = "java-02.01-e0" + i;
			calls.add(() -> exerciseState(browser, exercise, "in_progress"));
		}
		List<JsonNode> results = parallel(calls).stream().map(ProgressTestSupport::ok).toList();

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

	/**
	 * Explicit adds racing first marks: whichever commits first creates the one enrollment. Only a mark that created
	 * it reports courseAutoAdded and sets the initial continuation; if an add won, the continuation stays empty.
	 */
	@RepeatedTest(4)
	void explicitAddRacingFirstMarksCreatesOneEnrollment() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);

		var responses = parallel(List.of(() -> add(browser, "demo"), () -> lessonState(browser, INTRO, "completed"),
				() -> exerciseState(browser, E01, "solved"), () -> add(browser, "demo")));
		List<JsonNode> results = responses.stream().map(ProgressTestSupport::ok).toList();
		List<JsonNode> adds = List.of(results.get(0), results.get(3));
		List<JsonNode> marks = List.of(results.get(1), results.get(2));

		var enrollments = enrollmentsOf(user);
		assertThat(enrollments).hasSize(1);
		Object continuation = enrollments.getFirst().get("last_opened_lesson_id");
		long autoAdded = marks.stream().filter(mark -> mark.path("courseAutoAdded").asBoolean()).count();
		if (autoAdded == 1) assertThat(continuation).isEqualTo(INTRO);
		else {
			assertThat(autoAdded).isZero();
			assertThat(continuation).isNull();
		}
		String expected = (String) continuation;
		adds.forEach(enrollment -> assertEnrollment(enrollment, "demo", expected));
		marks.forEach(mark -> assertContinuation(mark.path("continuation"), expected));
		assertThat(marks.get(0).path("state").asString()).isEqualTo("completed");
		assertThat(marks.get(1).path("state").asString()).isEqualTo("solved");
		JsonNode progress = ok(progress(browser, "demo"));
		assertContinuation(progress.path("continuation"), expected);
		assertThat(progress.path("lessonStates").size()).isEqualTo(1);
		assertThat(progress.path("exerciseStates").size()).isEqualTo(1);
	}

	/** Parallel continuation PUTs: all succeed, and the one stored continuation is one of the requested lessons. */
	@RepeatedTest(3)
	void parallelContinuationsKeepOneOfTheRequestedLessons() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);
		ok(add(browser, "demo"));

		List<String> requested = List.of(INTRO, NEXT, INTRO, NEXT, INTRO, NEXT);
		List<Callable<HttpResponse<String>>> calls = new ArrayList<>();
		for (String lesson : requested) calls.add(() -> continuation(browser, "demo", lesson));
		var responses = parallel(calls);

		for (int i = 0; i < requested.size(); i++) {
			assertEnrollment(ok(responses.get(i)), "demo", requested.get(i));
		}
		var enrollments = enrollmentsOf(user);
		assertThat(enrollments).hasSize(1);
		Object stored = enrollments.getFirst().get("last_opened_lesson_id");
		assertThat(stored).isIn(INTRO, NEXT);
		assertContinuation(ok(progress(browser, "demo")).path("continuation"), (String) stored);
	}

	/** Two learners mark the same exercise at once with different states: each keeps their own. */
	@RepeatedTest(3)
	void twoLearnersMarkingTheSameExerciseKeepTheirOwnStates() throws Exception {
		var ana = ready(learner());
		var bruno = ready(learner());

		var responses = parallel(List.of(() -> exerciseState(ana, E01, "solved"),
				() -> exerciseState(bruno, E01, "in_progress"), () -> exerciseState(ana, E01, "solved"),
				() -> exerciseState(bruno, E01, "in_progress")));
		List<JsonNode> results = responses.stream().map(ProgressTestSupport::ok).toList();

		assertThat(List.of(results.get(0), results.get(2))).filteredOn(r -> r.path("courseAutoAdded").asBoolean())
			.hasSize(1);
		assertThat(List.of(results.get(1), results.get(3))).filteredOn(r -> r.path("courseAutoAdded").asBoolean())
			.hasSize(1);
		assertThat(results.get(0).path("state").asString()).isEqualTo("solved");
		assertThat(results.get(2).path("state").asString()).isEqualTo("solved");
		assertThat(results.get(1).path("state").asString()).isEqualTo("in_progress");
		assertThat(results.get(3).path("state").asString()).isEqualTo("in_progress");
		results.forEach(result -> assertContinuation(result.path("continuation"), INTRO));

		for (var learner : List.of(Map.entry(ana, "solved"), Map.entry(bruno, "in_progress"))) {
			String user = userId(learner.getKey());
			assertThat(enrollmentsOf(user)).hasSize(1);
			assertThat(progressRowsOf(user)).filteredOn(row -> "exercise".equals(row.get("kind")))
				.singleElement()
				.satisfies(row -> {
					assertThat(row.get("item")).isEqualTo(E01);
					assertThat(row.get("value")).isEqualTo(learner.getValue());
				});
			JsonNode states = ok(progress(learner.getKey(), "demo")).path("exerciseStates");
			assertThat(states.size()).isEqualTo(1);
			assertThat(states.get(0).path("state").asString()).isEqualTo(learner.getValue());
		}
	}
}
