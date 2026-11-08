package dev.codeacademy.content;

import java.util.List;
import java.util.Map;

/** content/<course>/manifest.json, as described in docs/manifiesto.md. */
public record Manifest(Integer schemaVersion, Source source, Course course, List<Phase> phases, List<Lesson> lessons,
		List<Resource> resources, List<SourceLink> sourceToId, List<Stage> projectStages, Provenance provenance) {

	/** Source-compatible v1 constructor for catalog clients and fixtures. */
	public Manifest(Integer schemaVersion, Source source, Course course, List<Phase> phases, List<Lesson> lessons,
			List<Resource> resources, List<SourceLink> sourceToId) {
		this(schemaVersion, source, course, phases, lessons, resources, sourceToId, null, null);
	}

	public record Stage(String id, String title, Integer position, String sourcePath,
			Boolean sourceAvailable, Boolean available, String bodyPath) {
	}

	public record Provenance(List<CopiedFile> files, List<GeneratedFile> generated) {
	}

	public record CopiedFile(String sourcePath, String localPath, String sourceSha256, String copySha256,
			String transformation) {
	}

	public record GeneratedFile(String localPath, String copySha256, List<String> inputResourceIds) {
	}

	public record Source(String name, String versionRef) {
	}

	public record Course(String id, String namespace, String title) {
	}

	/** A permanent group of lessons; its title is taken verbatim from the source course index. */
	public record Phase(String id, String title, Integer position) {
	}

	public record Lesson(String id, String title, Integer position, String phaseId, String sourcePath,
			Boolean sourceAvailable, Boolean available, String bodyPath, List<Exercise> exercises) {
	}

	public record Exercise(String id, String lessonId, String title) {
	}

	public record Resource(String resourceId, String lessonId, String sourcePath, String localPath, String mediaType,
			String stageId, String kind, String fileName, List<String> memberResourceIds) {
		public Resource(String resourceId, String lessonId, String sourcePath, String localPath, String mediaType) {
			this(resourceId, lessonId, sourcePath, localPath, mediaType, null, null);
		}

		public Resource(String resourceId, String lessonId, String sourcePath, String localPath, String mediaType,
				String stageId, String kind) {
			this(resourceId, lessonId, sourcePath, localPath, mediaType, stageId, kind, null, null);
		}
	}

	/** Exactly one target: a lesson/stage with fragments, a resource, a folder of resources or the course index. */
	public record SourceLink(String sourcePath, String lessonId, Map<String, String> fragments, String resourceId,
			List<String> resourceIds, String courseId, String stageId, String zipResourceId) {
		public SourceLink(String sourcePath, String lessonId, Map<String, String> fragments, String resourceId,
				List<String> resourceIds, String courseId) {
			this(sourcePath, lessonId, fragments, resourceId, resourceIds, courseId, null);
		}

		public SourceLink(String sourcePath, String lessonId, Map<String, String> fragments, String resourceId,
				List<String> resourceIds, String courseId, String stageId) {
			this(sourcePath, lessonId, fragments, resourceId, resourceIds, courseId, stageId, null);
		}
	}
}
