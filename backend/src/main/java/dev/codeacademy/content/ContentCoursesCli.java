package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The list of installed courses (H7, T7): content/cursos.json, kept in Git, says which course is installed, from which
 * repository, at which version tag and commit. {@code list} writes it from an installed copy; {@code reinstall}
 * installs exactly that list again into a new copy, from local clones of the course repositories. Offline, like
 * content-import: it never starts Spring, applies a copy or opens a database.
 */
public final class ContentCoursesCli {

	static final String HELP = "Usage: content-courses list --active DIR --output FILE\n"
		+ "       content-courses reinstall --list FILE --sources DIR --output DIR --report FILE\n"
		+ "list writes the installed courses (repository, version, tag, commit) of a copy.\n"
		+ "reinstall installs exactly that list into a new copy; each course comes from DIR/<repository name>.\n";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private ContentCoursesCli() {
	}

	/** One installed course, as the list records it; {@code tag} is null when it was installed from a commit. */
	record Entry(String id, String repository, String version, String tag, String commit) {
	}

	public static void main(String[] args) {
		int result;
		try {
			result = execute(args);
		}
		catch (ContentValidationException ex) {
			System.err.println("content-courses: invalid content: " + String.join("; ", ex.problems()));
			result = 2;
		}
		catch (IOException | IllegalArgumentException ex) {
			System.err.println("content-courses: " + ex.getMessage());
			result = 2;
		}
		System.exit(result);
	}

	static int execute(String[] args) throws IOException {
		if (args.length == 1 && args[0].equals("--help")) {
			System.out.print(HELP);
			return 0;
		}
		if (args.length == 5 && args[0].equals("list")) {
			Map<String, String> options = options(args, List.of("--active", "--output"));
			Path active = ContentUpdateCli.checked(Path.of(options.get("--active")));
			Path output = ContentUpdateCli.checked(Path.of(options.get("--output")));
			if (!Files.isDirectory(active) || !Files.isDirectory(output.getParent())) {
				throw new IllegalArgumentException("directories must exist");
			}
			List<Entry> entries = entries(active);
			// The list is a file kept in Git and updated with every installation: replaced whole, never half written.
			Path partial = Files.createTempFile(output.getParent(), ".cursos-", ".json");
			Files.writeString(partial, write(entries));
			Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			System.out.println("List written; courses: " + entries.size());
			return 0;
		}
		if (args.length == 9 && args[0].equals("reinstall")) {
			Map<String, String> options = options(args, List.of("--list", "--sources", "--output", "--report"));
			Path list = ContentUpdateCli.checked(Path.of(options.get("--list")));
			// The course repositories are only read, like --source of content-import: relative and with .. (T5).
			Path sources = ContentUpdateCli.checkedSource(Path.of(options.get("--sources")));
			Path output = ContentUpdateCli.checked(Path.of(options.get("--output")));
			Path report = ContentUpdateCli.checked(Path.of(options.get("--report")));
			if (!Files.isRegularFile(list) || !Files.isDirectory(sources) || !Files.isDirectory(output.getParent())
				|| !Files.isDirectory(report.getParent())) throw new IllegalArgumentException("directories must exist");
			if (Files.exists(output)) throw new IllegalArgumentException("output must not exist");
			if (ContentUpdateCli.overlaps(output, sources) || ContentUpdateCli.overlaps(report, sources)
				|| ContentUpdateCli.overlaps(report, output)) throw new IllegalArgumentException("paths must not overlap");
			return reinstall(read(list), sources, output, report);
		}
		throw invalid();
	}

	/** The courses of an installed copy, by ID. */
	static List<Entry> entries(Path active) throws IOException {
		List<Entry> entries = new ArrayList<>();
		for (CourseContent course : ContentLoader.loadInstalled(active)) {
			Manifest.Source source = course.manifest().source();
			entries.add(new Entry(course.manifest().course().id(), source.repository(), source.courseVersion(),
				source.versionTag(), source.versionRef()));
		}
		entries.sort(Comparator.comparing(Entry::id));
		return entries;
	}

	static String write(List<Entry> entries) {
		ObjectNode data = JSON.createObjectNode();
		data.put("format", 1);
		ArrayNode courses = data.putArray("courses");
		for (Entry entry : entries) {
			ObjectNode course = courses.addObject();
			course.put("id", entry.id());
			course.put("repository", entry.repository());
			course.put("version", entry.version());
			course.put("tag", entry.tag());
			course.put("commit", entry.commit());
		}
		return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n";
	}

	static List<Entry> read(Path list) throws IOException {
		JsonNode data = JSON.readTree(list.toFile());
		if (data.path("format").asInt(0) != 1 || !data.path("courses").isArray()) {
			throw new IllegalArgumentException("the list must be format 1 with a courses array");
		}
		List<Entry> entries = new ArrayList<>();
		for (JsonNode course : data.path("courses")) {
			Entry entry = new Entry(text(course, "id"), text(course, "repository"), text(course, "version"),
				text(course, "tag"), text(course, "commit"));
			if (entry.id() == null || entry.repository() == null || entry.commit() == null) {
				throw new IllegalArgumentException("every listed course needs id, repository and commit");
			}
			entries.add(entry);
		}
		return entries;
	}

	/**
	 * Installs the listed courses one on top of the other, each from its tag (or its commit), into a new copy. A tag
	 * that no longer names the recorded commit stops everything: the list is what was installed, not what the tag says
	 * today. Nothing is written to {@code output} unless every course installs.
	 */
	static int reinstall(List<Entry> entries, Path sources, Path output, Path report) throws IOException {
		Path work = Files.createTempDirectory(output.getParent(), ".reinstall-");
		ObjectNode data = JSON.createObjectNode();
		data.put("format", 1);
		ArrayNode courses = data.putArray("courses");
		int code = 0;
		try {
			Path active = Files.createDirectory(work.resolve("vacio"));
			for (int i = 0; i < entries.size() && code == 0; i++) {
				Entry entry = entries.get(i);
				ObjectNode course = courses.addObject().put("id", entry.id());
				Path repository = sources.resolve(name(entry.repository()));
				course.put("source", repository.toString());
				if (!Files.isDirectory(repository)) {
					course.putArray("findings").add("repository not found " + repository);
					code = 3;
					break;
				}
				if (entry.tag() != null) {
					String commit;
					try {
						commit = ContentImportSource.resolveTag(repository, entry.tag());
					}
					catch (IllegalArgumentException ex) {
						course.putArray("findings").add(ex.getMessage());
						code = 3;
						break;
					}
					if (!commit.equals(entry.commit())) {
						course.putArray("findings").add("tag " + entry.tag() + " now names " + commit + ", the list says "
							+ entry.commit());
						code = 3;
						break;
					}
				}
				Path next = work.resolve("copia-" + (i + 1));
				ContentImportGenerator.Result result = ContentImportGenerator.generate(new ContentImportCli.Paths(active,
					repository, entry.commit(), entry.tag(), next, work.resolve("informe-" + (i + 1) + ".json")));
				course.set("generation", JSON.readTree(result.report()));
				if (!result.written()) {
					code = 3;
					break;
				}
				active = next;
			}
			if (code == 0) {
				// The copy has to be exactly the list: the same courses, versions, tags and commits.
				List<Entry> installed = entries(active);
				List<Entry> expected = new ArrayList<>(entries);
				expected.sort(Comparator.comparing(Entry::id));
				if (!installed.equals(expected)) throw new IllegalArgumentException("the reinstalled copy differs from the list");
				Files.move(active, output);
			}
		}
		finally {
			delete(work);
		}
		data.put("written", code == 0);
		Files.writeString(report, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data) + "\n",
			java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
		System.out.println(code == 0 ? "Copy written; courses: " + entries.size() : "Copy not written; see the report");
		return code;
	}

	/** The folder of a course repository: the last segment of its address, without .git (curso-inicial). */
	static String name(String repository) {
		String trimmed = repository.replaceAll("/+$", "").replaceAll("\\.git$", "");
		String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
		if (name.isEmpty() || name.equals(".") || name.equals("..") || name.startsWith(".env") || name.equals(".git")) {
			throw new IllegalArgumentException("invalid repository " + repository);
		}
		return name;
	}

	private static Map<String, String> options(String[] args, List<String> allowed) {
		Map<String, String> options = new HashMap<>();
		for (int i = 1; i < args.length; i += 2) {
			if (!allowed.contains(args[i]) || args[i + 1].isBlank() || options.putIfAbsent(args[i], args[i + 1]) != null) {
				throw invalid();
			}
		}
		return options;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value == null || !value.isString() || value.asString().isBlank() ? null : value.asString();
	}

	private static void delete(Path root) throws IOException {
		if (!Files.exists(root)) return;
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("invalid arguments; use --help");
	}
}
