package dev.codeacademy.content;

import java.util.Map;

/** A validated course: its manifest, the rendered available lessons and the registered resources, by ID. */
public record CourseContent(Manifest manifest, Map<String, LessonContent> lessons, Map<String, ResourceFile> resources,
		Map<String, StageContent> stages) {
	public CourseContent(Manifest manifest, Map<String, LessonContent> lessons, Map<String, ResourceFile> resources) {
		this(manifest, lessons, resources, Map.of());
	}
}
