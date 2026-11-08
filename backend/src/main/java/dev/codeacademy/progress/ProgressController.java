package dev.codeacademy.progress;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import dev.codeacademy.account.AccountPrincipal;
import dev.codeacademy.api.ApiException;
import dev.codeacademy.api.StrictJson;
import dev.codeacademy.progress.ProgressViews.Enrollment;
import dev.codeacademy.progress.ProgressViews.ExerciseStateResult;
import dev.codeacademy.progress.ProgressViews.LessonStateResult;
import dev.codeacademy.progress.ProgressViews.MyCourses;
import dev.codeacademy.progress.ProgressViews.Progress;
import dev.codeacademy.progress.ProgressViews.StageProgress;
import dev.codeacademy.progress.ProgressViews.StageStateResult;

/** Personal progress routes: the owner is always the session's account, never a path or body value. */
@RestController
@RequestMapping(path = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
class ProgressController {

	private static final Set<String> LESSON_STATES = Set.of("not_completed", "completed");
	private static final Set<String> EXERCISE_STATES = Set.of("pending", "in_progress", "solved");

	private final ProgressService progress;

	ProgressController(ProgressService progress) {
		this.progress = progress;
	}

	/** Only the added courses; an empty list when none is. */
	@GetMapping("/courses")
	MyCourses courses(@AuthenticationPrincipal AccountPrincipal principal) {
		return progress.courses(principal.id());
	}

	/** Takes no body: any content, even an empty object, is an invalid request. */
	@PutMapping("/courses/{courseId}")
	Enrollment add(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String courseId,
			@RequestBody(required = false) String body) {
		if (body != null && !body.isEmpty()) throw ApiException.invalidRequest();
		return progress.add(principal.id(), courseId);
	}

	@GetMapping("/courses/{courseId}/progress")
	Progress progress(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String courseId) {
		return progress.progress(principal.id(), courseId);
	}

	@PutMapping("/lessons/{lessonId}/state")
	LessonStateResult lessonState(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String lessonId,
			@RequestBody(required = false) JsonNode body) {
		return progress.markLesson(principal.id(), lessonId, state(body, LESSON_STATES));
	}

	@PutMapping("/exercises/{exerciseId}/state")
	ExerciseStateResult exerciseState(@AuthenticationPrincipal AccountPrincipal principal,
			@PathVariable String exerciseId, @RequestBody(required = false) JsonNode body) {
		return progress.markExercise(principal.id(), exerciseId, state(body, EXERCISE_STATES));
	}

	@PutMapping("/courses/{courseId}/continuation")
	Enrollment continuation(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String courseId,
			@RequestBody(required = false) JsonNode body) {
		return progress.continueAt(principal.id(), courseId, StrictJson.strings(body, "lessonId").getFirst());
	}

	@GetMapping("/courses/{courseId}/stage-progress")
	StageProgress stageProgress(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String courseId) {
		return progress.stageProgress(principal.id(), courseId);
	}

	@PutMapping("/stages/{stageId}/state")
	StageStateResult stageState(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String stageId,
			@RequestBody(required = false) JsonNode body) {
		return progress.markStage(principal.id(), stageId, state(body, LESSON_STATES));
	}

	@PutMapping("/courses/{courseId}/stage-continuation")
	StageProgress stageContinuation(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String courseId,
			@RequestBody(required = false) JsonNode body) {
		return progress.continueStageAt(principal.id(), courseId, StrictJson.strings(body, "stageId").getFirst());
	}

	/** Schema errors are INVALID_REQUEST; a string that is not a state of this kind, INVALID_STATE. */
	private static String state(JsonNode body, Set<String> allowed) {
		String state = StrictJson.strings(body, "state").getFirst();
		if (!allowed.contains(state)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "State is invalid.");
		}
		return state;
	}
}
