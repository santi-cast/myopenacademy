package dev.codeacademy.content;

import java.text.Normalizer;
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
 * Structural rules of docs/manifiesto.md that do not need the files: schema, provenance, IDs, phases, parts,
 * positions, sourceToId.
 */
final class ManifestValidator {

	static final String PLACEHOLDER = "PENDIENTE_HASH_COMPLETO_DEL_COMMIT";
	private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
	private static final Pattern COMBINING_MARK = Pattern.compile("\\p{M}");
	private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
	/** FORMATO.md 3.3: lower-case letters without accents, digits, hyphens and dots. */
	private static final Pattern DECLARED_ID = Pattern.compile("[a-z0-9]+([.-][a-z0-9]+)*");

	private final Manifest manifest;
	private final List<String> problems = new ArrayList<>();
	private final Set<String> ids = new HashSet<>();
	private String prefix = "";
	private int folderPasses;

	private ManifestValidator(Manifest manifest) {
		this.manifest = manifest;
	}

	static List<String> validate(Manifest manifest) {
		ManifestValidator validator = new ManifestValidator(manifest);
		validator.run();
		return validator.problems;
	}

	/** Passes over the registered resources that the folder rule of one validation makes; tests bound its cost. */
	static int folderPasses(Manifest manifest) {
		ManifestValidator validator = new ManifestValidator(manifest);
		validator.run();
		return validator.folderPasses;
	}

	private void run() {
		if (!Objects.equals(manifest.schemaVersion(), 1) && !Objects.equals(manifest.schemaVersion(), 2)) {
			problems.add("schemaVersion " + manifest.schemaVersion() + " is not supported (expected 1 or 2)");
		}
		String versionRef = manifest.source() == null ? null : manifest.source().versionRef();
		if (PLACEHOLDER.equals(versionRef)) problems.add("source.versionRef is still the placeholder " + PLACEHOLDER);
		else if (versionRef == null || !COMMIT.matcher(versionRef).matches()) {
			problems.add("source.versionRef must be a full 40-character commit hash, not " + versionRef);
		}
		String versionTag = manifest.source() == null ? null : manifest.source().versionTag();
		if (versionTag != null && (!ContentImportSource.VERSION_TAG.matcher(versionTag).matches()
				|| !versionTag.equals("v" + manifest.source().courseVersion()))) {
			problems.add("source.versionTag " + versionTag + " must be v + source.courseVersion ("
				+ manifest.source().courseVersion() + ")");
		}
		if (manifest.course() == null || blank(manifest.course().id()) || blank(manifest.course().title())
				|| manifest.course().namespace() == null || !NAMESPACE.matcher(manifest.course().namespace()).matches()) {
			problems.add("course needs id, title and a lower-case namespace");
			return;
		}
		prefix = manifest.course().namespace() + ".";
		if (manifest.course().recommended() != null) {
			for (String recommended : manifest.course().recommended()) {
				if (recommended == null || !NAMESPACE.matcher(recommended).matches() || recommended.equals(manifest.course().id())) {
					problems.add("course.recommended " + recommended + " must be the id of another course");
				}
			}
		}
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
		parts(phases);
		Map<String, Lesson> lessons = lessons();
		phaseOrder(phases, lessons);
		project(lessons);
		Map<String, Manifest.Stage> stages = stages();
		Map<String, Manifest.Document> courseDocuments = courseDocuments(phases);
		Map<String, Resource> resources = resources(lessons, stages, courseDocuments);
		sourceToId(lessons, stages, courseDocuments, resources);
		if (Objects.equals(manifest.schemaVersion(), 2)) {
			Map<String, String> documents = new HashMap<>();
			lessons.values().forEach(lesson -> document(documents, lesson.sourcePath(), lesson.id()));
			stages.values().forEach(stage -> document(documents, stage.sourcePath(), stage.id()));
			courseDocuments.values().forEach(document -> document(documents, document.sourcePath(), document.id()));
			// A lesson and a stage may own the same source path, so a file resource is unique per path and owner kind.
			Set<String> owned = new HashSet<>();
			Set<String> mapped = new HashSet<>();
			manifest.sourceToId().stream().filter(Objects::nonNull).filter(link -> link.resourceId() != null)
				.forEach(link -> mapped.add(link.sourcePath()));
			for (Resource resource : resources.values()) {
				if ("zip".equals(resource.kind())) continue;
				String path = resource.sourcePath();
				if (!ContentPaths.isPlainRelative(path)) problems.add("invalid sourcePath " + path);
				if (documents.containsKey(path)) problems.add("duplicate sourcePath " + path);
				if (!owned.add(path + "\u0000" + ownerKind(resource))) problems.add("duplicate sourcePath " + path);
				if (!mapped.contains(path)) problems.add("missing sourceToId for " + resource.resourceId());
			}
			for (SourceLink link : manifest.sourceToId()) {
				if (link == null || link.sourcePath() == null) continue;
				String path = link.sourcePath();
				if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
				if (!ContentPaths.isPlainRelative(path)) problems.add("invalid sourceToId path " + link.sourcePath());
			}
		}
	}

	private void document(Map<String, String> documents, String path, String id) {
		if (!ContentPaths.isPlainRelative(path)) problems.add("invalid sourcePath " + path);
		if (documents.putIfAbsent(path, id) != null) problems.add("duplicate sourcePath " + path);
		boolean mapped = manifest.sourceToId().stream().filter(Objects::nonNull)
			.anyMatch(link -> Objects.equals(path, link.sourcePath())
				&& (id.equals(link.lessonId()) || id.equals(link.stageId()) || id.equals(link.documentId())
					|| id.equals(link.resourceId())));
		if (!mapped) problems.add("missing sourceToId for " + id);
	}

	private Map<String, Phase> phases() {
		Map<String, Phase> phases = new HashMap<>();
		List<Integer> positions = new ArrayList<>();
		for (Phase phase : manifest.phases()) {
			if (phase == null || blank(phase.id()) || blank(phase.title()) || phase.position() == null) {
				problems.add("phase " + (phase == null ? null : phase.id()) + " needs id, title and position");
				continue;
			}
			registerDeclared(phase.id());
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
	 * The parts of the course (H12), optional in schema 2 and checked as the importer checks {@code partes} of
	 * curso.json: an ID like any other of the course, a plain-text title, positions 1..n, and each listed phase exists
	 * and is in one part only; with parts, every phase is in one. An incomplete part is left out of the other checks.
	 */
	private void parts(Map<String, Phase> phases) {
		if (manifest.parts() == null) return;
		if (!Objects.equals(manifest.schemaVersion(), 2)) {
			problems.add("schemaVersion 1 does not allow parts");
			return;
		}
		Map<String, String> partOf = new HashMap<>();
		List<Integer> positions = new ArrayList<>();
		for (Manifest.Part part : manifest.parts()) {
			if (part == null || blank(part.id()) || blank(part.title()) || part.position() == null
					|| part.phaseIds() == null) {
				problems.add("part " + (part == null ? null : part.id()) + " needs id, title, position and phaseIds");
				continue;
			}
			registerDeclared(part.id());
			positions.add(part.position());
			String markdown = CourseDescriptor.markdown(part.title());
			if (markdown != null) {
				problems.add("part " + part.id() + ": title «" + part.title() + "» has Markdown formatting (" + markdown
					+ "); titles are plain text");
			}
			for (String phaseId : part.phaseIds()) {
				if (phaseId == null || !phases.containsKey(phaseId)) {
					problems.add("part " + part.id() + ": unknown phase " + phaseId);
				}
				else if (partOf.containsKey(phaseId)) {
					problems.add("part " + part.id() + ": phase " + phaseId + " is already in part " + partOf.get(phaseId));
				}
				else partOf.put(phaseId, part.id());
			}
		}
		if (!positions.stream().sorted().toList().equals(IntStream.rangeClosed(1, positions.size()).boxed().toList())) {
			problems.add("part positions must be 1.." + positions.size() + " without gaps or repeats");
		}
		manifest.phases().stream().filter(Objects::nonNull).map(Phase::id).filter(phases::containsKey)
			.filter(id -> !partOf.containsKey(id)).distinct().forEach(id -> problems.add("phase " + id + " is in no part"));
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
			registerDeclared(lesson.id());
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
				registerDeclared(exercise.id());
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

	/** The course project (FORMATO.md 8): its kind, and for a module project a module of the course. */
	private void project(Map<String, Lesson> lessons) {
		Manifest.Project project = manifest.course().project();
		if (project == null) return;
		if (Manifest.Project.MODULE.equals(project.kind())) {
			if (project.lessonId() == null || !lessons.containsKey(project.lessonId())) {
				problems.add("project: unknown module " + project.lessonId());
			}
		}
		else if (Manifest.Project.STAGES.equals(project.kind())) {
			if (project.lessonId() != null) problems.add("project: a project by stages has no module");
		}
		else problems.add("project: unsupported kind " + project.kind());
	}

	private Map<String, Manifest.Stage> stages() {
		Map<String, Manifest.Stage> stages = new HashMap<>();
		if (!Objects.equals(manifest.schemaVersion(), 2)) return stages;
		if (manifest.projectStages() == null) {
			problems.add("schemaVersion 2 needs projectStages");
			return stages;
		}
		List<Integer> positions = new ArrayList<>();
		for (Manifest.Stage stage : manifest.projectStages()) {
			if (stage == null || blank(stage.id()) || blank(stage.title()) || stage.position() == null
					|| blank(stage.sourcePath()) || stage.sourceAvailable() == null || stage.available() == null) {
				problems.add("stage needs id, title, position, sourcePath, sourceAvailable and available");
				continue;
			}
			registerDeclared(stage.id());
			stages.put(stage.id(), stage);
			positions.add(stage.position());
			if (stage.available()) {
				if (!stage.sourceAvailable()) problems.add(stage.id() + " is available but not sourceAvailable");
				if (blank(stage.bodyPath())) problems.add(stage.id() + " is available without bodyPath");
			}
			else if (stage.bodyPath() != null) problems.add(stage.id() + " is pending but has bodyPath");
		}
		if (!positions.stream().sorted().toList().equals(IntStream.rangeClosed(1, positions.size()).boxed().toList())) {
			problems.add("stage positions must be 1.." + positions.size() + " without gaps or repeats");
		}
		return stages;
	}

	/**
	 * The documents of the course without progress (H8): the challenges of a phase, at most one per phase, and the
	 * glossary, at most one. Optional in schema 2; schema 1 has none.
	 */
	private Map<String, Manifest.Document> courseDocuments(Map<String, Phase> phases) {
		Map<String, Manifest.Document> documents = new HashMap<>();
		if (manifest.documents() == null) return documents;
		if (!Objects.equals(manifest.schemaVersion(), 2)) {
			problems.add("schemaVersion 1 does not allow documents");
			return documents;
		}
		Set<String> phasesWithChallenges = new HashSet<>();
		Set<String> courseKinds = new HashSet<>();
		for (Manifest.Document document : manifest.documents()) {
			if (document == null || blank(document.id()) || blank(document.title()) || blank(document.sourcePath())
					|| blank(document.bodyPath())) {
				problems.add("document needs id, kind, title, sourcePath and bodyPath");
				continue;
			}
			registerDeclared(document.id());
			documents.put(document.id(), document);
			if (Manifest.Document.CHALLENGES.equals(document.kind())) {
				if (document.phaseId() == null || !phases.containsKey(document.phaseId())) {
					problems.add("document " + document.id() + ": unknown phase " + document.phaseId());
				}
				else if (!phasesWithChallenges.add(document.phaseId())) {
					problems.add("phase " + document.phaseId() + " has two challenge documents");
				}
			}
			else if (Manifest.Document.COURSE_FILES.containsKey(document.kind())) {
				// The glossary, the paths and the global cheat sheet belong to the whole course: no phase, one of each.
				if (document.phaseId() != null) {
					problems.add("document " + document.id() + ": a " + document.kind() + " document has no phase");
				}
				if (!courseKinds.add(document.kind())) problems.add("the course has two " + document.kind() + " documents");
			}
			else problems.add("document " + document.id() + ": unsupported kind " + document.kind());
		}
		return documents;
	}

	private Map<String, Resource> resources(Map<String, Lesson> lessons, Map<String, Manifest.Stage> stages,
			Map<String, Manifest.Document> documents) {
		Map<String, Resource> resources = new HashMap<>();
		Set<String> sourcePaths = new HashSet<>();
		// The entries that name each archive, in sourceToId order: a ZIP finds its folder without scanning every link.
		Map<String, List<SourceLink>> decorations = new HashMap<>();
		if (Objects.equals(manifest.schemaVersion(), 2)) {
			manifest.sourceToId().stream().filter(Objects::nonNull).filter(link -> link.zipResourceId() != null)
				.forEach(link -> decorations.computeIfAbsent(link.zipResourceId(), id -> new ArrayList<>()).add(link));
		}
		for (Resource resource : manifest.resources()) {
			if (resource == null || blank(resource.resourceId()) || blank(resource.sourcePath()) || blank(resource.mediaType())
					|| (resource.sourcePath().endsWith("/") && !"zip".equals(resource.kind()))) {
				problems.add("resource " + (resource == null ? null : resource.resourceId())
						+ " needs resourceId, a file sourcePath, localPath and mediaType");
				continue;
			}
			register(resource.resourceId());
			// Resource links admit letters and digits but never combining marks, so a decomposed ID could not be linked;
			// an ID is also kept in NFC so one name has one spelling.
			if (COMBINING_MARK.matcher(resource.resourceId()).find()) {
				problems.add("resource id " + resource.resourceId()
					+ " contains combining marks; use composed (NFC) characters");
			}
			else if (!Normalizer.isNormalized(resource.resourceId(), Normalizer.Form.NFC)) {
				problems.add("resource id " + resource.resourceId() + " is not in composed (NFC) form");
			}
			resources.put(resource.resourceId(), resource);
			if (Objects.equals(manifest.schemaVersion(), 2)) {
				if ("zip".equals(resource.kind())) {
					String folder = resource.sourcePath();
					String plain = folder.endsWith("/") ? folder.substring(0, folder.length() - 1) : "";
					String name = plain.substring(plain.lastIndexOf('/') + 1) + ".zip";
					if (!ContentPaths.isPlainRelative(plain) || !"application/zip".equals(resource.mediaType())
							|| !name.equals(resource.fileName()) || resource.memberResourceIds() == null) {
						problems.add("invalid ZIP metadata for " + resource.resourceId());
					}
					// A shared folder keeps one archive: its members are a subset of the files the folder lists.
					// Null members were already reported as invalid metadata above.
					boolean mapped = resource.memberResourceIds() == null
						|| decorations.getOrDefault(resource.resourceId(), List.of()).stream()
							.anyMatch(link -> folder.equals(link.sourcePath())
								&& link.resourceIds() != null && link.resourceIds().containsAll(resource.memberResourceIds()));
					if (!mapped) problems.add("ZIP must decorate the folder that lists its members: " + resource.resourceId());
				}
				else if (!"file".equals(resource.kind())) {
					problems.add("resource " + resource.resourceId() + ": unsupported kind " + resource.kind());
				}
				else if (resource.fileName() != null || resource.memberResourceIds() != null) {
					problems.add("file resource cannot have ZIP metadata: " + resource.resourceId());
				}
			}
			long owners = Stream.of(resource.lessonId(), resource.stageId(), resource.documentId()).filter(Objects::nonNull).count();
			if (owners != 1) {
				problems.add("resource " + resource.resourceId() + " must have exactly one owner");
			}
			else if (resource.documentId() != null) {
				if (!documents.containsKey(resource.documentId())) {
					problems.add("resource " + resource.resourceId() + ": unknown document " + resource.documentId());
				}
			}
			else if (resource.stageId() != null) {
				if (!stages.containsKey(resource.stageId())) {
					problems.add("resource " + resource.resourceId() + ": unknown stage " + resource.stageId());
				}
			}
			else if (!lessons.containsKey(resource.lessonId())) {
				problems.add("resource " + resource.resourceId() + ": unknown lesson " + resource.lessonId());
			}
			// One path belongs to at most one owner of each kind (lesson, stage, document), never to two of the same kind.
			if (!sourcePaths.add(resource.sourcePath() + "\u0000" + ownerKind(resource))) {
				problems.add("duplicate resource sourcePath " + resource.sourcePath());
			}
		}
		return resources;
	}

	private void sourceToId(Map<String, Lesson> lessons, Map<String, Manifest.Stage> stages,
			Map<String, Manifest.Document> documents, Map<String, Resource> resources) {
		Set<String> sourcePaths = new HashSet<>();
		Map<String, Set<String>> folders = folderMembers(resources);
		for (SourceLink link : manifest.sourceToId()) {
			if (link == null || blank(link.sourcePath())) {
				problems.add("a sourceToId entry needs sourcePath");
				continue;
			}
			String path = link.sourcePath();
			if (!sourcePaths.add(path)) problems.add("duplicate sourceToId " + path);
			long targets = Stream.of(link.lessonId(), link.stageId(), link.documentId(), link.resourceId(), link.resourceIds(),
				link.courseId()).filter(Objects::nonNull).count();
			if (link.zipResourceId() != null && link.resourceIds() == null) {
				problems.add("zipResourceId only decorates a folder: " + path);
			}
			if (targets != 1 || (link.fragments() != null && link.lessonId() == null && link.stageId() == null
					&& link.documentId() == null)) {
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
			else if (link.stageId() != null) {
				Manifest.Stage stage = stages.get(link.stageId());
				if (stage == null) problems.add("sourceToId " + path + ": unknown stage " + link.stageId());
				else if (!stage.sourcePath().equals(path)) {
					problems.add("sourceToId " + path + " does not match the sourcePath of " + link.stageId());
				}
			}
			else if (link.documentId() != null) {
				Manifest.Document document = documents.get(link.documentId());
				if (document == null) problems.add("sourceToId " + path + ": unknown document " + link.documentId());
				else if (!document.sourcePath().equals(path)) {
					problems.add("sourceToId " + path + " does not match the sourcePath of " + link.documentId());
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
				folder(link, resources, folders);
			}
			else if (!link.courseId().equals(manifest.course().id())) {
				problems.add("sourceToId " + path + ": unknown course " + link.courseId());
			}
		}
	}

	/**
	 * The non-ZIP resources under each listed folder, from one pass over the resources. Only a folder that ends in '/'
	 * is ever compared with its contents, and a path starts with such a folder exactly when the folder is one of the
	 * path's own prefixes that end in '/', so looking those prefixes up finds the same members as a scan per folder.
	 */
	private Map<String, Set<String>> folderMembers(Map<String, Resource> resources) {
		Map<String, Set<String>> members = new HashMap<>();
		manifest.sourceToId().stream().filter(Objects::nonNull).filter(link -> link.resourceIds() != null)
			.map(SourceLink::sourcePath).filter(path -> path != null && path.endsWith("/"))
			.forEach(path -> members.put(path, new HashSet<>()));
		if (members.isEmpty()) return members;
		folderPasses++;
		for (Resource resource : resources.values()) {
			if ("zip".equals(resource.kind())) continue;
			String path = resource.sourcePath();
			for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
				Set<String> inside = members.get(path.substring(0, slash + 1));
				if (inside != null) inside.add(resource.resourceId());
			}
		}
		return members;
	}

	private void folder(SourceLink link, Map<String, Resource> resources, Map<String, Set<String>> folders) {
		String folder = link.sourcePath();
		for (String id : link.resourceIds()) {
			if (!resources.containsKey(id)) problems.add("sourceToId " + folder + ": unknown resource " + id);
		}
		Set<String> inside = folders.getOrDefault(folder, Set.of());
		// The owners that claim the folder are the owners of the files its entry lists: at most one lesson and one
		// stage, because only a lesson and a stage may share a path without being a content conflict.
		Set<String> claims = new HashSet<>();
		Set<String> lessons = new HashSet<>();
		Set<String> stages = new HashSet<>();
		Set<String> documents = new HashSet<>();
		for (String id : link.resourceIds()) {
			Resource member = resources.get(id);
			if (member == null) continue;
			claims.add(owner(member));
			if (member.stageId() != null) stages.add(member.stageId());
			else if (member.documentId() != null) documents.add(member.documentId());
			else if (member.lessonId() != null) lessons.add(member.lessonId());
		}
		if (lessons.size() > 1 || stages.size() > 1 || documents.size() > 1) {
			problems.add("folder " + folder + " cannot be claimed by two lessons, two stages or two documents");
		}
		if (!folder.endsWith("/") || link.resourceIds().size() != new HashSet<>(link.resourceIds()).size()
				|| !inside.equals(new HashSet<>(link.resourceIds()))) {
			problems.add("folder " + folder + " does not list exactly its resources");
		}
		if (link.zipResourceId() != null) {
			Resource zip = resources.get(link.zipResourceId());
			if (zip == null || !"zip".equals(zip.kind()) || !folder.equals(zip.sourcePath())) {
				problems.add("folder ZIP does not match ordered members: " + folder);
				return;
			}
			// A shared folder keeps one archive: its members are a subset of the listed files of its owners.
			Set<String> listed = new HashSet<>(link.resourceIds());
			List<String> members = zip.memberResourceIds();
			if (members == null || members.size() != new HashSet<>(members).size() || !listed.containsAll(members)) {
				problems.add("folder ZIP members must be a subset of its folder files: " + folder);
				return;
			}
			for (String id : members) {
				Resource member = resources.get(id);
				if (member == null) continue;
				if (!"file".equals(member.kind()) || !claims.contains(owner(member))) {
					problems.add("ZIP member must be a file of an owner that claims the folder: " + id);
				}
			}
			if (!claims.contains(owner(zip))) {
				problems.add("folder ZIP must be owned by an owner that claims the folder: " + folder);
			}
		}
	}

	private static String owner(Resource resource) {
		if (resource.lessonId() != null) return resource.lessonId();
		return resource.stageId() != null ? resource.stageId() : resource.documentId();
	}

	private static String ownerKind(Resource resource) {
		if (resource.lessonId() != null) return "lesson";
		return resource.stageId() != null ? "stage" : "document";
	}

	/** An ID that comes from curso.json: in the course namespace and with the form of FORMATO.md 3.3. */
	private void registerDeclared(String id) {
		register(id);
		if (id != null && !DECLARED_ID.matcher(id).matches()) {
			problems.add("id " + id + " must be lower-case letters without accents, digits, hyphens and dots");
		}
	}

	/** Every ID is unique and starts with {@code <course>.} (FORMATO.md 3.3); resource IDs are the academy's own. */
	private void register(String id) {
		if (id == null) {
			problems.add("missing id in namespace " + manifest.course().namespace());
			return;
		}
		if (!ids.add(id)) problems.add("duplicate id " + id);
		if (!id.startsWith(prefix) || id.length() == prefix.length()) {
			problems.add("id " + id + " is not in namespace " + manifest.course().namespace() + " (expected " + prefix + "…)");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
