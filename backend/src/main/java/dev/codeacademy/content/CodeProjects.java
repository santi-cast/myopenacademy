package dev.codeacademy.content;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The Maven or Gradle project a linked code folder belongs to (H12, T11 and T17; FORMATO.md 7, kit 1.1.0): when the
 * destination of a «📁 Código» link is inside one, its ZIP is the ZIP of the whole project. The project is the outermost
 * folder on the path, inside codigo/, with a pom.xml or a settings.gradle(.kts) or, when no settings.gradle(.kts) is on
 * the path, with a build.gradle(.kts) (Gradle builds one without settings as a single-module project). Only the codigo/
 * that hangs directly from a phase (fase-NN-*) or from proyecto/ counts; a folder named codigo further down is just a
 * name. It never goes above that codigo/, codigo/ itself never counts, and a folder outside it belongs to no project.
 * The same as raiz_del_proyecto of the kit. Pure path logic: the caller says which files exist (the Git tree of the
 * pinned commit for the importer, the registered resources for the loader).
 */
final class CodeProjects {

	static final List<String> PROJECT_FILES = List.of("pom.xml", "settings.gradle", "settings.gradle.kts");
	static final List<String> SETTINGS = List.of("settings.gradle", "settings.gradle.kts");
	static final List<String> BUILD_GRADLE = List.of("build.gradle", "build.gradle.kts");
	/** The codigo/ of FORMATO.md: es_carpeta_de_codigo of the kit, at the course root. */
	private static final Pattern CODE = Pattern.compile("(proyecto|fase-[0-9]{2}(-[^/]*)?)/codigo/");

	private CodeProjects() {
	}

	/**
	 * The outermost project folder that holds {@code folder}, with its trailing slash, or null when it is in none.
	 * {@code folder} ends in '/' and is relative to the course root; it may be the project folder itself.
	 */
	static String root(String folder, Predicate<String> file) {
		var code = CODE.matcher(folder);
		if (!code.lookingAt()) return null;
		boolean settings = false;
		for (int slash = folder.indexOf('/', code.end()); slash >= 0; slash = folder.indexOf('/', slash + 1)) {
			String candidate = folder.substring(0, slash + 1);
			if (SETTINGS.stream().anyMatch(name -> file.test(candidate + name))) settings = true;
		}
		// Walking down from codigo/, the first folder that counts is the outermost.
		for (int slash = folder.indexOf('/', code.end()); slash >= 0; slash = folder.indexOf('/', slash + 1)) {
			String candidate = folder.substring(0, slash + 1);
			if (PROJECT_FILES.stream().anyMatch(name -> file.test(candidate + name))) return candidate;
			if (!settings && BUILD_GRADLE.stream().anyMatch(name -> file.test(candidate + name))) return candidate;
		}
		return null;
	}
}
