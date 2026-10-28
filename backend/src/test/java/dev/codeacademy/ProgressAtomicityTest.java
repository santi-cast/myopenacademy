package dev.codeacademy;

import org.junit.jupiter.api.Test;

import static dev.codeacademy.ApiClient.assertError;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failure anywhere inside a progress write, including at COMMIT, answers 500 INTERNAL_ERROR and leaves the whole
 * database as it was: never an enrollment without its state, nor half of a mark. Failures are injected with
 * PostgreSQL triggers that exist only while each case runs.
 */
class ProgressAtomicityTest extends ProgressTestSupport {

	private static final String TRIGGER = "progress_test_failure";

	@Test
	void failingStateInsertAfterTheEnrollmentInsertLeavesNothing() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);
		var before = snapshot();

		whileFailing("lesson_states", "create trigger " + TRIGGER + """
				 before insert on code_academy.lesson_states for each row
				when (new.lesson_id = 'demo-01.02') execute function code_academy.progress_test_fail()
				""", () -> assertError(lessonState(browser, NEXT, "completed"), 500, "INTERNAL_ERROR"));
		whileFailing("exercise_states", "create trigger " + TRIGGER + """
				 before insert on code_academy.exercise_states for each row
				when (new.exercise_id = 'demo-01.01-e02') execute function code_academy.progress_test_fail()
				""", () -> assertError(exerciseState(browser, E02, "solved"), 500, "INTERNAL_ERROR"));

		assertThat(snapshot()).isEqualTo(before);
		assertThat(enrollmentsOf(user)).isEmpty();
		assertThat(ok(lessonState(browser, NEXT, "completed")).path("courseAutoAdded").asBoolean()).isTrue();
	}

	@Test
	void failureAtCommitOnAStateLeavesNothing() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);
		var before = snapshot();

		whileFailing("lesson_states", "create constraint trigger " + TRIGGER + """
				 after insert on code_academy.lesson_states deferrable initially deferred for each row
				when (new.lesson_id = 'demo-01.02') execute function code_academy.progress_test_fail()
				""", () -> assertError(lessonState(browser, NEXT, "completed"), 500, "INTERNAL_ERROR"));
		whileFailing("exercise_states", "create constraint trigger " + TRIGGER + """
				 after insert on code_academy.exercise_states deferrable initially deferred for each row
				when (new.exercise_id = 'demo-01.01-e02') execute function code_academy.progress_test_fail()
				""", () -> assertError(exerciseState(browser, E02, "solved"), 500, "INTERNAL_ERROR"));

		assertThat(snapshot()).isEqualTo(before);
		assertThat(enrollmentsOf(user)).isEmpty();
		assertThat(ok(exerciseState(browser, E02, "solved")).path("courseAutoAdded").asBoolean()).isTrue();
	}

	@Test
	void failureAtCommitOnTheEnrollmentLeavesNothing() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);
		var before = snapshot();

		whileFailing("enrollments", "create constraint trigger " + TRIGGER + """
				 after insert on code_academy.enrollments deferrable initially deferred for each row
				when (new.user_id = '%s'::uuid) execute function code_academy.progress_test_fail()
				""".formatted(user), () -> {
			assertError(add(browser, "demo"), 500, "INTERNAL_ERROR");
			assertError(exerciseState(browser, E01, "solved"), 500, "INTERNAL_ERROR");
		});

		assertThat(snapshot()).isEqualTo(before);
		assertThat(enrollmentsOf(user)).isEmpty();
		assertEnrollment(ok(add(browser, "demo")), "demo", null);
	}

	@Test
	void failingContinuationUpdateLeavesNothing() throws Exception {
		var browser = ready(learner());
		String user = userId(browser);
		ok(add(browser, "demo"));
		var before = snapshot();

		whileFailing("enrollments", "create trigger " + TRIGGER + """
				 before update on code_academy.enrollments for each row
				when (new.user_id = '%s'::uuid) execute function code_academy.progress_test_fail()
				""".formatted(user),
				() -> assertError(continuation(browser, "demo", NEXT), 500, "INTERNAL_ERROR"));

		assertThat(snapshot()).isEqualTo(before);
		assertThat(enrollmentsOf(user)).singleElement()
			.satisfies(enrollment -> assertThat(enrollment.get("last_opened_lesson_id")).isNull());
		assertEnrollment(ok(continuation(browser, "demo", NEXT)), "demo", NEXT);
	}

	/** Runs body with the failing trigger installed; the trigger and its function are always removed after. */
	private void whileFailing(String table, String createTrigger, ThrowingRunnable body) throws Exception {
		try {
			jdbc.execute("""
					create function code_academy.progress_test_fail() returns trigger language plpgsql
					as $$ begin raise exception 'injected test failure'; end $$
					""");
			jdbc.execute(createTrigger);
			body.run();
		}
		finally {
			jdbc.execute("drop trigger if exists " + TRIGGER + " on code_academy." + table);
			jdbc.execute("drop function if exists code_academy.progress_test_fail()");
		}
	}
}
