package dev.codeacademy.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The version of this academy and the comparison of curso.json's academia_minima with it (H12, T15; FORMATO.md 3.2
 * and 11.1). The only source is the {@code <version>} of backend/pom.xml: Maven filters it into
 * {@code myopenacademy.version} of application.properties, which the importer reads from its own classpath.
 */
final class AcademyVersion {

	/** MAJOR.MINOR.PATCH: ASCII digits with no leading zeros and nothing else (SEMVER of the kit). */
	static final Pattern SEMVER = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");
	private static final String PROPERTY = "myopenacademy.version";

	private AcademyVersion() {
	}

	static String current() {
		Properties properties = new Properties();
		try (InputStream input = AcademyVersion.class.getResourceAsStream("/application.properties")) {
			if (input == null) throw new IllegalStateException("application.properties is not on the classpath");
			properties.load(input);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		String version = properties.getProperty(PROPERTY);
		if (version == null || !SEMVER.matcher(version).matches()) {
			throw new IllegalStateException(PROPERTY + " is not a filtered MAJOR.MINOR.PATCH: " + version);
		}
		return version;
	}

	/**
	 * Compares two MAJOR.MINOR.PATCH versions number by number, each as an integer of any size: without leading
	 * zeros, the longer number is the larger one, and two of the same length compare as text.
	 */
	static int compare(String a, String b) {
		String[] left = a.split("\\.");
		String[] right = b.split("\\.");
		for (int i = 0; i < 3; i++) {
			int order = left[i].length() != right[i].length()
				? Integer.compare(left[i].length(), right[i].length()) : left[i].compareTo(right[i]);
			if (order != 0) return order;
		}
		return 0;
	}
}
