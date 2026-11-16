package dev.codeacademy.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.codeacademy.content.ExampleContent;

/** The catalog follows the same order as «Mis cursos»: the first course, then by title in Spanish, then by ID. */
class CourseOrderTest {

	record Course(String id, String title) {
	}

	@Test
	void theFirstCourseThenTitlesAsSpanishSortsThemThenIds() {
		List<Course> courses = List.of(new Course("b-igual", "Mismo título"), new Course("java-web", "Álgebra y Java"),
			new Course("inicial", "Zeta: el curso base"), new Course("a-igual", "Mismo título"), new Course("javascript", "Ábaco"));
		assertThat(courses.stream().sorted(CourseOrder.of("inicial", Course::id, Course::title)).map(Course::id))
			.containsExactly("inicial", "javascript", "java-web", "a-igual", "b-igual");
		// Without the first course installed, only titles and IDs count.
		assertThat(courses.stream().sorted(CourseOrder.of("", Course::id, Course::title)).map(Course::id).toList().getFirst())
			.isEqualTo("javascript");
	}

	@Test
	void theCatalogListsTheConfiguredFirstCourseAndThenTheOthersByTitle() {
		Catalog catalog = Catalog.load(ExampleContent.root());
		// By title, «Curso de destino de ejemplo» goes before «Curso de ejemplo».
		assertThat(new CatalogController(catalog, "inicial").courses().courses()).extracting(CatalogController.CourseView::id)
			.containsExactly("ejemplo-destino", "ejemplo");
		assertThat(new CatalogController(catalog, "ejemplo").courses().courses()).extracting(CatalogController.CourseView::id)
			.containsExactly("ejemplo", "ejemplo-destino");
	}
}
