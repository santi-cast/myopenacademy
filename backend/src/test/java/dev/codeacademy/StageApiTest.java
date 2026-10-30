package dev.codeacademy;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import dev.codeacademy.content.ZipFixture;

import static dev.codeacademy.ProgressTestSupport.*;
import static dev.codeacademy.StageFixture.*;
import static dev.codeacademy.StageFixture.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class StageApiTest extends StageTestSupport {

	@Test
	void indexIncludesPendingInPositionOrderAndV1HasAnEmptyProjectWithoutWrites() throws Exception {
		var browser = learner();
		var before = TestDatabase.snapshot(jdbc);
		var response = browser.get("/api/courses/demo/stages");
		privateJson(response);
		JsonNode body = ok(response);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("courseId", "stages");
		assertThat(body.path("courseId").asString()).isEqualTo("demo");
		JsonNode entries = body.path("stages");
		assertThat(entries.isArray()).isTrue();
		assertThat(entries.size()).isEqualTo(3);
		for (int i = 0; i < 3; i++) {
			JsonNode entry = entries.get(i);
			assertThat(entry.propertyNames()).containsExactlyInAnyOrder("id", "courseId", "title", "position", "available");
			assertThat(entry.path("id").asString()).isEqualTo(List.of(FIRST, PENDING, LAST).get(i));
			assertThat(entry.path("courseId").asString()).isEqualTo("demo");
			assertThat(entry.path("title").asString()).isEqualTo(List.of("First stage", "Pending stage", "Last stage").get(i));
			assertThat(entry.path("position").isInt()).isTrue();
			assertThat(entry.path("position").asInt()).isEqualTo(i + 1);
			assertThat(entry.path("available").isBoolean()).isTrue();
			assertThat(entry.path("available").asBoolean()).isEqualTo(i != 1);
		}
		assertThat(ok(browser.get("/api/courses/other/stages"))).isEqualTo(ApiClient.JSON.readTree(
			"{\"courseId\":\"other\",\"stages\":[]}"));
		JsonNode course = ok(browser.get("/api/courses/demo"));
		assertThat(course.propertyNames()).containsExactlyInAnyOrder("course", "phases", "lessons");
		assertThat(course.path("lessons").size()).isEqualTo(3);
		assertCounts(ok(progress(browser, "demo")).path("progress"), 0, 2, 3, 0.0);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void availableBodyKeepsManualExercisesAndFullResourcesWithAvailableNeighborsOnly() throws Exception {
		var browser = learner();
		var before = TestDatabase.snapshot(jdbc);
		for (String id : List.of(FIRST, LAST)) {
			var response = browser.get("/api/stages/" + id);
			privateJson(response);
			JsonNode body = ok(response);
			assertThat(body.propertyNames()).containsExactlyInAnyOrder("stage", "bodyHtml", "resources", "previousStageId", "nextStageId");
			assertThat(body.path("stage")).isEqualTo(ok(browser.get("/api/courses/demo/stages")).path("stages").get(id.equals(FIRST) ? 0 : 2));
			assertThat(body.path("bodyHtml").asString()).isEqualTo(fixture.content.load().stages().get(id).bodyHtml());
			if (id.equals(FIRST)) {
				assertThat(body.path("previousStageId").isNull()).isTrue();
				assertThat(body.path("nextStageId").asString()).isEqualTo(LAST);
				assertThat(body.path("bodyHtml").asString()).contains("Ejercicios prácticos resueltos", "Manual exercise", "<del>Manual</del>");
				assertThat(body.path("resources").size()).isEqualTo(5);
				for (JsonNode resource : body.path("resources")) {
					assertThat(resource.propertyNames()).containsExactlyInAnyOrder("resourceId", "url", "mediaType", "fileName");
					String resourceId = resource.path("resourceId").asString();
					var registered = fixture.content.load().manifest().resources().stream()
						.filter(r -> r.resourceId().equals(resourceId)).findFirst().orElseThrow();
					assertThat(registered.stageId()).isEqualTo(FIRST);
					assertThat(resource.path("url").asString()).isEqualTo("/api/resources/" + resourceId);
					assertThat(resource.path("mediaType").asString()).isEqualTo(registered.mediaType());
					String name = resourceId.equals(ZipFixture.ZIP) ? "hola.zip"
						: registered.sourcePath().substring(registered.sourcePath().lastIndexOf('/') + 1);
					assertThat(resource.path("fileName").asString()).isEqualTo(name);
					var download = browser.download(resource.path("url").asString(), "*/*");
					assertThat(download.statusCode()).isEqualTo(200);
					assertThat(download.body()).isEqualTo(Files.readAllBytes(fixture.content.root.resolve(registered.localPath())));
				}
			}
			else {
				assertThat(body.path("previousStageId").asString()).isEqualTo(FIRST);
				assertThat(body.path("nextStageId").isNull()).isTrue();
				assertThat(body.path("resources").isArray()).isTrue();
				assertThat(body.path("resources").size()).isZero();
			}
		}
		assertThat(ok(stageProgress(browser, "demo")).path("stageStates").size()).isZero();
		assertThat(ok(browser.get("/api/me/courses")).path("courses").size()).isZero();
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void absentStatesAreDefaultsWithoutRowsAndCountsArePerCourseAndSeparateFromLessons() throws Exception {
		var browser = learner();
		var before = TestDatabase.snapshot(jdbc);
		var response = stageProgress(browser, "demo");
		privateJson(response);
		JsonNode body = ok(response);
		stageProgressShape(body, "demo", false, null);
		assertThat(body.path("stageStates").size()).isZero();
		stageCounts(body.path("progress"), 0, 2, 3);
		JsonNode empty = ok(stageProgress(browser, "other"));
		stageProgressShape(empty, "other", false, null);
		assertThat(empty.path("stageStates").size()).isZero();
		stageCounts(empty.path("progress"), 0, 0, 0);
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		ok(add(browser, "demo"));
		body = ok(stageProgress(browser, "demo"));
		stageProgressShape(body, "demo", true, null);
		assertThat(body.path("stageStates").size()).isZero();
		stageCounts(body.path("progress"), 0, 2, 3);
		assertCounts(ok(progress(browser, "demo")).path("progress"), 0, 2, 3, 0.0);
	}

	@Test
	void firstManualMarkAutoAddsAtomicallyAndRepeatedStateKeepsItsTimestamp() throws Exception {
		var browser = learner();
		var h4Catalog = ok(browser.get("/api/courses/demo"));
		var response = mark(browser, LAST, "not_completed");
		privateJson(response);
		JsonNode first = ok(response);
		assertThat(first.propertyNames()).containsExactlyInAnyOrder("courseId", "stageId", "state", "updatedAt", "courseAutoAdded", "continuation", "progress");
		assertThat(first.path("courseId").asString()).isEqualTo("demo");
		assertThat(first.path("stageId").asString()).isEqualTo(LAST);
		assertThat(first.path("state").asString()).isEqualTo("not_completed");
		assertThat(first.path("courseAutoAdded").isBoolean()).isTrue();
		assertThat(first.path("courseAutoAdded").asBoolean()).isTrue();
		stageContinuation(first.path("continuation"), LAST);
		stageCounts(first.path("progress"), 0, 2, 3);
		var at = utc(first.path("updatedAt"));
		assertContinuation(ok(progress(browser, "demo")).path("continuation"), null);
		var before = TestDatabase.snapshot(jdbc);
		JsonNode again = ok(mark(browser, LAST, "not_completed"));
		assertThat(utc(again.path("updatedAt"))).isEqualTo(at);
		assertThat(again.path("courseAutoAdded").asBoolean()).isFalse();
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		JsonNode completed = ok(mark(browser, FIRST, "completed"));
		assertThat(completed.path("courseAutoAdded").asBoolean()).isFalse();
		stageContinuation(completed.path("continuation"), LAST);
		stageCounts(completed.path("progress"), 1, 2, 3);
		JsonNode saved = ok(stageProgress(browser, "demo"));
		stageProgressShape(saved, "demo", true, LAST);
		assertThat(saved.path("stageStates").size()).isEqualTo(2);
		for (int i = 0; i < 2; i++) {
			JsonNode entry = saved.path("stageStates").get(i);
			assertThat(entry.propertyNames()).containsExactlyInAnyOrder("stageId", "state", "updatedAt");
			assertThat(entry.path("stageId").asString()).isEqualTo(i == 0 ? FIRST : LAST);
			assertThat(entry.path("state").asString()).isEqualTo(i == 0 ? "completed" : "not_completed");
			assertThat(entry.path("updatedAt")).isEqualTo((i == 0 ? completed : first).path("updatedAt"));
		}
		assertThat(ok(browser.get("/api/courses/demo"))).isEqualTo(h4Catalog);
		assertCounts(ok(progress(browser, "demo")).path("progress"), 0, 2, 3, 0.0);
		assertThat(utc(ok(mark(browser, LAST, "completed")).path("updatedAt"))).isAfter(at);
		stageCounts(ok(stageProgress(browser, "demo")).path("progress"), 2, 2, 3);
		ok(mark(browser, FIRST, "not_completed"));
		stageCounts(ok(stageProgress(browser, "demo")).path("progress"), 1, 2, 3);
	}

	@Test
	void continuationIsIndependentAndExistingEnrollmentMarksAndAddsNeverMoveEitherPointer() throws Exception {
		var browser = learner();
		assertEnrollment(ok(add(browser, "demo")), "demo", null);
		stageContinuation(ok(stageProgress(browser, "demo")).path("continuation"), null);
		JsonNode marked = ok(mark(browser, FIRST, "completed"));
		assertThat(marked.path("courseAutoAdded").asBoolean()).isFalse();
		stageContinuation(marked.path("continuation"), null);
		ok(lessonState(browser, INTRO, "completed"));
		ok(exerciseState(browser, E01, "solved"));
		ok(continuation(browser, "demo", NEXT));
		var lessonBefore = ok(progress(browser, "demo"));
		JsonNode opened = ok(open(browser, "demo", LAST));
		stageProgressShape(opened, "demo", true, LAST);
		stageCounts(opened.path("progress"), 1, 2, 3);
		assertThat(opened).isEqualTo(ok(stageProgress(browser, "demo")));
		assertThat(ok(progress(browser, "demo"))).isEqualTo(lessonBefore);
		ok(mark(browser, FIRST, "not_completed"));
		stageContinuation(ok(stageProgress(browser, "demo")).path("continuation"), LAST);
		assertThat(ok(progress(browser, "demo"))).isEqualTo(lessonBefore);
		var before = TestDatabase.snapshot(jdbc);
		assertEnrollment(ok(add(browser, "demo")), "demo", NEXT);
		assertThat(ok(open(browser, "demo", LAST))).isEqualTo(ok(stageProgress(browser, "demo")));
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
		ok(continuation(browser, "demo", INTRO));
		stageContinuation(ok(stageProgress(browser, "demo")).path("continuation"), LAST);
	}

	@Test
	void stageCountsUseStoredAvailabilityExcludePendingCompletionsAndDoNotRound() throws Exception {
		var browser = learner();
		ok(mark(browser, FIRST, "completed"));
		var h4 = ok(progress(browser, "demo"));
		var saved = ok(stageProgress(browser, "demo")).path("stageStates");
		String table = jdbc.queryForObject("""
				select table_name from information_schema.columns
				where table_schema = 'code_academy' and table_name <> 'lessons'
				  and column_name in ('id', 'available', 'source_available')
				group by table_name having count(*) = 3
				""", String.class);
		try {
			jdbc.update("update code_academy." + table + " set available = true, source_available = true where id = ?", PENDING);
			stageCounts(ok(stageProgress(browser, "demo")).path("progress"), 1, 3, 3);
			jdbc.update("update code_academy." + table + " set available = false where id = ?", FIRST);
			stageCounts(ok(stageProgress(browser, "demo")).path("progress"), 0, 2, 3);
			jdbc.update("update code_academy." + table + " set available = false where course_id = 'demo'");
			stageCounts(ok(stageProgress(browser, "demo")).path("progress"), 0, 0, 3);
			assertThat(ok(stageProgress(browser, "demo")).path("stageStates")).isEqualTo(saved);
			assertThat(ok(progress(browser, "demo"))).isEqualTo(h4);
		}
		finally {
			jdbc.update("update code_academy." + table + " set available = true where id in (?, ?)", FIRST, LAST);
			jdbc.update("update code_academy." + table + " set available = false, source_available = false where id = ?", PENDING);
		}
		assertThat(ok(stageProgress(browser, "demo")).path("stageStates")).isEqualTo(saved);
	}

	@Test
	void lessonAndExerciseFirstMarksLeaveStageContinuationEmptyAndPreserveH4Shapes() throws Exception {
		for (boolean exercise : List.of(false, true)) {
			var browser = learner();
			JsonNode result = ok(exercise ? exerciseState(browser, E01, "solved") : lessonState(browser, INTRO, "completed"));
			assertThat(result.propertyNames()).containsExactlyInAnyOrder("courseId", exercise ? "exerciseId" : "lessonId",
				"state", "updatedAt", "courseAutoAdded", "continuation", "progress");
			assertThat(result.path("courseAutoAdded").asBoolean()).isTrue();
			assertContinuation(result.path("continuation"), INTRO);
			assertCounts(result.path("progress"), exercise ? 0 : 1, 2, 3, exercise ? 0.0 : 50.0);
			stageContinuation(ok(stageProgress(browser, "demo")).path("continuation"), null);
			JsonNode marked = ok(mark(browser, LAST, "completed"));
			assertThat(marked.path("courseAutoAdded").asBoolean()).isFalse();
			stageContinuation(marked.path("continuation"), null);
			assertContinuation(ok(progress(browser, "demo")).path("continuation"), INTRO);
		}
	}
}
