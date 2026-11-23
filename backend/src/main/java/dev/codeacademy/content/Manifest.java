package dev.codeacademy.content;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * content/<course>/manifest.json, as described in docs/manifiesto.md. {@code parts} is optional in schema 2 (H12) and
 * is never written when null, so the manifest of a course without parts keeps the bytes it had before.
 */
public record Manifest(Integer schemaVersion, Source source, Course course, List<Phase> phases, List<Lesson> lessons,
		List<Resource> resources, List<SourceLink> sourceToId, List<Stage> projectStages, Provenance provenance,
		List<Document> documents, @JsonInclude(JsonInclude.Include.NON_NULL) List<Part> parts) {

	/** Without parts (before H12, or a course that has none). */
	public Manifest(Integer schemaVersion, Source source, Course course, List<Phase> phases, List<Lesson> lessons,
			List<Resource> resources, List<SourceLink> sourceToId, List<Stage> projectStages, Provenance provenance,
			List<Document> documents) {
		this(schemaVersion, source, course, phases, lessons, resources, sourceToId, projectStages, provenance, documents,
			null);
	}

	/** Without course documents (before H8, or a course that has none). */
	public Manifest(Integer schemaVersion, Source source, Course course, List<Phase> phases, List<Lesson> lessons,
			List<Resource> resources, List<SourceLink> sourceToId, List<Stage> projectStages, Provenance provenance) {
		this(schemaVersion, source, course, phases, lessons, resources, sourceToId, projectStages, provenance, null);
	}

	/** Source-compatible v1 constructor for catalog clients and fixtures. */
	public Manifest(Integer schemaVersion, Source source, Course course, List<Phase> phases, List<Lesson> lessons,
			List<Resource> resources, List<SourceLink> sourceToId) {
		this(schemaVersion, source, course, phases, lessons, resources, sourceToId, null, null, null);
	}

	/**
	 * A document of the course without progress (H8, T3): the challenges of a phase ({@code kind} "retos", with its
	 * {@code phaseId}; FORMATO.md 6) or the glossary ({@code kind} "glosario", no phase; FORMATO.md 9). Its ID is the
	 * academy's: {@code <phase id>.retos} or {@code <course>.glosario}.
	 */
	public record Document(String id, String kind, String title, String phaseId, String sourcePath, String bodyPath) {
		public static final String CHALLENGES = "retos";
		public static final String GLOSSARY = "glosario";
		/** The learning path (ROADMAP.md), the minimal path (RUTA-MINIMA.md) and the global cheat sheet (FORMATO.md 4). */
		public static final String ROADMAP = "ruta";
		public static final String MINIMAL_ROADMAP = "ruta-minima";
		public static final String CHEAT_SHEET = "chuleta";
		/** The course-wide documents, by kind, with their file (FORMATO.md 4 and 9): at most one of each. */
		public static final java.util.Map<String, String> COURSE_FILES = java.util.Map.of(GLOSSARY, "glosario.md",
			ROADMAP, "ROADMAP.md", MINIMAL_ROADMAP, "RUTA-MINIMA.md", CHEAT_SHEET, "99-chuleta-global.md");
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

	/**
	 * Where the copy comes from: the pinned commit ({@code versionRef}) and, when it was installed from a version tag,
	 * that tag and the {@code version} of curso.json it names (FORMATO.md 11.1; H7, A5). {@code repository} is the
	 * {@code repositorio} of curso.json: with the commit and the tag, what the list of installed courses records (T7).
	 */
	public record Source(String name, String versionRef, String versionTag, String courseVersion, String repository) {
		public Source(String name, String versionRef) {
			this(name, versionRef, null, null, null);
		}
	}

	/**
	 * The course as the catalog shows it: {@code description} and {@code recommended} (the course IDs it is worth doing
	 * first, never a requirement) come from curso.json ({@code descripcion}, {@code conviene_saber_antes}; H7, T6).
	 */
	public record Course(String id, String namespace, String title, String description, List<String> recommended,
			Project project) {
		public Course(String id, String namespace, String title) {
			this(id, namespace, title, null, null, null);
		}

		public Course(String id, String namespace, String title, String description, List<String> recommended) {
			this(id, namespace, title, description, recommended, null);
		}
	}

	/**
	 * The course project for the learner's portfolio (FORMATO.md 8; H8, T4): {@code kind} "etapas" (its stages are
	 * {@code projectStages}) or "modulo" ({@code lessonId}, the module that is the project). Null: no project declared.
	 */
	public record Project(String kind, String title, String lessonId) {
		public static final String STAGES = "etapas";
		public static final String MODULE = "modulo";
	}

	/** A permanent group of lessons; its title is taken verbatim from the source course index. */
	public record Phase(String id, String title, Integer position) {
	}

	/**
	 * A named stretch of the course (curso.json {@code partes}; H12): its phases in the order it lists them. It holds
	 * no learner progress, so an update may retitle it, add it, remove it or move a phase to another part.
	 */
	public record Part(String id, String title, Integer position, List<String> phaseIds) {
	}

	public record Lesson(String id, String title, Integer position, String phaseId, String sourcePath,
			Boolean sourceAvailable, Boolean available, String bodyPath, List<Exercise> exercises) {
	}

	public record Exercise(String id, String lessonId, String title) {
	}

	/** Exactly one owner: {@code lessonId}, {@code stageId} or {@code documentId}. */
	public record Resource(String resourceId, String lessonId, String sourcePath, String localPath, String mediaType,
			String stageId, String kind, String fileName, List<String> memberResourceIds, String documentId) {
		public Resource(String resourceId, String lessonId, String sourcePath, String localPath, String mediaType,
				String stageId, String kind, String fileName, List<String> memberResourceIds) {
			this(resourceId, lessonId, sourcePath, localPath, mediaType, stageId, kind, fileName, memberResourceIds, null);
		}

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
			List<String> resourceIds, String courseId, String stageId, String zipResourceId, String documentId) {
		public SourceLink(String sourcePath, String lessonId, Map<String, String> fragments, String resourceId,
				List<String> resourceIds, String courseId, String stageId, String zipResourceId) {
			this(sourcePath, lessonId, fragments, resourceId, resourceIds, courseId, stageId, zipResourceId, null);
		}

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
