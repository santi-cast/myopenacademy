package dev.codeacademy.progress;

import java.util.List;

/** The progress shapes of docs/api.md «Formas reutilizables de salida»; every field is part of the contract. */
final class ProgressViews {

	private ProgressViews() {
	}

	record CourseView(String id, String title) {
	}

	/** Computed from the stored catalog and the states; percentage 0–100 unrounded, null without available lessons. */
	record Counts(int completedLessons, int availableLessons, int plannedLessons, Double percentage) {

		static Counts of(int completed, int available, int planned) {
			return new Counts(completed, available, planned, available == 0 ? null : completed * 100.0 / available);
		}
	}

	/** Stages intentionally have separate DTOs: no new fields leak into H4 responses. */
	record StageCounts(int completedStages, int availableStages, int plannedStages, Double percentage) {
		static StageCounts of(int completed, int available, int planned) {
			return new StageCounts(completed, available, planned, available == 0 ? null : completed * 100.0 / available);
		}
	}

	record StageContinuation(String lastOpenedStageId) {
	}

	record StageState(String stageId, String state, String updatedAt) {
	}

	record StageProgress(String courseId, boolean added, List<StageState> stageStates,
			StageContinuation continuation, StageCounts progress) {
	}

	record StageStateResult(String courseId, String stageId, String state, String updatedAt,
			boolean courseAutoAdded, StageContinuation continuation, StageCounts progress) {
	}

	record Continuation(String lastOpenedLessonId) {
	}

	record Enrollment(String courseId, boolean added, Continuation continuation, Counts progress) {
	}

	record MyCourse(CourseView course, Enrollment enrollment) {
	}

	record MyCourses(List<MyCourse> courses) {
	}

	record LessonState(String lessonId, String state, String updatedAt) {
	}

	record ExerciseState(String exerciseId, String state, String updatedAt) {
	}

	record Progress(String courseId, boolean added, List<LessonState> lessonStates, List<ExerciseState> exerciseStates,
			Continuation continuation, Counts progress) {
	}

	record LessonStateResult(String courseId, String lessonId, String state, String updatedAt, boolean courseAutoAdded,
			Continuation continuation, Counts progress) {
	}

	record ExerciseStateResult(String courseId, String exerciseId, String state, String updatedAt,
			boolean courseAutoAdded, Continuation continuation, Counts progress) {
	}
}
