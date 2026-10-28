package dev.codeacademy.content;

/** An exercise extracted from its lesson, associated with the manifest ID; statement and solution sanitized separately. */
public record ExerciseContent(String id, String lessonId, String courseId, String title, String statementHtml,
		String solutionHtml) {
}
