package dev.codeacademy.catalog;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;

/**
 * The order of the courses, the same in the catalog and in «Mis cursos» (author's decisions, 16 November 2026): first
 * the configured first course ({@code code-academy.first-course}, the base course; configuration, never a course written
 * in the code), then the others by title as Spanish sorts it («Ábaco» before «Álgebra») and, when two titles are equal,
 * by ID. Sorted in Java, so it never depends on the database collation.
 */
public final class CourseOrder {

	private CourseOrder() {
	}

	public static <T> Comparator<T> of(String firstCourse, Function<T, String> id, Function<T, String> title) {
		Collator spanish = Collator.getInstance(Locale.forLanguageTag("es-ES"));
		return Comparator.comparing((T course) -> !id.apply(course).equals(firstCourse))
			.thenComparing(title, spanish)
			.thenComparing(id);
	}
}
