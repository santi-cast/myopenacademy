package dev.codeacademy.content;

import java.util.Map;

/**
 * A validated course: its manifest, the rendered available lessons, the registered resources, the project stages and
 * the documents without progress (challenges and glossary; H8), by ID. A document is rendered like a stage.
 */
public record CourseContent(Manifest manifest, Map<String, LessonContent> lessons, Map<String, ResourceFile> resources,
		Map<String, StageContent> stages, Map<String, StageContent> documents) {
	public CourseContent(Manifest manifest, Map<String, LessonContent> lessons, Map<String, ResourceFile> resources) {
		this(manifest, lessons, resources, Map.of(), Map.of());
	}
}
