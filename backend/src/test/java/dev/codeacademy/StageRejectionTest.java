package dev.codeacademy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.codeacademy.ApiClient.assertError;
import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.StageFixture.*;
import static dev.codeacademy.StageFixture.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class StageRejectionTest extends StageTestSupport {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"{}", "null", "[]", "true", "42", "\"completed\"", "{", "{\"state\":null}",
		"{\"state\":true}", "{\"state\":1}", "{\"state\":[]}", "{\"state\":{}}",
		"{\"state\":\"completed\",\"state\":\"not_completed\"}",
		"{\"state\":\"completed\",\"extra\":true}", "{\"state\":\"completed\"} {}"})
	void invalidStateShapeLeavesEnrollmentBothProgressDomainsAndDatabaseUntouched(String body) throws Exception {
		var browser = learner();
		rejected(browser, 400, "INVALID_REQUEST", () -> browser.send("PUT", "/api/me/stages/" + FIRST + "/state", body));
	}

	@ParameterizedTest
	@ValueSource(strings = {"pending", "in_progress", "solved", "COMPLETED", "Completed", "", " completed", "completed "})
	void unknownStateStringsAreInvalidStateNotInvalidRequest(String state) throws Exception {
		var browser = learner();
		rejected(browser, 400, "INVALID_STATE", () -> mark(browser, FIRST, state));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"{}", "null", "[]", "true", "{", "{\"stageId\":null}", "{\"stageId\":1}",
		"{\"stageId\":true}", "{\"stageId\":[]}", "{\"stageId\":{}}",
		"{\"stageId\":\"demo.stage-first\",\"stageId\":\"demo.stage-last\"}",
		"{\"stageId\":\"demo.stage-first\",\"lessonId\":\"demo.01.01\"}"})
	void invalidContinuationShapeChangesNothingOnAnExistingEnrollment(String body) throws Exception {
		var browser = learner();
		ok(mark(browser, FIRST, "completed"));
		ok(lessonState(browser, INTRO, "completed"));
		rejected(browser, 400, "INVALID_REQUEST", () -> browser.send("PUT", "/api/me/courses/demo/stage-continuation", body));
	}

	@Test
	void pendingContentStateAndContinuationRejectWithTheExactStableMessage() throws Exception {
		var browser = learner();
		for (boolean enrolled : List.of(false, true)) {
			if (enrolled) {
				ok(mark(browser, FIRST, "completed"));
				ok(lessonState(browser, INTRO, "completed"));
				ok(exerciseState(browser, E01, "solved"));
			}
			rejected(browser, 409, "STAGE_UNAVAILABLE", () -> browser.get("/api/stages/" + PENDING));
			for (String state : List.of("completed", "not_completed")) {
				rejected(browser, 409, "STAGE_UNAVAILABLE", () -> mark(browser, PENDING, state));
			}
			rejected(browser, 409, "STAGE_UNAVAILABLE", () -> open(browser, "demo", PENDING));
		}
	}

	@Test
	void unknownIdsAndCourseMismatchNeverCreateOrChangePersonalData() throws Exception {
		var browser = learner();
		for (String id : List.of("demo.stage-missing", "DEMO-stage-first", "demo.stage-first%20", "demo.01.01")) {
			rejected(browser, 404, "NOT_FOUND", () -> browser.get("/api/stages/" + id));
			rejected(browser, 404, "NOT_FOUND", () -> mark(browser, id, "completed"));
			rejected(browser, 404, "NOT_FOUND", () -> open(browser, "demo", id));
		}
		rejected(browser, 404, "NOT_FOUND", () -> browser.get("/api/courses/missing/stages"));
		rejected(browser, 404, "NOT_FOUND", () -> stageProgress(browser, "missing"));
		rejected(browser, 404, "NOT_FOUND", () -> open(browser, "missing", FIRST));
		rejected(browser, 400, "COURSE_MISMATCH", () -> open(browser, "other", FIRST));
		rejected(browser, 409, "COURSE_NOT_ADDED", () -> open(browser, "demo", FIRST));
		ok(mark(browser, FIRST, "completed"));
		ok(add(browser, "other"));
		rejected(browser, 400, "COURSE_MISMATCH", () -> open(browser, "other", LAST));
	}

	@Test
	void everyNewRouteRequiresSessionAndBothWritesRequireCsrf() throws Exception {
		var browser = ready(learner());
		var anonymous = ready(new ApiClient(port));
		var before = TestDatabase.snapshot(jdbc);
		for (String path : List.of("/api/courses/demo/stages", "/api/stages/" + FIRST,
			"/api/me/courses/demo/stage-progress")) {
			assertError(anonymous.get(path), 401, "AUTHENTICATION_REQUIRED");
		}
		assertError(mark(anonymous, FIRST, "completed"), 401, "AUTHENTICATION_REQUIRED");
		assertError(open(anonymous, "demo", FIRST), 401, "AUTHENTICATION_REQUIRED");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		for (String csrf : new String[] {null, "wrong-token"}) {
			rejected(browser, 403, "CSRF_REJECTED", () -> browser.send("PUT", "/api/me/stages/" + FIRST + "/state", state("completed"), csrf));
			rejected(browser, 403, "CSRF_REJECTED", () -> browser.send("PUT", "/api/me/courses/demo/stage-continuation",
				"{\"stageId\":\"demo.stage-first\"}", csrf));
		}
	}

	@Test
	void jsonNegotiationRejectsEveryRouteBeforeEffectsAndAcceptsH4CompatibleMediaRanges() throws Exception {
		var browser = learner();
		for (String path : List.of("/api/courses/demo/stages", "/api/stages/" + FIRST,
			"/api/me/courses/demo/stage-progress")) {
			rejected(browser, 406, "NOT_ACCEPTABLE", () -> browser.sendAccepting("GET", path, null, "text/html"));
			for (String accept : new String[] {null, "*/*", "application/json", "application/json;q=0"}) {
				ok(browser.sendAccepting("GET", path, null, accept));
			}
		}
		rejected(browser, 406, "NOT_ACCEPTABLE", () -> browser.sendAccepting("PUT", "/api/me/stages/" + FIRST + "/state", state("completed"), "text/html"));
		ok(mark(browser, FIRST, "completed"));
		rejected(browser, 406, "NOT_ACCEPTABLE", () -> browser.sendAccepting("PUT", "/api/me/courses/demo/stage-continuation",
			"{\"stageId\":\"demo.stage-last\"}", "application/xml"));
	}

	@Test
	void twoSessionsCannotSelectAnotherOwnerAndEveryRejectedOverridePreservesBothUsers() throws Exception {
		var owner = learner();
		var visitor = learner();
		ok(mark(owner, FIRST, "completed"));
		ok(open(owner, "demo", LAST));
		ok(lessonState(owner, INTRO, "completed"));
		ok(exerciseState(owner, E01, "solved"));
		var ownerBefore = personal(owner);
		String ownerId = userId(owner);
		String foreignEnrollment = jdbc.queryForObject("select id::text from code_academy.enrollments where user_id = ?::uuid", String.class, ownerId);
		var before = TestDatabase.snapshot(jdbc);
		assertThat(ok(stageProgress(visitor, "demo")).path("stageStates").size()).isZero();
		// Query parameters do not select an owner: if accepted, the session still selects the visitor.
		assertThat(ok(visitor.get("/api/me/courses/demo/stage-progress?userId=" + ownerId)))
			.isEqualTo(ok(stageProgress(visitor, "demo")));
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		for (String field : List.of("userId", "usuario_id", "enrollmentId")) {
			String foreign = field.equals("enrollmentId") ? foreignEnrollment : ownerId;
			rejected(visitor, 400, "INVALID_REQUEST", () -> visitor.send("PUT", "/api/me/stages/" + FIRST + "/state",
				"{\"state\":\"completed\",\"" + field + "\":\"" + foreign + "\"}"));
			rejected(visitor, 400, "INVALID_REQUEST", () -> visitor.send("PUT", "/api/me/courses/demo/stage-continuation",
				"{\"stageId\":\"demo.stage-first\",\"" + field + "\":\"" + foreign + "\"}"));
		}
		ok(mark(visitor, FIRST, "not_completed"));
		ok(open(visitor, "demo", FIRST));
		ok(lessonState(visitor, INTRO, "not_completed"));
		ok(exerciseState(visitor, E01, "pending"));
		assertThat(personal(owner)).isEqualTo(ownerBefore);
		stageCounts(ok(stageProgress(visitor, "demo")).path("progress"), 0, 2, 3);
		stageContinuation(ok(stageProgress(visitor, "demo")).path("continuation"), FIRST);
		stageCounts(ok(stageProgress(owner, "demo")).path("progress"), 1, 2, 3);
		stageContinuation(ok(stageProgress(owner, "demo")).path("continuation"), LAST);
	}
}
