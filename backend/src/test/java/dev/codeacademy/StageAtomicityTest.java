package dev.codeacademy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.StageFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

class StageAtomicityTest extends StageTestSupport {

	@Test
	void failedFirstMarkRollsBackEnrollmentStateAndBothContinuationsEvenAtCommit() throws Exception {
		var browser = ready(learner());
		for (boolean deferred : List.of(false, true)) {
			var publicBefore = personal(browser);
			var before = TestDatabase.snapshot(jdbc);
			// Discover the new state table by its public relational role, not a mandated physical name.
			String table = jdbc.queryForObject("""
					select table_name from information_schema.columns
					where table_schema = 'code_academy' and column_name in ('stage_id', 'enrollment_id')
					group by table_name having count(*) = 2
					""", String.class);
			withFailure(table, deferred ? "create constraint trigger stage_test_failure after insert on code_academy."
				+ table + " deferrable initially deferred for each row execute function code_academy.stage_test_fail()"
				: "create trigger stage_test_failure before insert on code_academy." + table
				+ " for each row execute function code_academy.stage_test_fail()",
				() -> assertError(mark(browser, FIRST, "completed"), 500, "INTERNAL_ERROR"));
			assertThat(personal(browser)).isEqualTo(publicBefore);
			assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		}
		assertThat(ok(mark(browser, FIRST, "completed")).path("courseAutoAdded").asBoolean()).isTrue();
	}

	@Test
	void failedEnrollmentCommitAndContinuationUpdateHaveNoPartialPublicOrStoredEffects() throws Exception {
		var browser = ready(learner());
		var publicBefore = personal(browser);
		var before = TestDatabase.snapshot(jdbc);
		withFailure("enrollments", """
				create constraint trigger stage_test_failure after insert on code_academy.enrollments
				deferrable initially deferred for each row execute function code_academy.stage_test_fail()
				""", () -> assertError(mark(browser, LAST, "not_completed"), 500, "INTERNAL_ERROR"));
		assertThat(personal(browser)).isEqualTo(publicBefore);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		ok(mark(browser, FIRST, "completed"));
		ok(lessonState(browser, INTRO, "completed"));
		publicBefore = personal(browser);
		before = TestDatabase.snapshot(jdbc);
		withFailure("enrollments", """
				create trigger stage_test_failure before update on code_academy.enrollments
				for each row execute function code_academy.stage_test_fail()
				""", () -> assertError(open(browser, "demo", LAST), 500, "INTERNAL_ERROR"));
		assertThat(personal(browser)).isEqualTo(publicBefore);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		stageContinuation(ok(open(browser, "demo", LAST)).path("continuation"), LAST);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void concurrentStageAndLessonOrExerciseFirstMarksReuseExactlyOneEnrollment(boolean exercise) throws Exception {
		var browser = ready(learner());
		var responses = parallel(List.of(() -> mark(browser, FIRST, "completed"),
			() -> exercise ? exerciseState(browser, E01, "solved") : lessonState(browser, INTRO, "completed")));
		var stage = ok(responses.get(0));
		var lesson = ok(responses.get(1));
		boolean stageCreated = stage.path("courseAutoAdded").asBoolean();
		assertThat(lesson.path("courseAutoAdded").asBoolean()).isNotEqualTo(stageCreated);
		var stageProgress = ok(stageProgress(browser, "demo"));
		stageProgressShape(stageProgress, "demo", true, stageCreated ? FIRST : null);
		stageCounts(stageProgress.path("progress"), 1, 2, 3);
		assertThat(stageProgress.path("stageStates").size()).isEqualTo(1);
		var h4 = ok(progress(browser, "demo"));
		assertContinuation(h4.path("continuation"), stageCreated ? null : INTRO);
		assertCounts(h4.path("progress"), exercise ? 0 : 1, 2, 3, exercise ? 0.0 : 50.0);
		assertThat(h4.path(exercise ? "exerciseStates" : "lessonStates").size()).isEqualTo(1);
		assertThat(ok(browser.get("/api/me/courses")).path("courses").size()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from code_academy.enrollments where user_id = ?::uuid",
			Integer.class, userId(browser))).isEqualTo(1);
		var before = TestDatabase.snapshot(jdbc);
		ok(mark(browser, FIRST, "completed"));
		if (exercise) ok(exerciseState(browser, E01, "solved"));
		else ok(lessonState(browser, INTRO, "completed"));
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	private void withFailure(String table, String trigger, ThrowingRunnable body) throws Exception {
		try {
			jdbc.execute("""
					create function code_academy.stage_test_fail() returns trigger language plpgsql
					as $$ begin raise exception 'injected stage test failure'; end $$
					""");
			jdbc.execute(trigger);
			body.run();
		}
		finally {
			jdbc.execute("drop trigger if exists stage_test_failure on code_academy." + table);
			jdbc.execute("drop function if exists code_academy.stage_test_fail()");
		}
	}
}
