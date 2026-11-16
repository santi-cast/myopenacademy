package dev.codeacademy.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Resource;

/**
 * Loads a course from its controlled copy: manifest structure, file containment, then rendering of every available
 * lesson. Any problem aborts the whole load with no partial result (docs/manifiesto.md, «Validación y entrega segura»).
 */
public final class ContentLoader {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
		.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
		.build();

	private ContentLoader() {
	}

	/**
	 * Every course of the content root, one directory per course with its manifest.json, in directory name order.
	 * IDs must also be unique across courses; any problem in any course aborts the whole load.
	 */
	public static List<CourseContent> loadAll(Path contentRoot) {
		return loadAll(contentRoot, false);
	}

	/** The courses installed in a content root, which may hold none yet: an academy starts with no course. */
	static List<CourseContent> loadInstalled(Path contentRoot) {
		return loadAll(contentRoot, true);
	}

	private static List<CourseContent> loadAll(Path contentRoot, boolean allowEmpty) {
		Path root = realRoot(contentRoot);
		List<String> manifests;
		try (Stream<Path> entries = Files.list(root)) {
			manifests = entries.filter(Files::isDirectory).map(dir -> dir.getFileName() + "/manifest.json").sorted()
				.toList();
		}
		catch (IOException ex) {
			throw new ContentValidationException("content root " + contentRoot + " cannot be read");
		}
		if (manifests.isEmpty() && allowEmpty) return List.of();
		if (manifests.isEmpty()) throw new ContentValidationException("content root " + contentRoot + " has no course");
		List<CourseContent> courses = manifests.stream().map(manifest -> load(root, manifest)).toList();
		List<String> problems = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (CourseContent course : courses) {
			Manifest manifest = course.manifest();
			List<String> courseIds = new ArrayList<>(List.of(manifest.course().id()));
			manifest.phases().forEach(phase -> courseIds.add(phase.id()));
			manifest.lessons().forEach(lesson -> {
				courseIds.add(lesson.id());
				lesson.exercises().forEach(exercise -> courseIds.add(exercise.id()));
			});
			if (manifest.projectStages() != null) manifest.projectStages().forEach(stage -> courseIds.add(stage.id()));
			if (manifest.documents() != null) manifest.documents().forEach(document -> courseIds.add(document.id()));
			manifest.resources().forEach(resource -> courseIds.add(resource.resourceId()));
			courseIds.stream().filter(id -> !ids.add(id))
				.forEach(id -> problems.add("id " + id + " is used by more than one course"));
		}
		failIfAny(problems);
		// Links to the other installed courses become internal links (FORMATO.md 10.2; H8, T5).
		return CrossCourseLinks.resolve(courses);
	}

	/**
	 * Reads a registered resource when it is served, re-checking the manifest localPath against the content root: empty
	 * if it is no longer the same contained regular file reached without symbolic links.
	 */
	public static Optional<byte[]> readResource(Path contentRoot, String localPath, Path loadedPath) {
		try {
			Path checked = ContentPaths.file(contentRoot.toRealPath(), localPath, "resource", new ArrayList<>());
			if (checked == null || !checked.equals(loadedPath)) return Optional.empty();
			try (InputStream input = Files.newInputStream(checked, LinkOption.NOFOLLOW_LINKS)) {
				return Optional.of(input.readAllBytes());
			}
		}
		catch (IOException ex) {
			return Optional.empty();
		}
	}

	/** manifestPath is relative to contentRoot, as are the bodyPath and localPath entries of the manifest. */
	public static CourseContent load(Path contentRoot, String manifestPath) {
		Path root = realRoot(contentRoot);
		List<String> problems = new ArrayList<>();
		Path manifestFile = ContentPaths.file(root, manifestPath, "manifest", problems);
		failIfAny(problems);
		Manifest manifest = read(manifestFile);
		failIfAny(ManifestValidator.validate(manifest));

		Map<String, Path> bodies = new LinkedHashMap<>();
		for (Lesson lesson : manifest.lessons()) {
			if (!lesson.available()) continue;
			Path body = ContentPaths.file(root, lesson.bodyPath(), "bodyPath of " + lesson.id() + ":", problems);
			if (body != null) bodies.put(lesson.id(), body);
		}
		Map<String, Path> stageBodies = new LinkedHashMap<>();
		if (manifest.projectStages() != null) {
			for (Manifest.Stage stage : manifest.projectStages()) {
				if (!stage.available()) continue;
				Path body = ContentPaths.file(root, stage.bodyPath(), "bodyPath of " + stage.id() + ":", problems);
				if (body != null) stageBodies.put(stage.id(), body);
			}
		}
		Map<String, Path> documentBodies = new LinkedHashMap<>();
		if (manifest.documents() != null) {
			for (Manifest.Document document : manifest.documents()) {
				Path body = ContentPaths.file(root, document.bodyPath(), "bodyPath of " + document.id() + ":", problems);
				if (body != null) documentBodies.put(document.id(), body);
			}
		}
		CopyProvenance.validate(manifest, root, problems);
		Map<String, ResourceFile> resources = new LinkedHashMap<>();
		Map<String, String> originalNames = new HashMap<>();
		for (Resource resource : manifest.resources()) {
			String name = "zip".equals(resource.kind()) ? resource.fileName()
				: resource.sourcePath().substring(resource.sourcePath().lastIndexOf('/') + 1);
			originalNames.put(resource.resourceId(), name);
			Path file = ContentPaths.file(root, resource.localPath(), "localPath of " + resource.resourceId() + ":", problems);
			if (file != null) {
				resources.put(resource.resourceId(), new ResourceFile(resource.resourceId(), file, resource.mediaType(), name));
			}
		}
		failIfAny(problems);

		LessonPipeline pipeline = new LessonPipeline(manifest, originalNames);
		for (Lesson lesson : manifest.lessons()) {
			if (!lesson.available()) continue;
			try {
				pipeline.parse(lesson, Files.readString(bodies.get(lesson.id()), StandardCharsets.UTF_8));
			}
			catch (IOException ex) {
				pipeline.problems().add(lesson.id() + ": body " + lesson.bodyPath() + " is not readable UTF-8 text");
			}
		}
		if (manifest.projectStages() != null) {
			for (Manifest.Stage stage : manifest.projectStages()) {
				if (!stage.available()) continue;
				try {
					pipeline.parse(stage, Files.readString(stageBodies.get(stage.id()), StandardCharsets.UTF_8));
				}
				catch (IOException ex) {
					pipeline.problems().add(stage.id() + ": body " + stage.bodyPath() + " is not readable UTF-8 text");
				}
			}
		}
		if (manifest.documents() != null) {
			for (Manifest.Document document : manifest.documents()) {
				try {
					pipeline.parse(document, Files.readString(documentBodies.get(document.id()), StandardCharsets.UTF_8));
				}
				catch (IOException ex) {
					pipeline.problems().add(document.id() + ": body " + document.bodyPath() + " is not readable UTF-8 text");
				}
			}
		}
		failIfAny(pipeline.problems());
		Map<String, LessonContent> lessons = pipeline.render();
		failIfAny(pipeline.problems());
		return new CourseContent(manifest, lessons, Collections.unmodifiableMap(resources), pipeline.stages(),
			pipeline.documents());
	}

	private static Path realRoot(Path contentRoot) {
		try {
			return contentRoot.toRealPath();
		}
		catch (IOException ex) {
			throw new ContentValidationException("content root " + contentRoot + " cannot be read");
		}
	}

	private static Manifest read(Path manifestFile) {
		try {
			var tree = JSON.readTree(Files.readString(manifestFile, StandardCharsets.UTF_8));
			if (tree != null && tree.path("schemaVersion").asInt() == 1) {
				if (tree.has("projectStages") || tree.has("provenance") || tree.has("documents")) {
					throw new ContentValidationException("schemaVersion 1 does not allow projectStages, provenance or documents");
				}
				for (var resource : tree.path("resources")) {
					if (resource.has("kind") || resource.has("stageId") || resource.has("fileName") || resource.has("memberResourceIds")) {
						throw new ContentValidationException("schemaVersion 1 does not allow resource kind or stageId");
					}
				}
				for (var link : tree.path("sourceToId")) {
					if (link.has("stageId") || link.has("zipResourceId")) {
						throw new ContentValidationException("schemaVersion 1 does not allow stageId or zipResourceId");
					}
				}
			}
			if (tree == null || tree.isNull()) throw new ContentValidationException("manifest must be an object");
			return JSON.treeToValue(tree, Manifest.class);
		}
		catch (IOException | JacksonException ex) {
			throw new ContentValidationException("manifest is not valid JSON for schema 1 or 2: " + ex.getMessage().lines().findFirst().orElse(""));
		}
	}

	private static void failIfAny(List<String> problems) {
		if (!problems.isEmpty()) throw new ContentValidationException(problems);
	}
}
