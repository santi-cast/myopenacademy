package dev.codeacademy.content;

import java.util.List;
import java.util.Map;

/** content/<course>/manifest.json, as described in docs/manifiesto.md. */
public record Manifest(Integer schemaVersion, Source source, Course course, List<Lesson> lessons, List<Resource> resources,
		List<SourceLink> sourceToId) {

	public record Source(String name, String versionRef) {
	}

	public record Course(String id, String namespace, String title) {
	}

	public record Lesson(String id, String title, Integer position, String sourcePath, Boolean sourceAvailable,
			Boolean available, String bodyPath, List<Exercise> exercises) {
	}

	public record Exercise(String id, String lessonId, String title) {
	}

	public record Resource(String resourceId, String lessonId, String sourcePath, String localPath, String mediaType) {
	}

	/** Exactly one target: a lesson (with its fragments), a resource, a folder of resources or the course index. */
	public record SourceLink(String sourcePath, String lessonId, Map<String, String> fragments, String resourceId,
			List<String> resourceIds, String courseId) {
	}
}
