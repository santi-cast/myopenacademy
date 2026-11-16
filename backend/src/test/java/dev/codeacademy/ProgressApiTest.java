package dev.codeacademy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static dev.codeacademy.ApiClient.assertError;
import static org.assertj.core.api.Assertions.assertThat;

/** Enrollment, states, counts and continuation as docs/api.md «Progreso y continuación» describes them. */
class ProgressApiTest extends ProgressTestSupport {

	@Test
	void progressOfANotAddedCourseIsEmptyWithSharedCountsAndWritesNothing() throws Exception {
		var browser = learner();
		var before = snapshot();

		JsonNode ejemplo = ok(progress(browser, EJEMPLO));
		JsonNode demo = ok(progress(browser, "demo"));

		for (JsonNode body : List.of(ejemplo, demo)) {
			assertThat(body.propertyNames()).containsExactlyInAnyOrder("courseId", "added", "lessonStates",
					"exerciseStates", "continuation", "progress");
			assertThat(body.path("added").isBoolean()).isTrue();
			assertThat(body.path("added").asBoolean()).isFalse();
			assertThat(body.path("lessonStates").isArray()).isTrue();
			assertThat(body.path("lessonStates").size()).isZero();
			assertThat(body.path("exerciseStates").isArray()).isTrue();
			assertThat(body.path("exerciseStates").size()).isZero();
			assertContinuation(body.path("continuation"), null);
		}
		assertThat(ejemplo.path("courseId").asString()).isEqualTo(EJEMPLO);
		// One of the two modules of ejemplo is available; the other one is pending.
		assertCounts(ejemplo.path("progress"), 0, 1, 2, 0.0);
		assertThat(demo.path("courseId").asString()).isEqualTo("demo");
		assertCounts(demo.path("progress"), 0, 2, 3, 0.0);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void progressOfAnUnknownCourseIs404() throws Exception {
		var browser = learner();
		var before = snapshot();

		assertError(progress(browser, "python"), 404, "NOT_FOUND");

		assertThat(snapshot()).isEqualTo(before);
	}

	/**
	 * T3b: «Mis cursos» follows the IDs byte by byte, whatever the database collation. java-web and javascript sort
	 * the other way round with en_US.utf8 (postgres:18.6's default), which ignores the hyphen at the first level.
	 * The stored catalog gets two extra courses for this test only, as a later import could add them.
	 */
	@Test
	void myCoursesListTheFirstCourseAndThenTheOthersByTitle() throws Exception {
		// Author's decision (16 November 2026): first the configured first course (code-academy.first-course, «inicial»),
		// then by title as Spanish sorts it (Á with A, whatever the database collation), and by ID when titles tie.
		var browser = learner();
		String user = userId(browser);
		Map<String, String> titles = Map.of("javascript", "Ábaco", "java-web", "Álgebra y Java", "inicial", "Zeta: el curso base",
				"b-igual", "Mismo título", "a-igual", "Mismo título");
		List<String> ids = List.copyOf(titles.keySet());
		try {
			for (String id : ids) {
				jdbc.update("insert into code_academy.courses (id, title) values (?, ?)", id, titles.get(id));
				jdbc.update("insert into code_academy.enrollments (user_id, course_id, created_at) values (?::uuid, ?, now())",
						user, id);
			}
			JsonNode courses = ok(browser.get("/api/me/courses")).path("courses");
			List<String> listed = new java.util.ArrayList<>();
			courses.forEach(course -> listed.add(course.path("course").path("id").asString()));

			assertThat(listed).containsExactly("inicial", "javascript", "java-web", "a-igual", "b-igual");
		}
		finally {
			for (String id : ids) {
				jdbc.update("delete from code_academy.enrollments where course_id = ?", id);
				jdbc.update("delete from code_academy.courses where id = ?", id);
			}
		}
	}

	@Test
	void myCoursesListsOnlyAddedCoursesByTitle() throws Exception {
		var browser = learner();
		var before = snapshot();
		JsonNode empty = ok(browser.get("/api/me/courses"));
		assertThat(empty.propertyNames()).containsExactly("courses");
		assertThat(empty.path("courses").isArray()).isTrue();
		assertThat(empty.path("courses").size()).isZero();
		assertThat(snapshot()).isEqualTo(before);

		ok(lessonState(browser, EJEMPLO_LESSON, "completed"));
		ok(add(browser, "demo"));
		var added = snapshot();
		JsonNode courses = ok(browser.get("/api/me/courses")).path("courses");

		assertThat(courses.size()).isEqualTo(2);
		// «Curso de ejemplo» before «Demo»: by title.
		JsonNode demo = courses.get(1);
		assertThat(demo.propertyNames()).containsExactlyInAnyOrder("course", "enrollment");
		assertThat(demo.path("course").propertyNames()).containsExactlyInAnyOrder("id", "title");
		assertThat(demo.path("course").path("id").asString()).isEqualTo("demo");
		assertThat(demo.path("course").path("title").asString()).isEqualTo("Demo");
		assertEnrollment(demo.path("enrollment"), "demo", null);
		assertCounts(demo.path("enrollment").path("progress"), 0, 2, 3, 0.0);
		JsonNode ejemplo = courses.get(0);
		assertThat(ejemplo.path("course").path("id").asString()).isEqualTo(EJEMPLO);
		assertThat(ejemplo.path("course").path("title").asString()).isEqualTo("Curso de ejemplo");
		assertEnrollment(ejemplo.path("enrollment"), EJEMPLO, EJEMPLO_LESSON);
		assertCounts(ejemplo.path("enrollment").path("progress"), 1, 1, 2, 100.0);
		assertThat(snapshot()).as("GET writes nothing").isEqualTo(added);
	}

	@Test
	void markingALessonOfANotAddedCourseAddsItWithThatLessonAsContinuation() throws Exception {
		var browser = learner();
		String user = userId(browser);

		JsonNode result = ok(lessonState(browser, NEXT, "completed"));

		assertThat(result.propertyNames()).containsExactlyInAnyOrder("courseId", "lessonId", "state", "updatedAt",
				"courseAutoAdded", "continuation", "progress");
		assertThat(result.path("courseId").asString()).isEqualTo("demo");
		assertThat(result.path("lessonId").asString()).isEqualTo(NEXT);
		assertThat(result.path("state").asString()).isEqualTo("completed");
		assertThat(result.path("courseAutoAdded").isBoolean()).isTrue();
		assertThat(result.path("courseAutoAdded").asBoolean()).isTrue();
		assertContinuation(result.path("continuation"), NEXT);
		assertCounts(result.path("progress"), 1, 2, 3, 50.0);
		Instant updatedAt = utc(result.path("updatedAt"));

		var enrollments = enrollmentsOf(user);
		assertThat(enrollments).hasSize(1);
		assertThat(enrollments.getFirst().get("course_id")).isEqualTo("demo");
		assertThat(enrollments.getFirst().get("last_opened_lesson_id")).isEqualTo(NEXT);
		var states = jdbc.queryForList("select course_id, lesson_id, state from code_academy.lesson_states "
				+ "where enrollment_id = ?", enrollments.getFirst().get("id"));
		assertThat(states).containsExactly(Map.of("course_id", "demo", "lesson_id", NEXT, "state", "completed"));
		assertThat(jdbc.queryForObject("select updated_at from code_academy.lesson_states where enrollment_id = ?",
				java.time.OffsetDateTime.class, enrollments.getFirst().get("id")).toInstant()).isEqualTo(updatedAt);

		// The enrollment exists now: another mark neither re-adds nor moves the continuation.
		JsonNode second = ok(lessonState(browser, INTRO, "completed"));
		assertThat(second.path("courseAutoAdded").asBoolean()).isFalse();
		assertContinuation(second.path("continuation"), NEXT);
		assertCounts(second.path("progress"), 2, 2, 3, 100.0);
		assertThat(enrollmentsOf(user)).hasSize(1);
		assertThat(enrollmentsOf(user).getFirst().get("last_opened_lesson_id")).isEqualTo(NEXT);
	}

	@Test
	void markingAnExerciseOfANotAddedCourseAddsItWithTheExerciseLessonAsContinuation() throws Exception {
		var browser = learner();
		String user = userId(browser);

		JsonNode result = ok(exerciseState(browser, E02, "in_progress"));

		assertThat(result.propertyNames()).containsExactlyInAnyOrder("courseId", "exerciseId", "state", "updatedAt",
				"courseAutoAdded", "continuation", "progress");
		assertThat(result.path("courseId").asString()).isEqualTo("demo");
		assertThat(result.path("exerciseId").asString()).isEqualTo(E02);
		assertThat(result.path("state").asString()).isEqualTo("in_progress");
		assertThat(result.path("courseAutoAdded").asBoolean()).isTrue();
		assertContinuation(result.path("continuation"), INTRO);
		assertCounts(result.path("progress"), 0, 2, 3, 0.0);
		utc(result.path("updatedAt"));

		assertThat(enrollmentsOf(user)).singleElement()
			.satisfies(row -> assertThat(row.get("last_opened_lesson_id")).isEqualTo(INTRO));
		assertThat(jdbc.queryForList("select s.course_id, s.exercise_id, s.state from code_academy.exercise_states s "
				+ "join code_academy.enrollments e on e.id = s.enrollment_id where e.user_id = ?::uuid", user))
			.containsExactly(Map.of("course_id", "demo", "exercise_id", E02, "state", "in_progress"));
		assertThat(jdbc.queryForObject("select count(*) from code_academy.lesson_states s join code_academy.enrollments e "
				+ "on e.id = s.enrollment_id where e.user_id = ?::uuid", Integer.class, user)).isZero();

		JsonNode again = ok(exerciseState(browser, E01, "solved"));
		assertThat(again.path("courseAutoAdded").asBoolean()).isFalse();
		assertContinuation(again.path("continuation"), INTRO);
	}

	@Test
	void everyContractStateIsAcceptedAndCorrectableWithoutSequence() throws Exception {
		var browser = learner();
		for (String state : List.of("completed", "not_completed", "completed")) {
			assertThat(ok(lessonState(browser, INTRO, state)).path("state").asString()).isEqualTo(state);
		}
		for (String state : List.of("solved", "pending", "in_progress", "solved", "pending")) {
			assertThat(ok(exerciseState(browser, E01, state)).path("state").asString()).isEqualTo(state);
		}
		JsonNode body = ok(progress(browser, "demo"));
		assertThat(body.path("lessonStates").size()).isEqualTo(1);
		assertThat(body.path("lessonStates").get(0).path("state").asString()).isEqualTo("completed");
		assertThat(body.path("exerciseStates").size()).isEqualTo(1);
		assertThat(body.path("exerciseStates").get(0).path("state").asString()).isEqualTo("pending");
	}

	@Test
	void repeatingTheSameStateKeepsUpdatedAtAndChangesNothing() throws Exception {
		var browser = learner();
		Instant lessonAt = utc(ok(lessonState(browser, INTRO, "completed")).path("updatedAt"));
		Instant exerciseAt = utc(ok(exerciseState(browser, E01, "in_progress")).path("updatedAt"));
		var before = snapshot();

		JsonNode lessonAgain = ok(lessonState(browser, INTRO, "completed"));
		JsonNode exerciseAgain = ok(exerciseState(browser, E01, "in_progress"));

		assertThat(utc(lessonAgain.path("updatedAt"))).isEqualTo(lessonAt);
		assertThat(lessonAgain.path("courseAutoAdded").asBoolean()).isFalse();
		assertThat(utc(exerciseAgain.path("updatedAt"))).isEqualTo(exerciseAt);
		assertThat(snapshot()).isEqualTo(before);

		Instant changed = utc(ok(lessonState(browser, INTRO, "not_completed")).path("updatedAt"));
		assertThat(changed).isAfter(lessonAt);
		Instant exerciseChanged = utc(ok(exerciseState(browser, E01, "solved")).path("updatedAt"));
		assertThat(exerciseChanged).isAfter(exerciseAt);
	}

	@Test
	void progressOfAnAddedCourseReturnsTheSavedStatesInSyllabusOrder() throws Exception {
		var browser = learner();
		JsonNode intro = ok(lessonState(browser, INTRO, "completed"));
		JsonNode next = ok(lessonState(browser, NEXT, "not_completed"));
		JsonNode e02 = ok(exerciseState(browser, E02, "solved"));
		JsonNode e01 = ok(exerciseState(browser, E01, "in_progress"));
		var before = snapshot();

		JsonNode body = ok(progress(browser, "demo"));

		assertThat(body.path("courseId").asString()).isEqualTo("demo");
		assertThat(body.path("added").asBoolean()).isTrue();
		assertContinuation(body.path("continuation"), INTRO);
		assertCounts(body.path("progress"), 1, 2, 3, 50.0);
		JsonNode lessons = body.path("lessonStates");
		assertThat(lessons.size()).isEqualTo(2);
		assertThat(lessons.get(0).propertyNames()).containsExactlyInAnyOrder("lessonId", "state", "updatedAt");
		assertThat(lessons.get(0).path("lessonId").asString()).isEqualTo(INTRO);
		assertThat(lessons.get(0).path("state").asString()).isEqualTo("completed");
		assertThat(lessons.get(0).path("updatedAt")).isEqualTo(intro.path("updatedAt"));
		assertThat(lessons.get(1).path("lessonId").asString()).isEqualTo(NEXT);
		assertThat(lessons.get(1).path("state").asString()).isEqualTo("not_completed");
		assertThat(lessons.get(1).path("updatedAt")).isEqualTo(next.path("updatedAt"));
		JsonNode exercises = body.path("exerciseStates");
		assertThat(exercises.size()).isEqualTo(2);
		assertThat(exercises.get(0).propertyNames()).containsExactlyInAnyOrder("exerciseId", "state", "updatedAt");
		assertThat(exercises.get(0).path("exerciseId").asString()).isEqualTo(E01);
		assertThat(exercises.get(0).path("state").asString()).isEqualTo("in_progress");
		assertThat(exercises.get(0).path("updatedAt")).isEqualTo(e01.path("updatedAt"));
		assertThat(exercises.get(1).path("exerciseId").asString()).isEqualTo(E02);
		assertThat(exercises.get(1).path("state").asString()).isEqualTo("solved");
		assertThat(exercises.get(1).path("updatedAt")).isEqualTo(e02.path("updatedAt"));
		assertThat(snapshot()).as("GET writes nothing").isEqualTo(before);
	}

	@Test
	void countsComeFromTheStoredCatalogAndOnlyCountAvailableLessons() throws Exception {
		var browser = learner();
		ok(lessonState(browser, INTRO, "completed"));
		ok(lessonState(browser, NEXT, "completed"));
		assertCounts(ok(progress(browser, "demo")).path("progress"), 2, 2, 3, 100.0);

		// A later import may make a lesson pending: its saved state stays but no longer counts.
		withAvailability(NEXT, false, () -> {
			JsonNode body = ok(progress(browser, "demo"));
			assertCounts(body.path("progress"), 1, 1, 3, 100.0);
			assertThat(body.path("lessonStates").size()).isEqualTo(2);
			withAvailability(INTRO, false, () -> {
				assertCounts(ok(progress(browser, "demo")).path("progress"), 0, 0, 3, null);
				assertCounts(ok(browser.get("/api/me/courses")).path("courses").get(0).path("enrollment")
					.path("progress"), 0, 0, 3, null);
			});
		});
		ok(lessonState(browser, NEXT, "not_completed"));
		assertCounts(ok(progress(browser, "demo")).path("progress"), 1, 2, 3, 50.0);
	}

	@Test
	void percentageIsNotRounded() throws Exception {
		var browser = learner();
		ok(lessonState(browser, INTRO, "completed"));
		jdbc.update("update code_academy.lessons set available = true, source_available = true where id = ?", PENDING);
		try {
			assertCounts(ok(progress(browser, "demo")).path("progress"), 1, 3, 3, 100.0 / 3);
		}
		finally {
			jdbc.update("update code_academy.lessons set available = false, source_available = false where id = ?",
					PENDING);
		}
	}

	@Test
	void explicitAddCreatesAnEmptyEnrollmentAndRepeatingItChangesNothing() throws Exception {
		var browser = learner();
		String user = userId(browser);

		JsonNode enrollment = ok(add(browser, "demo"));

		assertEnrollment(enrollment, "demo", null);
		assertCounts(enrollment.path("progress"), 0, 2, 3, 0.0);
		assertThat(progressRowsOf(user)).singleElement().satisfies(row -> {
			assertThat(row.get("item")).isEqualTo("demo");
			assertThat(row.get("value")).isNull();
		});
		JsonNode body = ok(progress(browser, "demo"));
		assertThat(body.path("added").asBoolean()).isTrue();
		assertThat(body.path("lessonStates").size()).isZero();
		assertThat(body.path("exerciseStates").size()).isZero();
		var before = snapshot();

		JsonNode again = ok(add(browser, "demo"));

		assertThat(again).isEqualTo(enrollment);
		assertThat(snapshot()).isEqualTo(before);

		// Marking after an explicit add keeps the empty continuation and reports no automatic addition.
		JsonNode marked = ok(lessonState(browser, INTRO, "completed"));
		assertThat(marked.path("courseAutoAdded").asBoolean()).isFalse();
		assertContinuation(marked.path("continuation"), null);
	}

	@Test
	void repeatingTheAddNeverResetsStatesOrContinuation() throws Exception {
		var browser = learner();
		ok(lessonState(browser, INTRO, "completed"));
		ok(exerciseState(browser, E01, "solved"));
		ok(continuation(browser, "demo", NEXT));
		var before = snapshot();

		JsonNode enrollment = ok(add(browser, "demo"));

		assertEnrollment(enrollment, "demo", NEXT);
		assertCounts(enrollment.path("progress"), 1, 2, 3, 50.0);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void explicitAddOfAnUnknownCourseIs404() throws Exception {
		var browser = learner();
		var before = snapshot();

		assertError(add(browser, "python"), 404, "NOT_FOUND");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void continuationOfAnAddedCourseIsConfirmedAndMarkingDoesNotMoveIt() throws Exception {
		var browser = learner();
		String user = userId(browser);
		ok(add(browser, "demo"));

		JsonNode enrollment = ok(continuation(browser, "demo", NEXT));

		assertEnrollment(enrollment, "demo", NEXT);
		assertCounts(enrollment.path("progress"), 0, 2, 3, 0.0);
		assertThat(enrollmentsOf(user).getFirst().get("last_opened_lesson_id")).isEqualTo(NEXT);

		JsonNode marked = ok(exerciseState(browser, E01, "solved"));
		assertContinuation(marked.path("continuation"), NEXT);
		assertThat(enrollmentsOf(user).getFirst().get("last_opened_lesson_id")).isEqualTo(NEXT);

		assertEnrollment(ok(continuation(browser, "demo", INTRO)), "demo", INTRO);
		var before = snapshot();
		assertEnrollment(ok(continuation(browser, "demo", INTRO)), "demo", INTRO);
		assertThat(snapshot()).isEqualTo(before);
		assertContinuation(ok(progress(browser, "demo")).path("continuation"), INTRO);
	}

	@Test
	void continuationOfANotAddedCourseIsCourseNotAddedWithoutRows() throws Exception {
		var browser = learner();
		var before = snapshot();

		assertError(continuation(browser, "demo", INTRO), 409, "COURSE_NOT_ADDED");
		assertError(continuation(browser, EJEMPLO, EJEMPLO_LESSON), 409, "COURSE_NOT_ADDED");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void continuationToALessonOfAnotherCourseIsCourseMismatch() throws Exception {
		var browser = learner();
		ok(add(browser, "demo"));
		ok(continuation(browser, "demo", INTRO));
		var before = snapshot();

		assertError(continuation(browser, "demo", EJEMPLO_LESSON), 400, "COURSE_MISMATCH");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void continuationToAPendingLessonIsLessonUnavailable() throws Exception {
		var browser = learner();
		ok(add(browser, "demo"));
		ok(add(browser, EJEMPLO));
		var before = snapshot();

		assertError(continuation(browser, "demo", PENDING), 409, "LESSON_UNAVAILABLE");

		assertThat(snapshot()).isEqualTo(before);
		// The module that curso.json declares not available yet is pending in the imported course too.
		assertError(continuation(browser, EJEMPLO, EJEMPLO_PENDING), 409, "LESSON_UNAVAILABLE");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void continuationWithAnUnknownLessonOrCourseIs404() throws Exception {
		var browser = learner();
		ok(add(browser, "demo"));
		var before = snapshot();

		assertError(continuation(browser, "demo", "demo.09.09"), 404, "NOT_FOUND");
		assertError(continuation(browser, "python", INTRO), 404, "NOT_FOUND");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void stateOfAPendingLessonIsLessonUnavailableAndAddsNothing() throws Exception {
		var browser = learner();
		var before = snapshot();

		for (String state : List.of("completed", "not_completed")) {
			assertError(lessonState(browser, PENDING, state), 409, "LESSON_UNAVAILABLE");
		}

		assertThat(snapshot()).isEqualTo(before);
		// The module that curso.json declares not available yet is pending in the imported course too.
		for (String state : List.of("completed", "not_completed")) {
			assertError(lessonState(browser, EJEMPLO_PENDING, state), 409, "LESSON_UNAVAILABLE");
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void stateOfAnExerciseOfAPendingLessonIsLessonUnavailableAndAddsNothing() throws Exception {
		// Unreachable through an import (a pending lesson has no exercises and a stored exercise cannot disappear),
		// so the stored catalog is changed directly to prove the defense.
		var browser = learner();
		withAvailability(INTRO, false, () -> {
			var before = snapshot();
			for (String state : List.of("pending", "in_progress", "solved")) {
				assertError(exerciseState(browser, E01, state), 409, "LESSON_UNAVAILABLE");
			}
			assertThat(snapshot()).isEqualTo(before);
		});
	}

	@Test
	void unknownLessonOrExerciseIs404AndAddsNothing() throws Exception {
		var browser = learner();
		var before = snapshot();

		assertError(lessonState(browser, "demo.09.09", "completed"), 404, "NOT_FOUND");
		assertError(exerciseState(browser, "demo.01.01.e99", "solved"), 404, "NOT_FOUND");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void writesThatCannotAnswerJsonAreRejectedBeforeRunning() throws Exception {
		var browser = learner();
		var before = snapshot();

		assertError(browser.sendAccepting("PUT", "/api/me/lessons/" + INTRO + "/state", state("completed"),
				"text/html"), 406, "NOT_ACCEPTABLE");
		assertError(browser.sendAccepting("PUT", "/api/me/exercises/" + E01 + "/state", state("solved"),
				"application/xml"), 406, "NOT_ACCEPTABLE");
		assertError(browser.sendAccepting("PUT", "/api/me/courses/demo", null, "text/html"), 406, "NOT_ACCEPTABLE");
		assertError(browser.sendAccepting("GET", "/api/me/courses", null, "text/html"), 406, "NOT_ACCEPTABLE");

		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void thereIsNoRouteToRemoveACourseOrAState() throws Exception {
		var browser = learner();
		ok(lessonState(browser, INTRO, "completed"));
		var before = snapshot();

		for (String path : List.of("/api/me/courses/demo", "/api/me/lessons/" + INTRO + "/state",
				"/api/me/exercises/" + E01 + "/state", "/api/me/courses/demo/continuation")) {
			assertError(browser.send("DELETE", path, null), 403, "ACCESS_DENIED");
		}
		assertError(browser.send("POST", "/api/me/courses/demo", null), 403, "ACCESS_DENIED");
		assertError(browser.send("PATCH", "/api/me/lessons/" + INTRO + "/state", state("completed")), 403,
				"ACCESS_DENIED");

		assertThat(snapshot()).isEqualTo(before);
	}
}
