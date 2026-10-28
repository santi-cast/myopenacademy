package dev.codeacademy.content;

import java.util.List;

/** One available lesson, rendered and sanitized; resourceIds are the resources its links use, in first-use order. */
public record LessonContent(String lessonId, String bodyHtml, List<ExerciseContent> exercises, List<String> resourceIds) {
}
