package dev.codeacademy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.ArrayNode;

import dev.codeacademy.catalog.CatalogConflictException;
import dev.codeacademy.catalog.CatalogSynchronizer;
import dev.codeacademy.content.ContentFixture;
import dev.codeacademy.content.ContentLoader;
import dev.codeacademy.content.Manifest;
import dev.codeacademy.content.Manifest.Lesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Startup synchronization of the catalog tables, run directly: without a web server the application does not load
 * content by itself (S15), so this context starts with an empty catalog of its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CatalogSynchronizationTest {

	@Autowired CatalogSynchronizer synchronizer;
	@Autowired JdbcTemplate jdbc;

	@TempDir
	Path tempDir;

	Manifest java;
	ContentFixture demo;

	@BeforeEach
	void synchronizedBase() {
		java = ContentLoader.load(CatalogApiTest.CONTENT, "java/manifest.json").manifest();
		demo = ContentFixture.copyInto(tempDir);
		synchronizer.synchronize(List.of(java, demo.load().manifest()));
	}

	@Test
	void insertsEveryCourseLessonAndExerciseAndASecondRunChangesNothing() {
		assertThat(jdbc.queryForList("select id from code_academy.courses order by id", String.class))
				.containsExactly("demo", "java");
		assertThat(count("lessons where course_id = 'java'")).isEqualTo(199);
		assertThat(count("lessons where course_id = 'demo'")).isEqualTo(3);
		assertThat(count("exercises where course_id = 'java' and lesson_id = 'java-02.01'")).isEqualTo(9);
		assertThat(jdbc.queryForList("select id, lesson_id, title, position from code_academy.exercises "
				+ "where course_id = 'demo' order by id")).containsExactly(
						Map.of("id", "demo-01.01-e01", "lesson_id", "demo-01.01", "title", "Primer ejercicio",
								"position", 1),
						Map.of("id", "demo-01.01-e02", "lesson_id", "demo-01.01", "title", "Segundo `ejercicio`",
								"position", 2));
		assertThat(jdbc.queryForMap("select course_id, title, position, available, source_available "
				+ "from code_academy.lessons where id = 'demo-02.01'")).isEqualTo(Map.of("course_id", "demo",
						"title", "Pendiente", "position", 3, "available", false, "source_available", false));
		var before = TestDatabase.snapshot(jdbc);

		synchronizer.synchronize(List.of(java, demo.load().manifest()));

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void updatesTitlesPositionsAndAvailabilityKeepingIds() {
		demo.editManifest(manifest -> {
			manifest.withObject("course").put("title", "Demo renovado");
			ContentFixture.lesson(manifest, "demo-01.01").put("position", 2).put("title", "Introducción nueva");
			ContentFixture.lesson(manifest, "demo-01.02").put("position", 1).put("available", false)
					.putNull("bodyPath");
		});

		synchronizer.synchronize(List.of(java, demo.load().manifest()));

		assertThat(jdbc.queryForObject("select title from code_academy.courses where id = 'demo'", String.class))
				.isEqualTo("Demo renovado");
		assertThat(jdbc.queryForList("select id, title, position, available from code_academy.lessons "
				+ "where course_id = 'demo' order by position")).containsExactly(
						Map.of("id", "demo-01.02", "title", "Siguiente", "position", 1, "available", false),
						Map.of("id", "demo-01.01", "title", "Introducción nueva", "position", 2, "available", true),
						Map.of("id", "demo-02.01", "title", "Pendiente", "position", 3, "available", false));
	}

	@Test
	void aLessonRemovedFromTheManifestIsRejectedWithoutChanges() {
		demo.editManifest(manifest -> {
			removeWhere((ArrayNode) manifest.get("lessons"), "id", "demo-02.01");
			removeWhere((ArrayNode) manifest.get("sourceToId"), "sourcePath", "fase-02/01-pendiente.md");
			manifest.withObject("course").put("title", "Cambio que no debe guardarse");
		});
		demo.replace(ContentFixture.INTRO, "[02.01 Pendiente](../fase-02/01-pendiente.md#algo-que-no-existe)",
				"02.01 Pendiente");
		Manifest withoutLesson = demo.load().manifest();
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, withoutLesson)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("lesson demo-02.01 is no longer in the manifest");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aLessonMovedToAnotherCourseIsRejectedWithoutChanges() {
		demo.editManifest(manifest -> {
			manifest.withObject("course").put("id", "demo-nuevo");
			ContentFixture.link(manifest, "00-indice.md").put("courseId", "demo-nuevo");
		});
		Manifest moved = demo.load().manifest();
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, moved)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("lesson demo-01.01 belongs to course demo, not demo-nuevo")
				.hasMessageContaining("course demo is no longer in the content");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void anExerciseRemovedOrMovedToAnotherLessonIsRejectedWithoutChanges() {
		Manifest base = demo.load().manifest();
		Lesson intro = base.lessons().get(0);
		Lesson next = base.lessons().get(1);
		var second = intro.exercises().get(1);
		Manifest removed = withLessons(base, lesson -> lesson.id().equals(intro.id())
				? withExercises(lesson, List.of(intro.exercises().get(0))) : lesson);
		Manifest moved = withLessons(base, lesson -> {
			if (lesson.id().equals(intro.id())) return withExercises(lesson, List.of(intro.exercises().get(0)));
			if (lesson.id().equals(next.id())) {
				return withExercises(lesson, List.of(new Manifest.Exercise(second.id(), next.id(), second.title())));
			}
			return lesson;
		});
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, removed)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("exercise demo-01.01-e02 is no longer in the manifest");
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, moved)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("exercise demo-01.01-e02 belongs to lesson demo-01.01 of course demo, "
						+ "not demo-01.02 of course demo");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aFailureDuringTheWritesLeavesNoPartialChange() {
		Manifest base = demo.load().manifest();
		// Two lessons on one position only fail when the transaction commits, after every other write.
		Manifest clash = withLessons(base, lesson -> lesson.id().equals("demo-02.01")
				? new Lesson(lesson.id(), "Título nuevo", 1, lesson.sourcePath(), lesson.sourceAvailable(),
						lesson.available(), lesson.bodyPath(), lesson.exercises())
				: lesson);
		Manifest renamed = new Manifest(clash.schemaVersion(), clash.source(),
				new Manifest.Course("demo", "demo", "Otro título"), clash.lessons(), clash.resources(), clash.sourceToId());
		var before = TestDatabase.snapshot(jdbc);

		assertThat(catchThrowable(() -> synchronizer.synchronize(List.of(java, renamed)))).isNotNull();

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	private int count(String fromWhere) {
		return jdbc.queryForObject("select count(*) from code_academy." + fromWhere, Integer.class);
	}

	private static void removeWhere(ArrayNode array, String field, String value) {
		for (int i = array.size() - 1; i >= 0; i--) {
			if (array.get(i).get(field).asString().equals(value)) array.remove(i);
		}
	}

	private static Manifest withLessons(Manifest manifest, UnaryOperator<Lesson> change) {
		List<Lesson> lessons = new ArrayList<>();
		manifest.lessons().forEach(lesson -> lessons.add(change.apply(lesson)));
		return new Manifest(manifest.schemaVersion(), manifest.source(), manifest.course(), lessons,
				manifest.resources(), manifest.sourceToId());
	}

	private static Lesson withExercises(Lesson lesson, List<Manifest.Exercise> exercises) {
		return new Lesson(lesson.id(), lesson.title(), lesson.position(), lesson.sourcePath(), lesson.sourceAvailable(),
				lesson.available(), lesson.bodyPath(), exercises);
	}
}
