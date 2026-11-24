package dev.codeacademy.content;

import java.util.List;
import java.util.function.Predicate;

/**
 * The Maven or Gradle project a linked code folder belongs to (H12, T11; FORMATO.md 7, revision 1.1): when the
 * destination of a «📁 Código» link is inside one, its ZIP is the ZIP of the whole project. The project is the outermost
 * one inside codigo/: the folder of a pom.xml with no other pom.xml above it, or the folder with settings.gradle or
 * settings.gradle.kts. It never goes above codigo/ (the nearest folder named codigo, as the kit's checker walks), and
 * codigo/ itself never counts; a folder outside codigo/ belongs to no project. A build.gradle without settings above
 * it is not a project for this rule. Pure path logic: the caller says which files exist (the Git tree of the pinned
 * commit for the importer, the registered resources for the loader).
 */
final class CodeProjects {

	static final List<String> BUILD_FILES = List.of("pom.xml", "settings.gradle", "settings.gradle.kts");
	private static final String CODE = "codigo/";

	private CodeProjects() {
	}

	/**
	 * The outermost project folder that holds {@code folder}, with its trailing slash, or null when it is in none.
	 * {@code folder} ends in '/'; it may be the project folder itself. Walking down from codigo/, the first folder with a
	 * build file is the outermost, so nested projects and the modules of a multi-module build all answer the root.
	 */
	static String root(String folder, Predicate<String> file) {
		int code = -1;
		for (int at = folder.indexOf(CODE); at >= 0; at = folder.indexOf(CODE, at + 1)) {
			if (at == 0 || folder.charAt(at - 1) == '/') code = at;
		}
		if (code < 0) return null;
		for (int slash = folder.indexOf('/', code + CODE.length()); slash >= 0; slash = folder.indexOf('/', slash + 1)) {
			String candidate = folder.substring(0, slash + 1);
			for (String name : BUILD_FILES) {
				if (file.test(candidate + name)) return candidate;
			}
		}
		return null;
	}
}
