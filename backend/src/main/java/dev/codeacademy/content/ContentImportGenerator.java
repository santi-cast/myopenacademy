package dev.codeacademy.content;

import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.content.Manifest.Exercise;
import dev.codeacademy.content.Manifest.Lesson;
import dev.codeacademy.content.Manifest.Resource;
import dev.codeacademy.content.Manifest.SourceLink;
import dev.codeacademy.content.Manifest.Stage;

/**
 * Offline and deterministic candidate generator. It reads one immutable source commit, keeps every accepted identity
 * and copy, and writes a complete new candidate root; it never installs it, starts Spring or touches a database.
 * <p>
 * Rules: the active manifest is the permanent identity map; source bodies and resources are literal copies of Git
 * blobs; folders are enumerated recursively (including dotfiles), each folder entry lists every file its owners claim
 * beneath it and it is archived through {@link FolderZip}; every cross-document fragment is mapped through
 * {@link HeadingIds} and registered for lessons and stages alike;
 * protected names are refused before any blob is read or serialized and excluded with a report entry; anything it
 * cannot map is reported as a finding instead of being guessed, skipped or rewritten. The finished candidate is
 * revalidated with the pure {@link ContentLoader}.
 */
final class ContentImportGenerator {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
	private static final Pattern EXTERNAL = Pattern.compile("(?i)^https?://[^\\s/?#]+.*");
	private static final String FILE = "file";
	private static final String ZIP = "zip";

	private ContentImportGenerator() {
	}

	/**
	 * The outcome of one generation: findings stop all writes, reported findings never do, and the report is always
	 * returned for the caller. An inert formatting removal and a protected source path excluded by policy are
	 * reported instead of blocking.
	 */
	record Result(boolean written, List<String> findings, List<String> reported, int lessons, int stages, int resources,
			String report) {
	}

	static Result generate(ContentImportCli.Paths paths) throws IOException {
		// One object stream for the whole generation: every blob of the pinned tree is read through it exactly once.
		try (ContentImportSource source = ContentImportSource.open(paths.source(), paths.commit())) {
			Map<String, String> baseRefs = new TreeMap<>();
			List<Plan> plans = new ArrayList<>();
			Set<String> findings = new TreeSet<>();
			Set<String> reported = new TreeSet<>();
			// The installed courses: the one this source declares is regenerated, every other one is carried unchanged.
			List<CourseContent> installed = ContentLoader.loadInstalled(paths.active());
			for (CourseContent course : installed) {
				baseRefs.put(course.manifest().course().id(), course.manifest().source().versionRef());
			}
			CourseDescriptor descriptor = descriptor(source, findings);
			if (descriptor == null) {
				return new Result(false, List.copyOf(findings), List.copyOf(reported), 0, 0, 0,
					report(paths, baseRefs, 0, 0, 0, 0, false, findings, reported));
			}
			Manifest previous = null;
			List<String> carried = new ArrayList<>();
			for (CourseContent course : installed) {
				if (course.manifest().course().id().equals(descriptor.id())) previous = course.manifest();
				else carried.add(course.manifest().course().id());
			}
			plans.add(new Builder(descriptor, previous, paths.active(), source, findings, reported).build());
			int lessons = 0;
			int stages = 0;
			int resources = 0;
			int folders = 0;
			for (Plan plan : plans) {
				lessons += plan.skeleton().lessons().size();
				stages += plan.skeleton().projectStages().size();
				for (Resource resource : plan.skeleton().resources()) {
					if (ZIP.equals(resource.kind())) folders++;
					else resources++;
				}
			}
			if (!findings.isEmpty()) {
				return new Result(false, List.copyOf(findings), List.copyOf(reported), lessons, stages, resources,
					report(paths, baseRefs, lessons, stages, resources, folders, false, findings, reported));
			}
			Files.createDirectory(paths.output());
			for (Plan plan : plans) write(plan, paths.output());
			for (String course : carried) carry(paths.active().resolve(course), paths.output().resolve(course));
			// The finished candidate is accepted only if the pure runtime loader accepts it, ZIP bytes included.
			ContentLoader.loadAll(paths.output());
			return new Result(true, List.of(), List.copyOf(reported), lessons, stages, resources,
				report(paths, baseRefs, lessons, stages, resources, folders, true, List.of(), reported));
		}
	}

	private static void write(Plan plan, Path output) throws IOException {
		Manifest skeleton = plan.skeleton();
		Files.createDirectories(output.resolve(skeleton.course().id()));
		List<Manifest.CopiedFile> copies = new ArrayList<>();
		for (Copy copy : plan.copies()) {
			// A document the builder already read is written from those bytes: one read per source path, not two.
			byte[] bytes = plan.documents().get(copy.sourcePath());
			if (bytes == null) bytes = plan.source().read(copy.sourcePath());
			Path target = output.resolve(copy.localPath());
			Files.createDirectories(target.getParent());
			Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			String hash = plan.hashes().get(copy.sourcePath());
			if (hash == null) hash = ContentUpdateComparison.sha(bytes);
			copies.add(new Manifest.CopiedFile(copy.sourcePath(), copy.localPath(), hash, hash, null));
		}
		List<Resource> archives = new ArrayList<>();
		for (Resource resource : skeleton.resources()) {
			if (ZIP.equals(resource.kind())) archives.add(resource);
		}
		Map<String, MessageDigest> digests = new TreeMap<>();
		// The manifest is validated once for every archive of this course, so the cost stops multiplying by folders.
		FolderZip.writeAll(skeleton, output, archives.stream().map(Resource::resourceId).toList(), localPath -> {
			Path target = output.resolve(localPath);
			Files.createDirectories(target.getParent());
			MessageDigest digest = sha256();
			digests.put(localPath, digest);
			return new DigestOutputStream(
				Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), digest);
		});
		List<Manifest.GeneratedFile> generated = new ArrayList<>();
		for (Resource resource : archives) {
			// The hash of the archive is the digest of the bytes just written, never a second read of the file.
			generated.add(new Manifest.GeneratedFile(resource.localPath(),
				HexFormat.of().formatHex(digests.get(resource.localPath()).digest()), resource.memberResourceIds()));
		}
		Manifest finished = new Manifest(skeleton.schemaVersion(), skeleton.source(), skeleton.course(), skeleton.phases(),
			skeleton.lessons(), skeleton.resources(), skeleton.sourceToId(), skeleton.projectStages(),
			new Manifest.Provenance(List.copyOf(copies), List.copyOf(generated)));
		Files.writeString(output.resolve(skeleton.course().id() + "/manifest.json"),
			JSON.writerWithDefaultPrettyPrinter().writeValueAsString(finished) + "\n",
			StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	/** The course's own curso.json at the pinned commit, or null with the reasons among the findings. */
	private static CourseDescriptor descriptor(ContentImportSource source, Set<String> findings) throws IOException {
		byte[] bytes = source.read(CourseDescriptor.PATH);
		if (bytes == null) {
			findings.add("source has no " + CourseDescriptor.PATH + " at " + source.commit());
			return null;
		}
		List<String> problems = new ArrayList<>();
		CourseDescriptor descriptor = CourseDescriptor.parse(bytes, problems);
		findings.addAll(problems);
		return descriptor;
	}

	/** Another installed course stays exactly as it is: its accepted files are copied byte for byte. */
	private static void carry(Path from, Path to) throws IOException {
		try (var files = Files.walk(from)) {
			for (Path path : files.toList()) {
				if (Files.isSymbolicLink(path)) throw new IOException("symbolic links are not allowed: " + path);
				Path target = to.resolve(from.relativize(path).toString());
				if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
				else Files.copy(path, target);
			}
		}
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String report(ContentImportCli.Paths paths, Map<String, String> baseRefs, int lessons, int stages,
			int resources, int folders, boolean written, Collection<String> findings, Collection<String> reported) {
		ObjectNode data = JSON.createObjectNode();
		data.put("format", 1);
		data.put("active", paths.active().toString());
		data.put("source", paths.source().toString());
		data.put("commit", paths.commit());
		data.put("output", paths.output().toString());
		data.put("report", paths.report().toString());
		ObjectNode refs = data.putObject("baseRefs");
		baseRefs.forEach(refs::put);
		data.put("lessons", lessons);
		data.put("stages", stages);
		data.put("resources", resources);
		data.put("folders", folders);
		data.put("written", written);
		ArrayNode list = data.putArray("findings");
		findings.forEach(list::add);
		// Non-blocking: inert formatting the sanitizer removed and protected source paths excluded by policy, each
		// with its document or source path.
		ArrayNode observations = data.putArray("reported");
		reported.forEach(observations::add);
		return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n";
	}

	private record Copy(String sourcePath, String localPath) {
	}

	/**
	 * Every folder entry lists every file claimed beneath it, at any depth: one pass over the ordered claimed resources,
	 * never a rescan of the whole collection per folder, so a folder count and a resource count never multiply. Each
	 * claim reaches only the claimed folders above its own path, and every list follows the order of that pass.
	 */
	static <T> Map<String, List<String>> folderMembers(SortedMap<String, List<T>> claimedByPath,
			Collection<String> folders, Function<? super T, String> id) {
		Map<String, List<String>> members = new TreeMap<>();
		for (String folder : folders) members.put(folder, new ArrayList<>());
		for (Map.Entry<String, List<T>> entry : claimedByPath.entrySet()) {
			String path = entry.getKey();
			for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
				List<String> ids = members.get(path.substring(0, slash + 1));
				if (ids == null) continue;
				for (T claim : entry.getValue()) ids.add(id.apply(claim));
			}
		}
		members.replaceAll((folder, ids) -> List.copyOf(ids));
		return members;
	}

	/** One course plan: its manifest, its copies, its object stream and the bytes and hashes already read for it. */
	private record Plan(Manifest skeleton, List<Copy> copies, ContentImportSource source,
			Map<String, byte[]> documents, Map<String, String> hashes) {
	}

	private record ResourcePlan(String id, String localPath, String mediaType, String owner, boolean stage) {
	}

	/** One claimed source path for one owner: a lesson and a stage may hold the same path with different IDs. */
	private record Claim(String path, String owner) {
	}

	/** One course: the identity its curso.json declares plus everything read from the pinned tree for it. */
	private static final class Builder {

		private final CourseDescriptor descriptor;
		/** The same course as installed now, if any: only to report local adaptations, never as identity. */
		private final Manifest previous;
		private final Path acceptedRoot;
		private final ContentImportSource source;
		private final Set<String> findings;
		private final Set<String> reported;
		private final String directory;
		/** Committed bytes per source path of the documents already read: the copy phase writes these, not a re-read. */
		private final Map<String, byte[]> documentBodies = new TreeMap<>();
		private final Map<String, String> texts = new TreeMap<>();
		private final Map<String, String> documentPaths = new TreeMap<>();
		private final Map<String, String> documents = new TreeMap<>();
		private final Map<String, List<String>> anchors = new TreeMap<>();
		private final Map<String, Map<String, String>> fragments = new TreeMap<>();
		private final Map<Claim, ResourcePlan> resources =
			new TreeMap<>(Comparator.comparing(Claim::path).thenComparing(Claim::owner));
		/** The same claims in path order: one lookup per path, never a scan of the whole collection. */
		private final SortedMap<String, List<ResourcePlan>> claimsByPath = new TreeMap<>();
		/** Source paths whose committed bytes are already hashed: a copy never hashes them again. */
		private final Map<String, String> hashes = new TreeMap<>();
		private final Map<String, FolderPlan> folders = new TreeMap<>();
		private final Set<String> stageIds = new TreeSet<>();
		/** Charset declared by each folder's codificacion.txt, read once per folder ("" for none). */
		private final Map<String, String> csvCharsets = new TreeMap<>();
		/**
		 * Course documents that are not lessons or stages and are not imported: the index and, until they get their own
		 * page (H8), each phase's challenges and the course glossary. A link to one of them leads to the course entry, never a guessed anchor.
		 */
		private final Set<String> entryPaths = new TreeSet<>();

		Builder(CourseDescriptor descriptor, Manifest previous, Path acceptedRoot, ContentImportSource source,
				Set<String> findings, Set<String> reported) {
			this.descriptor = descriptor;
			this.previous = previous;
			this.acceptedRoot = acceptedRoot;
			this.source = source;
			this.findings = findings;
			this.reported = reported;
			this.directory = descriptor.id();
		}

		Plan build() throws IOException {
			entryPaths.add(CourseDescriptor.INDEX);
			// Any course may have its glossary (FORMATO.md 9); until H8 gives it its own page, it leads to the entry.
			if (source.file(CourseDescriptor.GLOSSARY)) entryPaths.add(CourseDescriptor.GLOSSARY);
			for (CourseDescriptor.Phase phase : descriptor.phases()) {
				if (phase.challenges() != null && allowed(phase.challenges())) entryPaths.add(phase.challenges());
			}
			List<Lesson> lessons = lessons();
			List<Stage> stages = stages();
			installedResources();
			try {
				references();
			}
			catch (java.io.UncheckedIOException ex) {
				throw ex.getCause();
			}
			Manifest skeleton = manifest(lessons, stages);
			// Everything is validated before writing: exercises by exact title, links and fragments,
			// with the same pipeline the runtime uses. Reports accumulate instead of stopping at the first.
			validate(skeleton);
			return new Plan(skeleton, copies(skeleton), source, Map.copyOf(documentBodies), Map.copyOf(hashes));
		}

		/**
		 * Modules in the order of curso.json, with the IDs and exercises it declares. A pending module is listed
		 * without a body; an available one is read once. Nothing is invented: an exercise heading the module has and
		 * curso.json does not declare is a problem of the shared pipeline, not a new ID.
		 */
		private List<Lesson> lessons() throws IOException {
			List<Lesson> lessons = new ArrayList<>();
			int position = 0;
			for (CourseDescriptor.Phase phase : descriptor.phases()) {
				for (CourseDescriptor.Module module : phase.modules()) {
					position++;
					if (!allowed(module.file())) continue;
					if (!module.available()) {
						documents.put(module.file(), module.id());
						lessons.add(new Lesson(module.id(), module.title(), position, phase.id(), module.file(), false,
							false, null, List.of()));
						continue;
					}
					byte[] body = source.read(module.file());
					if (body == null) {
						findings.add("source body unavailable " + module.file());
						continue;
					}
					String bodyPath = directory + "/lessons/" + module.id() + ".md";
					addDocument(module.id(), module.file(), body, bodyPath);
					List<Exercise> exercises = module.exercises().stream()
						.map(exercise -> new Exercise(exercise.id(), module.id(), exercise.title())).toList();
					lessons.add(new Lesson(module.id(), module.title(), position, phase.id(), module.file(), true, true,
						bodyPath, exercises));
				}
			}
			return lessons;
		}

		private List<Stage> stages() throws IOException {
			List<Stage> stages = new ArrayList<>();
			int position = 0;
			for (CourseDescriptor.Stage stage : descriptor.stages()) {
				position++;
				if (!allowed(stage.file())) continue;
				stageIds.add(stage.id());
				if (!stage.available()) {
					documents.put(stage.file(), stage.id());
					stages.add(new Stage(stage.id(), stage.title(), position, stage.file(), false, false, null));
					continue;
				}
				byte[] body = source.read(stage.file());
				if (body == null) {
					findings.add("source body unavailable " + stage.file());
					continue;
				}
				String bodyPath = directory + "/project/" + stage.id() + ".md";
				addDocument(stage.id(), stage.file(), body, bodyPath);
				stages.add(new Stage(stage.id(), stage.title(), position, stage.file(), true, true, bodyPath));
			}
			return stages;
		}

		/** Reads one available document once: it is copied, anchored and scanned with the same bytes. */
		private void addDocument(String id, String sourcePath, byte[] body, String acceptedBodyPath) throws IOException {
			String text = text(sourcePath, body);
			if (text == null) return;
			checkAccepted(sourcePath, acceptedBodyPath, body);
			documentBodies.put(sourcePath, body);
			texts.put(id, text);
			documentPaths.put(id, sourcePath);
			documents.put(sourcePath, id);
			anchors.put(sourcePath, ContentImportDocuments.anchors(text));
		}

		/**
		 * Resource IDs are the academy's own (curso.json does not name them): the installed copy of this course keeps
		 * every resource ID, local path and owner, and its bytes must still be the committed ones. A resource of a
		 * module or stage the course no longer declares is left to the comparison, which reports its removal.
		 */
		private void installedResources() throws IOException {
			if (previous == null) return;
			for (Resource resource : previous.resources()) {
				if ("zip".equals(resource.kind())) continue;
				String owner = resource.lessonId() != null ? resource.lessonId() : resource.stageId();
				if (!documents.containsValue(owner) || !allowed(resource.sourcePath())) continue;
				byte[] bytes = source.read(resource.sourcePath());
				if (bytes == null) {
					findings.add("source resource unavailable " + resource.sourcePath());
					continue;
				}
				checkAccepted(resource.sourcePath(), resource.localPath(), bytes);
				register(new Claim(resource.sourcePath(), owner), new ResourcePlan(resource.resourceId(),
					resource.localPath(), resource.mediaType(), owner, resource.stageId() != null));
			}
		}

		private void references() {
			for (Map.Entry<String, String> document : texts.entrySet()) {
				String id = document.getKey();
				String sourcePath = documentPaths.get(id);
				for (ContentImportDocuments.Reference reference : ContentImportDocuments.references(document.getValue())) {
					resolve(id, sourcePath, reference);
				}
			}
		}

		private void resolve(String id, String documentPath, ContentImportDocuments.Reference reference) {
			String destination = reference.destination();
			if (destination.isEmpty()) {
				findings.add("unsupported destination " + documentPath + " (link without destination)");
				return;
			}
			String where = documentPath + ":" + reference.line() + ": " + destination;
			if (SCHEME.matcher(destination).find()) {
				if (!EXTERNAL.matcher(destination).matches()) findings.add("unsupported destination " + where);
				return;
			}
			if (destination.startsWith("/") || destination.startsWith("\\") || destination.contains("?")) {
				findings.add("unsupported destination " + where);
				return;
			}
			int hash = destination.indexOf('#');
			String path = LessonPipeline.decode(hash < 0 ? destination : destination.substring(0, hash));
			String fragment = hash < 0 ? null : LessonPipeline.decode(destination.substring(hash + 1));
			if (path == null || (hash >= 0 && (fragment == null || fragment.isEmpty()))) {
				findings.add("unsupported destination " + where);
				return;
			}
			String resolved = path.isEmpty() ? documentPath : LessonPipeline.resolvePath(documentPath, path);
			if (resolved == null) {
				findings.add("unsupported destination " + where);
				return;
			}
			if (entryPaths.contains(resolved)) {
				// The index and the challenges are not imported: any fragment resolves to the course entry, never a guessed anchor.
				return;
			}
			if (documents.containsKey(resolved)) {
				if (reference.image()) {
					findings.add("unsupported destination " + where + " (image of a document)");
					return;
				}
				if (fragment == null) return;
				List<String> ids = anchors.get(resolved);
				// A pending document has no body: the loader offers its entry instead of a fragment, as in H4.
				if (ids == null) return;
				if (!ids.contains(fragment)) {
					findings.add("unsupported destination " + where + " (no heading «" + fragment + "» in " + resolved + ")");
					return;
				}
				fragments.computeIfAbsent(resolved, key -> new TreeMap<>()).put(fragment, fragment);
				return;
			}
			String folder = folderAt(resolved);
			if (folder != null) {
				if (reference.image()) findings.add("unsupported destination " + where + " (image of a folder)");
				else if (fragment != null) findings.add("unsupported destination " + where + " (fragment on a folder)");
				else claimFolder(folder, id);
				return;
			}
			if (source.file(resolved)) {
				if (resolved.endsWith(".md")) {
					findings.add("unsupported destination " + where + " (source document without identity)");
					return;
				}
				if (fragment != null) {
					findings.add("unsupported destination " + where + " (fragment on a resource)");
					return;
				}
				if (reference.image() && !ContentImportDocuments.image(resolved)) {
					findings.add("unsupported destination " + where + " (not a PNG, JPEG or SVG image)");
					return;
				}
				claim(resolved, id);
				return;
			}
			findings.add("unsupported destination " + where);
		}

		private String folderAt(String resolved) {
			String folder = resolved.endsWith("/") ? resolved : resolved + "/";
			return source.directory(folder) ? folder : null;
		}

		/**
		 * One source path, one resource per owner: a lesson and a stage that share a file each get their own ID and
		 * local path, because both own their copy. Two owners of the same kind are a content conflict, not a guess.
		 */
		private ResourcePlan claim(String path, String owner) {
			if (!allowed(path)) return null;
			Claim key = new Claim(path, owner);
			ResourcePlan existing = resources.get(key);
			if (existing != null) return existing;
			boolean stage = stageIds.contains(owner);
			for (ResourcePlan other : claimsByPath.getOrDefault(path, List.of())) {
				if (other.stage() != stage) continue;
				findings.add("resource " + path + " is claimed by " + other.owner() + " and " + owner);
				return other;
			}
			if (!source.file(path)) {
				findings.add("source resource unavailable " + path);
				return null;
			}
			String id = owner + "-r-" + HeadingIds.slug(name(path)) + "-" + digest(path);
			ResourcePlan plan = new ResourcePlan(id, directory + "/resources/" + id, mediaType(path), owner, stage);
			checkInstalledResource(path, plan.localPath());
			register(key, plan);
			return plan;
		}

		/**
		 * The media type of a resource. A CSV whose folder declares its encoding in codificacion.txt (FORMATO.md 7.1,
		 * an iconv name such as WINDOWS-1252) is served with that charset; every other text file is UTF-8.
		 */
		private String mediaType(String path) {
			String mediaType = ContentImportDocuments.mediaType(path);
			if (!path.toLowerCase(Locale.ROOT).endsWith(".csv")) return mediaType;
			String folder = path.substring(0, path.lastIndexOf('/') + 1);
			String charset = csvCharsets.computeIfAbsent(folder, this::declaredCharset);
			return charset.isEmpty() ? mediaType : "text/plain; charset=" + charset;
		}

		/** The charset codificacion.txt names, in lower case; empty when there is none or it is not usable. */
		private String declaredCharset(String folder) {
			String declaration = folder + "codificacion.txt";
			byte[] bytes;
			try {
				bytes = source.file(declaration) ? source.read(declaration) : null;
			}
			catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			}
			if (bytes == null) return "";
			String name = new String(bytes, StandardCharsets.UTF_8).strip();
			try {
				if (name.isEmpty() || name.contains("\n")) throw new IllegalCharsetNameException(name);
				return Charset.forName(name).name().toLowerCase(Locale.ROOT);
			}
			catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
				findings.add(declaration + ": unknown encoding «" + name + "»");
				return "";
			}
		}

		/** One claim enters both orders: by (path, owner) for identity and by path for the folder entries. */
		private void register(Claim key, ResourcePlan plan) {
			resources.put(key, plan);
			claimsByPath.computeIfAbsent(key.path(), path -> new ArrayList<>()).add(plan);
		}

		/**
		 * A linked folder is copied recursively, including dotfiles. Every owner that claims it keeps its own files, the
		 * folder entry lists every file claimed beneath it, and the sorted-first owner ID owns its single archive.
		 */
		private void claimFolder(String folder, String owner) {
			// A registered folder keeps its trailing slash; its plain path is what containment rules check.
			String plain = folder.substring(0, folder.length() - 1);
			if (!allowed(plain)) return;
			FolderPlan plan = folders.computeIfAbsent(folder, FolderPlan::new);
			if (plan.files.containsKey(owner)) return;
			if (!plan.owners.isEmpty() && plan.owners.stream()
				.noneMatch(other -> stageIds.contains(other) != stageIds.contains(owner))) {
				findings.add("folder " + folder + " is claimed by " + plan.owner() + " and " + owner);
				return;
			}
			List<String> members = new ArrayList<>();
			for (String entry : source.entries(folder)) {
				if (source.file(entry)) members.add(entry);
				else findings.add("unsupported member " + entry + " (symbolic links and submodules are not copied)");
			}
			if (members.isEmpty()) {
				findings.add("folder " + folder + " has no file to copy");
				return;
			}
			List<String> ids = new ArrayList<>();
			for (String member : members) {
				ResourcePlan file = claim(member, owner);
				if (file != null) ids.add(file.id());
			}
			if (ids.isEmpty()) return;
			plan.owners.add(owner);
			plan.files.put(owner, List.copyOf(ids));
		}

		/** One claimed folder: its owners in ID order, each owner's ordered files, and the archive of the first. */
		private static final class FolderPlan {

			private final String sourcePath;
			private final Set<String> owners = new TreeSet<>();
			private final Map<String, List<String>> files = new TreeMap<>();

			FolderPlan(String sourcePath) {
				this.sourcePath = sourcePath;
			}

			String sourcePath() {
				return sourcePath;
			}

			String owner() {
				return owners.iterator().next();
			}

			String zipId() {
				String plain = sourcePath.substring(0, sourcePath.length() - 1);
				return owner() + "-zip-" + HeadingIds.slug(name(plain)) + "-" + digest(sourcePath);
			}

			/** The archive of the sorted-first owner holds that owner's files: the same paths, one copy each. */
			List<String> archiveMembers() {
				return List.copyOf(files.get(owner()));
			}
		}

		private Manifest manifest(List<Lesson> lessons, List<Stage> stages) {
			Map<String, List<String>> members = folderMembers(claimsByPath, folders.keySet(), ResourcePlan::id);
			List<Resource> resourceEntries = new ArrayList<>();
			for (Map.Entry<Claim, ResourcePlan> entry : resources.entrySet()) {
				ResourcePlan plan = entry.getValue();
				resourceEntries.add(new Resource(plan.id(), plan.stage() ? null : plan.owner(), entry.getKey().path(),
					plan.localPath(), plan.mediaType(), plan.stage() ? plan.owner() : null, FILE, null, null));
			}
			for (FolderPlan folder : folders.values()) {
				if (folder.owners.isEmpty()) continue;
				String plain = folder.sourcePath().substring(0, folder.sourcePath().length() - 1);
				String owner = folder.owner();
				boolean stage = stageIds.contains(owner);
				resourceEntries.add(new Resource(folder.zipId(), stage ? null : owner, folder.sourcePath(),
					directory + "/resources/" + folder.zipId() + ".zip", "application/zip", stage ? owner : null, ZIP,
					name(plain) + ".zip", folder.archiveMembers()));
			}
			resourceEntries.sort(Comparator.comparing(Resource::sourcePath).thenComparing(Resource::resourceId));
			List<SourceLink> links = new ArrayList<>();
			for (String entry : entryPaths) links.add(new SourceLink(entry, null, null, null, null, directory, null, null));
			for (Lesson lesson : lessons) {
				links.add(new SourceLink(lesson.sourcePath(), lesson.id(),
					fragments.getOrDefault(lesson.sourcePath(), Map.of()), null, null, null, null, null));
			}
			for (Stage stage : stages) {
				links.add(new SourceLink(stage.sourcePath(), null,
					fragments.getOrDefault(stage.sourcePath(), Map.of()), null, null, null, stage.id(), null));
			}
			// One mapping per source path: the sorted-first owner answers links to a path two owners share.
			String previous = null;
			for (Map.Entry<Claim, ResourcePlan> entry : resources.entrySet()) {
				if (entry.getKey().path().equals(previous)) continue;
				previous = entry.getKey().path();
				links.add(new SourceLink(previous, null, null, entry.getValue().id(), null, null, null, null));
			}
			for (FolderPlan folder : folders.values()) {
				if (folder.owners.isEmpty()) continue;
				links.add(new SourceLink(folder.sourcePath(), null, null, null, members.get(folder.sourcePath()), null, null,
					folder.zipId()));
			}
			links.sort(Comparator.comparing(SourceLink::sourcePath));
			List<Manifest.Phase> phases = new ArrayList<>();
			for (CourseDescriptor.Phase phase : descriptor.phases()) {
				phases.add(new Manifest.Phase(phase.id(), phase.title(), phases.size() + 1));
			}
			return new Manifest(2, new Manifest.Source(descriptor.title(), source.commit()),
				new Manifest.Course(descriptor.id(), descriptor.id(), descriptor.title()),
				List.copyOf(phases), List.copyOf(lessons), List.copyOf(resourceEntries), List.copyOf(links),
				List.copyOf(stages), new Manifest.Provenance(List.of(), List.of()));
		}

		/** Exercises, links and fragments are validated before anything is written, with the runtime pipeline. */
		private void validate(Manifest manifest) {
			Map<String, String> names = new TreeMap<>();
			for (Resource resource : manifest.resources()) {
				names.put(resource.resourceId(), ZIP.equals(resource.kind()) ? resource.fileName()
					: resource.sourcePath().substring(resource.sourcePath().lastIndexOf('/') + 1));
			}
			LessonPipeline pipeline = new LessonPipeline(manifest, names);
			for (Lesson lesson : manifest.lessons()) {
				if (lesson.available()) pipeline.parse(lesson, texts.get(lesson.id()));
			}
			for (Stage stage : manifest.projectStages()) {
				if (stage.available()) pipeline.parse(stage, texts.get(stage.id()));
			}
			pipeline.render();
			findings.addAll(pipeline.problems());
			reported.addAll(pipeline.warnings());
		}

		private List<Copy> copies(Manifest manifest) {
			List<Copy> copies = new ArrayList<>();
			for (Lesson lesson : manifest.lessons()) {
				if (lesson.available()) copies.add(new Copy(lesson.sourcePath(), lesson.bodyPath()));
			}
			for (Stage stage : manifest.projectStages()) {
				if (stage.available()) copies.add(new Copy(stage.sourcePath(), stage.bodyPath()));
			}
			for (Resource resource : manifest.resources()) {
				if (!ZIP.equals(resource.kind())) copies.add(new Copy(resource.sourcePath(), resource.localPath()));
			}
			copies.sort(Comparator.comparing(Copy::localPath));
			return List.copyOf(copies);
		}

		/** An installed copy of a resource must still hold the committed bytes; a local edit is reported. */
		private void checkInstalledResource(String path, String localPath) {
			if (previous == null || !Files.isRegularFile(acceptedRoot.resolve(localPath))) return;
			try {
				byte[] bytes = source.read(path);
				if (bytes != null) checkAccepted(path, localPath, bytes);
			}
			catch (IOException ex) {
				throw new java.io.UncheckedIOException(ex);
			}
		}

		/** A local edit is an adaptation: it is reported, never replaced by the committed bytes. */
		private void checkAccepted(String sourcePath, String localPath, byte[] committed) throws IOException {
			// The committed hash is remembered: the copy phase registers the same file without hashing it again.
			String hash = hashes.computeIfAbsent(sourcePath, path -> ContentUpdateComparison.sha(committed));
			if (localPath == null) return;
			Path copy = acceptedRoot.resolve(localPath);
			if (!Files.isRegularFile(copy)) return;
			if (!ContentUpdateComparison.sha(Files.readAllBytes(copy)).equals(hash)) {
				findings.add("local adaptation would be replaced " + localPath);
			}
		}

		/**
		 * Plain relative paths only. A .git, .env or .env.* segment is never read or serialized: policy excludes it from
		 * the candidate and reports its source path, so the affected document ships without that file and none is
		 * substituted for it.
		 */
		private boolean allowed(String path) {
			if (!ContentPaths.isPlainRelative(path)) {
				findings.add("invalid source path " + path);
				return false;
			}
			try {
				ContentUpdateComparison.safeNames(path);
			}
			catch (IllegalArgumentException ex) {
				reported.add("protected source path excluded by policy: " + path);
				return false;
			}
			return true;
		}

		/** Strict UTF-8: a source body that cannot be decoded is reported, never silently replaced. */
		private String text(String path, byte[] bytes) {
			try {
				return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
			}
			catch (CharacterCodingException ex) {
				findings.add("source document is not valid UTF-8: " + path);
				return null;
			}
		}

		private static String name(String path) {
			return path.substring(path.lastIndexOf('/') + 1);
		}

		/** Eight hex digits of the source path: a stable, collision-resistant suffix for generated IDs. */
		private static String digest(String path) {
			return ContentUpdateComparison.sha(path.getBytes(StandardCharsets.UTF_8)).substring(0, 8);
		}
	}
}
