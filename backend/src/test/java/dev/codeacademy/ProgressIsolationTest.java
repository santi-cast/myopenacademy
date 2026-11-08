package dev.codeacademy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Two learners on the same courses: each reads and writes only their own progress, chosen by the session alone. */
class ProgressIsolationTest extends ProgressTestSupport {

	@Test
	void eachLearnerSeesOnlyTheirOwnStatesAndContinuation() throws Exception {
		var ana = learner();
		var bruno = learner();

		ok(lessonState(ana, INTRO, "completed"));
		ok(exerciseState(ana, E01, "solved"));
		ok(continuation(ana, "demo", NEXT));
		ok(lessonState(bruno, NEXT, "not_completed"));
		ok(exerciseState(bruno, E02, "in_progress"));
		ok(lessonState(bruno, JAVA_LESSON, "completed"));

		JsonNode anaDemo = ok(progress(ana, "demo"));
		assertThat(anaDemo.path("lessonStates").size()).isEqualTo(1);
		assertThat(anaDemo.path("lessonStates").get(0).path("lessonId").asString()).isEqualTo(INTRO);
		assertThat(anaDemo.path("lessonStates").get(0).path("state").asString()).isEqualTo("completed");
		assertThat(anaDemo.path("exerciseStates").size()).isEqualTo(1);
		assertThat(anaDemo.path("exerciseStates").get(0).path("exerciseId").asString()).isEqualTo(E01);
		assertThat(anaDemo.path("exerciseStates").get(0).path("state").asString()).isEqualTo("solved");
		assertContinuation(anaDemo.path("continuation"), NEXT);
		assertCounts(anaDemo.path("progress"), 1, 2, 3, 50.0);
		JsonNode anaJava = ok(progress(ana, "java"));
		assertThat(anaJava.path("added").asBoolean()).isFalse();
		assertThat(anaJava.path("lessonStates").size()).isZero();
		assertCounts(anaJava.path("progress"), 0, 199, 199, 0.0);
		JsonNode anaCourses = ok(ana.get("/api/me/courses")).path("courses");
		assertThat(anaCourses.size()).isEqualTo(1);
		assertThat(anaCourses.get(0).path("course").path("id").asString()).isEqualTo("demo");

		JsonNode brunoDemo = ok(progress(bruno, "demo"));
		assertThat(brunoDemo.path("lessonStates").size()).isEqualTo(1);
		assertThat(brunoDemo.path("lessonStates").get(0).path("lessonId").asString()).isEqualTo(NEXT);
		assertThat(brunoDemo.path("lessonStates").get(0).path("state").asString()).isEqualTo("not_completed");
		assertThat(brunoDemo.path("exerciseStates").size()).isEqualTo(1);
		assertThat(brunoDemo.path("exerciseStates").get(0).path("exerciseId").asString()).isEqualTo(E02);
		assertContinuation(brunoDemo.path("continuation"), NEXT);
		assertCounts(brunoDemo.path("progress"), 0, 2, 3, 0.0);
		JsonNode brunoJava = ok(progress(bruno, "java"));
		assertThat(brunoJava.path("added").asBoolean()).isTrue();
		assertCounts(brunoJava.path("progress"), 1, 199, 199, 100.0 / 199);
		assertThat(ok(bruno.get("/api/me/courses")).path("courses").size()).isEqualTo(2);
	}

	@Test
	void oneLearnersWritesNeverChangeAnotherLearnersRows() throws Exception {
		var ana = learner();
		String anaId = userId(ana);
		ok(lessonState(ana, INTRO, "completed"));
		ok(exerciseState(ana, E01, "solved"));
		ok(continuation(ana, "demo", NEXT));
		var anaRows = progressRowsOf(anaId);
		JsonNode anaProgress = ok(progress(ana, "demo"));

		var bruno = learner();
		String brunoId = userId(bruno);
		// The same lesson and exercise, opposite states, every write route and repeated operations.
		ok(lessonState(bruno, INTRO, "not_completed"));
		ok(exerciseState(bruno, E01, "pending"));
		ok(exerciseState(bruno, E02, "solved"));
		ok(add(bruno, "demo"));
		ok(continuation(bruno, "demo", INTRO));
		ok(lessonState(bruno, INTRO, "completed"));
		ok(lessonState(bruno, INTRO, "not_completed"));
		ok(add(bruno, "java"));

		assertThat(progressRowsOf(anaId)).isEqualTo(anaRows);
		assertThat(ok(progress(ana, "demo"))).isEqualTo(anaProgress);
		assertThat(progressRowsOf(brunoId)).extracting(row -> row.get("enrollment_id"))
			.doesNotContainAnyElementsOf(anaRows.stream().map(row -> row.get("enrollment_id")).toList());
		assertThat(enrollmentsOf(brunoId)).hasSize(2);
	}
}
