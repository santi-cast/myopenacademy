package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Canonical containment of manifest paths: plain relative paths, no symlink on any component, existing regular files. */
final class ContentPaths {

	private ContentPaths() {
	}

	/** The checked file, or null after adding a problem that names the path as written in the manifest. */
	static Path file(Path root, String relative, String what, List<String> problems) {
		if (relative == null || relative.isBlank()) {
			problems.add(what + " is missing");
			return null;
		}
		if (!isPlainRelative(relative)) {
			problems.add(what + " " + relative + " must be a plain relative path inside the content root");
			return null;
		}
		Path current = root;
		for (String segment : relative.split("/")) {
			current = current.resolve(segment);
			if (Files.isSymbolicLink(current)) {
				problems.add(what + " " + relative + " goes through a symbolic link");
				return null;
			}
		}
		if (!Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
			problems.add(what + " " + relative + " does not exist");
			return null;
		}
		try {
			Path real = current.toRealPath();
			if (!real.startsWith(root)) {
				problems.add(what + " " + relative + " escapes the content root");
				return null;
			}
			return real;
		}
		catch (IOException ex) {
			problems.add(what + " " + relative + " cannot be read");
			return null;
		}
	}

	private static boolean isPlainRelative(String path) {
		if (path.startsWith("/") || path.contains("\\") || path.contains(":") || Path.of(path).isAbsolute()) return false;
		for (String segment : path.split("/", -1)) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
		}
		return true;
	}
}
