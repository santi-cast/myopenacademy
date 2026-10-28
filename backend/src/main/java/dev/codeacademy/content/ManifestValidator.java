package dev.codeacademy.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import dev.codeacademy.content.Manifest.Exercise;
import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Phase;
import dev.codeacademy.content.Manifest.Resource;
import dev.codeacademy.content.Manifest.SourceLink;

/**
 * Structural rules of docs/manifiesto.md that do not need the files: schema, provenance, IDs, phases, positions,
 * sourceToId.
 */
final class ManifestValidator {

	static final int SCHEMA_VERSION = 1;
	static final String PLACEHOLDER = "PENDIENTE_HASH_COMPLETO_DEL_COMMIT";
	private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
	private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

	private final Manifest manifest;
	private final List<String> problems = new ArrayList<>();
	private final Set<String> ids = new HashSet<>();
	private String prefix = "";

	private ManifestValidator(Manifest manifest) {
		this.manifest = manifest;
	}

	static List<String> validate(Manifest manifest) {
		ManifestValidator validator = new ManifestValidator(manifest);
		validator.run();
		return validator.problems;
	}

	private void run() {
		if (!Objects.equals(manifest.schemaVersion(), SCHEMA_VERSION)) {
			problems.add("schemaVersion " + manifest.schemaVersion() + " is not supported (expected " + SCHEMA_VERSION + ")");
		}
		String versionRef = manifest.source() == null ? null : manifest.source().versionRef();
		if (PLACEHOLDER.equals(versionRef)) problems.add("source.versionRef is still the placeholder " + PLACEHOLDER);
		else if (versionRef == null || !COMMIT.matcher(versionRef).matches()) {
			problems.add("source.versionRef must be a full 40-character commit hash, not " + versionRef);
		}
		if (manifest.course() == null || blank(manifest.course().id()) || blank(manifest.course().title())
				|| manifest.course().namespace() == null || !NAMESPACE.matcher(manifest.course().namespace()).matches()) {
			problems.add("course needs id, title and a lower-case namespace");
			return;
		}
		prefix = manifest.course().namespace() + "-";
		ids.add(manifest.course().id());
		if (manifest.phases() == null) {
			problems.add("manifest needs phases");
			return;
		}
		if (manifest.lessons() == null || manifest.resources() == null || manifest.sourceToId() == null) {
			problems.add("manifest needs lessons, resources and sourceToId");
			return;
		}
		Map<String, Phase> phases = phases();
		Map<String, Lesson> lessons = lessons();
		phaseOrder(phases, lessons);
		Map<String, Resource> resources = resources(lessons);
		sourceToId(lessons, resources);
	}

	private Map<String, Phase> phases() {
		Map<String, Phase> phases = new HashMap<>();
		List<Integer> positions = new ArrayList<>();
		for (Phase phase : manifest.phases()) {
			if (phase == null || blank(phase.id()) || blank(phase.title()) || phase.position() == null) {
				problems.add("phase " + (phase == null ? null : phase.id()) + " needs id, title and position");
				continue;
			}
			register(phase.id());
			phases.put(phase.id(), phase);
			positions.add(phase.position());
		}
		List<Integer> expected = IntStream.rangeClosed(1, positions.size()).boxed().toList();
		if (!positions.stream().sorted().toList().equals(expected)) {
			problems.add("phase positions must be 1.." + positions.size() + " without gaps or repeats");
		}
		return phases;
	}

	/**
	 * Every lesson is in a phase of its course and every phase has lessons. In lesson position order the phase
	 * positions never go back, so each phase is one block of consecutive lessons and the blocks follow phase order.
	 */
	private void phaseOrder(Map<String, Phase> phases, Map<String, Lesson> lessons) {
		Set<String> used = new HashSet<>();
		Phase before = null;
		for (Lesson lesson : lessons.values().stream().sorted(Comparator.comparing(Lesson::position)).toList()) {
			if (blank(lesson.phaseId())) {
				problems.add("lesson " + lesson.id() + " needs phaseId");
				continue;
			}
			Phase phase = phases.get(lesson.phaseId());
			if (phase == null) {
				problems.add("lesson " + lesson.id() + ": unknown phase " + lesson.phaseId());
				continue;
			}
			used.add(phase.id());
			if (before != null && before.position() > phase.position()) {
				problems.add("lesson " + lesson.id() + " (position " + lesson.position() + ") is in phase " + phase.id()
						+ " (position " + phase.position() + "), after a lesson of phase " + before.id() + " (position "
						+ before.position() + ")");
			}
			before = phase;
		}
		manifest.phases().stream().filter(Objects::nonNull).map(Phase::id).filter(phases::containsKey)
			.filter(id -> !used.contains(id)).distinct().forEach(id -> problems.add("phase " + id + " has no lessons"));
	}

	private Map<String, Lesson> lessons() {
		Map<String, Lesson> lessons = new HashMap<>();
		Set<String> sourcePaths = new HashSet<>();
		List<Integer> positions = new ArrayList<>();
		for (Lesson lesson : manifest.lessons()) {
			if (lesson == null || blank(lesson.id()) || blank(lesson.title()) || lesson.position() == null
					|| blank(lesson.sourcePath()) || lesson.sourceAvailable() == null || lesson.available() == null
					|| lesson.exercises() == null) {
				problems.add("lesson " + (lesson == null ? null : lesson.id())
						+ " needs id, title, position, sourcePath, sourceAvailable, available and exercises");
				continue;
			}
			register(lesson.id());
			lessons.put(lesson.id(), lesson);
			positions.add(lesson.position());
			if (!sourcePaths.add(lesson.sourcePath())) problems.add("duplicate lesson sourcePath " + lesson.sourcePath());
			if (lesson.available()) {
				if (!lesson.sourceAvailable()) problems.add(lesson.id() + " is available but not sourceAvailable");
				if (lesson.bodyPath() == null) problems.add(lesson.id() + " is available without bodyPath");
			}
			else if (lesson.bodyPath() != null || !lesson.exercises().isEmpty()) {
				problems.add(lesson.id() + " is pending but has bodyPath or exercises");
			}
			Set<String> exerciseTitles = new HashSet<>();
			for (Exercise exercise : lesson.exercises()) {
				if (exercise == null || blank(exercise.id()) || blank(exercise.title())) {
					problems.add("an exercise of " + lesson.id() + " needs id and title");
					continue;
				}
				register(exercise.id());
				// The title is what associates the exercise with its heading in the lesson.
				if (!exerciseTitles.add(exercise.title())) {
					problems.add("exercise " + exercise.id() + " of " + lesson.id() + " repeats the title «"
							+ exercise.title() + "»");
				}
				if (!lesson.id().equals(exercise.lessonId())) {
					problems.add("exercise " + exercise.id() + " belongs to " + exercise.lessonId() + ", not to " + lesson.id());
				}
			}
		}
		List<Integer> expected = IntStream.rangeClosed(1, positions.size()).boxed().toList();
		if (!positions.stream().sorted().toList().equals(expected)) {
			problems.add("lesson positions must be 1.." + positions.size() + " without gaps or repeats");
		}
		return lessons;
	}

	private Map<String, Resource> resources(Map<String, Lesson> lessons) {
		Map<String, Resource> resources = new HashMap<>();
		Set<String> sourcePaths = new HashSet<>();
		for (Resource resource : manifest.resources()) {
			if (resource == null || blank(resource.resourceId()) || blank(resource.sourcePath()) || blank(resource.mediaType())
					|| resource.sourcePath().endsWith("/")) {
				problems.add("resource " + (resource == null ? null : resource.resourceId())
						+ " needs resourceId, a file sourcePath, localPath and mediaType");
				continue;
			}
			register(resource.resourceId());
			resources.put(resource.resourceId(), resource);
			if (!lessons.containsKey(resource.lessonId())) {
				problems.add("resource " + resource.resourceId() + ": unknown lesson " + resource.lessonId());
			}
			if (!sourcePaths.add(resource.sourcePath())) problems.add("duplicate resource sourcePath " + resource.sourcePath());
		}
		return resources;
	}

	private void sourceToId(Map<String, Lesson> lessons, Map<String, Resource> resources) {
		Set<String> sourcePaths = new HashSet<>();
		for (SourceLink link : manifest.sourceToId()) {
			if (link == null || blank(link.sourcePath())) {
				problems.add("a sourceToId entry needs sourcePath");
				continue;
			}
			String path = link.sourcePath();
			if (!sourcePaths.add(path)) problems.add("duplicate sourceToId " + path);
			long targets = Stream.of(link.lessonId(), link.resourceId(), link.resourceIds(), link.courseId())
				.filter(Objects::nonNull).count();
			if (targets != 1 || (link.fragments() != null && link.lessonId() == null)) {
				problems.add("sourceToId " + path + " must have exactly one target");
				continue;
			}
			if (link.lessonId() != null) {
				Lesson lesson = lessons.get(link.lessonId());
				if (lesson == null) problems.add("sourceToId " + path + ": unknown lesson " + link.lessonId());
				else if (!lesson.sourcePath().equals(path)) {
					problems.add("sourceToId " + path + " does not match the sourcePath of " + link.lessonId());
				}
			}
			else if (link.resourceId() != null) {
				Resource resource = resources.get(link.resourceId());
				if (resource == null) problems.add("sourceToId " + path + ": unknown resource " + link.resourceId());
				else if (!resource.sourcePath().equals(path)) {
					problems.add("sourceToId " + path + " does not match the sourcePath of " + link.resourceId());
				}
			}
			else if (link.resourceIds() != null) {
				folder(link, resources);
			}
			else if (!link.courseId().equals(manifest.course().id())) {
				problems.add("sourceToId " + path + ": unknown course " + link.courseId());
			}
		}
	}

	private void folder(SourceLink link, Map<String, Resource> resources) {
		String folder = link.sourcePath();
		for (String id : link.resourceIds()) {
			if (!resources.containsKey(id)) problems.add("sourceToId " + folder + ": unknown resource " + id);
		}
		Set<String> inside = new HashSet<>();
		resources.values().stream().filter(resource -> resource.sourcePath().startsWith(folder))
			.forEach(resource -> inside.add(resource.resourceId()));
		if (!folder.endsWith("/") || link.resourceIds().size() != new HashSet<>(link.resourceIds()).size()
				|| !inside.equals(new HashSet<>(link.resourceIds()))) {
			problems.add("folder " + folder + " does not list exactly its resources");
		}
	}

	private void register(String id) {
		if (!ids.add(id)) problems.add("duplicate id " + id);
		if (!id.startsWith(prefix) || id.length() == prefix.length()) {
			problems.add("id " + id + " is not in namespace " + manifest.course().namespace());
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
