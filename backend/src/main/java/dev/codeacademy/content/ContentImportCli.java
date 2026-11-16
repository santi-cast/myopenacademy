package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Standalone offline generator entry point. Deliberately never starts Spring, installs a copy or opens a database. */
public final class ContentImportCli {

	static final String HELP = "Usage: content-import generate --active DIR --source GIT (--tag vX.Y.Z | --commit SHA)"
		+ " --output DIR --report FILE\n"
		+ "Generate a new candidate copy offline; never applies it and never writes a database.\n"
		+ "--tag installs a published version: the tag is resolved to its commit and both are recorded.\n";

	private ContentImportCli() {
	}

	public static void main(String[] args) {
		int result;
		try {
			result = execute(args);
		}
		catch (ContentValidationException ex) {
			System.err.println("content-import: invalid content: " + String.join("; ", ex.problems()));
			result = 2;
		}
		catch (IOException | IllegalArgumentException ex) {
			System.err.println("content-import: " + ex.getMessage());
			result = 2;
		}
		System.exit(result);
	}

	private static int execute(String[] args) throws IOException {
		if (args.length == 1 && args[0].equals("--help")) {
			System.out.print(HELP);
			return 0;
		}
		if (args.length != 11 || !args[0].equals("generate")) throw invalid();
		Map<String, String> options = new HashMap<>();
		for (int i = 1; i < args.length; i += 2) {
			if (!List.of("--active", "--source", "--commit", "--tag", "--output", "--report").contains(args[i])
				|| args[i + 1].isBlank() || options.putIfAbsent(args[i], args[i + 1]) != null) throw invalid();
		}
		// Exactly one of --commit and --tag: five options in all, each once.
		if (options.containsKey("--commit") == options.containsKey("--tag")) throw invalid();
		Path source = ContentUpdateCli.checked(Path.of(options.get("--source")));
		String tag = options.get("--tag");
		String commit = options.get("--commit");
		if (tag != null) {
			if (!Files.isDirectory(source)) throw new IllegalArgumentException("directories must exist");
			commit = ContentImportSource.resolveTag(source, tag);
		}
		Paths paths = new Paths(ContentUpdateCli.checked(Path.of(options.get("--active"))), source, commit, tag,
			ContentUpdateCli.checked(Path.of(options.get("--output"))),
			ContentUpdateCli.checked(Path.of(options.get("--report"))));
		List<Path> distinct = List.of(paths.active(), paths.source(), paths.output(), paths.report());
		for (int i = 0; i < distinct.size(); i++) for (int j = i + 1; j < distinct.size(); j++) {
			if (ContentUpdateCli.overlaps(distinct.get(i), distinct.get(j))) throw new IllegalArgumentException("paths must not overlap");
		}
		if (!Files.isDirectory(paths.active()) || !Files.isDirectory(paths.source())
			|| !Files.isDirectory(paths.report().getParent()) || !Files.isDirectory(paths.output().getParent())) {
			throw new IllegalArgumentException("directories must exist");
		}
		if (Files.exists(paths.output())) throw new IllegalArgumentException("output must not exist");
		ContentImportGenerator.Result result = ContentImportGenerator.generate(paths);
		// CREATE_NEW keeps the report as evidence of this run and never replaces an unrelated one.
		Files.writeString(paths.report(), result.report(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		if (!result.findings().isEmpty()) {
			System.out.println("Candidate not written; findings: " + result.findings().size());
			return 3;
		}
		String line = "Candidate written; lessons: " + result.lessons() + "; stages: " + result.stages()
			+ "; resources: " + result.resources();
		// Reported findings never block: inert formatting the sanitizer removed, always with its path.
		if (!result.reported().isEmpty()) line += "; reported: " + result.reported().size();
		System.out.println(line);
		return 0;
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("invalid arguments; use --help");
	}

	/** {@code commit} is always the full commit installed; {@code tag} is the version tag it came from, or null. */
	record Paths(Path active, Path source, String commit, String tag, Path output, Path report) {
		Paths(Path active, Path source, String commit, Path output, Path report) {
			this(active, source, commit, null, output, report);
		}
	}
}
