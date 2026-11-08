package dev.codeacademy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.catalog.CatalogConflictException;
import dev.codeacademy.catalog.CatalogSynchronizer;
import dev.codeacademy.content.ContentFixture;
import dev.codeacademy.content.ContentLoader;
import dev.codeacademy.content.ContentValidationException;
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

	/** The real course takes seconds to load and no test changes it, so every test shares one load. */
	private static Manifest realJava;

	Manifest java;
	ContentFixture demo;

	@BeforeAll
	static void loadRealCourse() {
		realJava = ContentLoader.load(CatalogApiTest.CONTENT, "java/manifest.json").manifest();
	}

	@BeforeEach
	void synchronizedBase() {
		java = realJava;
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
						Map.of("id", "demo-01.01-e02", "lesson_id", "demo-01.01", "title", "Segundo ejercicio",
								"position", 2));
		assertThat(jdbc.queryForMap("select course_id, title, position, available, source_available "
				+ "from code_academy.lessons where id = 'demo-02.01'")).isEqualTo(Map.of("course_id", "demo",
						"title", "Pendiente", "position", 3, "available", false, "source_available", false));
		assertThat(jdbc.queryForList("select id, course_id, title, position from code_academy.phases "
				+ "where course_id = 'demo' order by position")).containsExactly(
						Map.of("id", "demo-phase-01", "course_id", "demo", "title", "Primera fase", "position", 1),
						Map.of("id", "demo-phase-02", "course_id", "demo", "title", "Segunda fase", "position", 2));
		assertThat(jdbc.queryForList("select id || ' ' || phase_id from code_academy.lessons where course_id = 'demo' "
				+ "order by position", String.class)).containsExactly("demo-01.01 demo-phase-01",
						"demo-01.02 demo-phase-01", "demo-02.01 demo-phase-02");
		assertThat(count("phases where course_id = 'java'")).isEqualTo(21);
		assertThat(jdbc.queryForObject("select title from code_academy.phases where id = 'java-phase-02'", String.class))
				.isEqualTo("Fundamentos de Java");
		assertThat(jdbc.queryForObject("select phase_id from code_academy.lessons where id = 'java-02.01'", String.class))
				.isEqualTo("java-phase-02");
		assertThat(count("lessons where phase_id is null")).isZero();
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
	void aLessonMovedToAnotherPhaseAndRetitledOrReorderedPhasesAreSynchronized() {
		demo.editManifest(manifest -> {
			ContentFixture.lesson(manifest, "demo-01.02").put("phaseId", "demo-phase-02");
			ContentFixture.phase(manifest, "demo-phase-02").put("title", "Segunda fase renovada");
		});

		synchronizer.synchronize(List.of(java, demo.load().manifest()));

		assertThat(jdbc.queryForList("select id || ' ' || phase_id from code_academy.lessons where course_id = 'demo' "
				+ "order by position", String.class)).containsExactly("demo-01.01 demo-phase-01",
						"demo-01.02 demo-phase-02", "demo-02.01 demo-phase-02");
		assertThat(jdbc.queryForList("select id || ' ' || position || ' ' || title from code_academy.phases "
				+ "where course_id = 'demo' order by position", String.class))
				.containsExactly("demo-phase-01 1 Primera fase", "demo-phase-02 2 Segunda fase renovada");

		// Swapping the phases' positions (and the lessons with them) only fails at commit if the check is not deferred.
		demo.editManifest(manifest -> {
			ContentFixture.phase(manifest, "demo-phase-01").put("position", 2);
			ContentFixture.phase(manifest, "demo-phase-02").put("position", 1);
			ContentFixture.lesson(manifest, "demo-01.02").put("position", 1);
			ContentFixture.lesson(manifest, "demo-02.01").put("position", 2);
			ContentFixture.lesson(manifest, "demo-01.01").put("position", 3);
		});

		synchronizer.synchronize(List.of(java, demo.load().manifest()));

		assertThat(jdbc.queryForList("select id || ' ' || position from code_academy.phases where course_id = 'demo' "
				+ "order by position", String.class)).containsExactly("demo-phase-02 1", "demo-phase-01 2");
	}

	@Test
	void aStoredPhaseRemovedFromTheManifestIsRejectedWithoutChanges() {
		demo.editManifest(manifest -> {
			removeWhere((ArrayNode) manifest.get("phases"), "id", "demo-phase-02");
			ContentFixture.lesson(manifest, "demo-02.01").put("phaseId", "demo-phase-01");
			manifest.withObject("course").put("title", "Cambio que no debe guardarse");
		});
		Manifest withoutPhase = demo.load().manifest();
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, withoutPhase)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("phase demo-phase-02 is no longer in the manifest");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aRenamedPhaseIdIsRejectedWithoutChanges() {
		demo.editManifest(manifest -> {
			ContentFixture.phase(manifest, "demo-phase-02").put("id", "demo-phase-segunda");
			ContentFixture.lesson(manifest, "demo-02.01").put("phaseId", "demo-phase-segunda");
		});
		Manifest renamed = demo.load().manifest();
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, renamed)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("phase demo-phase-02 is no longer in the manifest");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aLessonRemovedFromTheManifestIsRejectedWithoutChanges() {
		demo.editManifest(manifest -> {
			removeWhere((ArrayNode) manifest.get("lessons"), "id", "demo-02.01");
			// Its phase would be left empty, which the manifest rules reject.
			removeWhere((ArrayNode) manifest.get("phases"), "id", "demo-phase-02");
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
				.hasMessageContaining("phase demo-phase-01 belongs to course demo, not demo-nuevo")
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
	void aPhaseMovedToACoexistingCourseIsRejectedWithoutChanges() {
		Manifest base = demo.load().manifest();
		Manifest other = new Manifest(1, base.source(), new Manifest.Course("otro", "demo", "Otro"),
				List.of(new Manifest.Phase("demo-phase-02", "Segunda fase", 1)),
				List.of(new Lesson("demo-90.01", "Nueva", 1, "demo-phase-02", "x/90.md", false, false, null, List.of())),
				List.of(), List.of());
		Manifest demoWithout = new Manifest(base.schemaVersion(), base.source(), base.course(),
				base.phases().subList(0, 1), base.lessons().stream().map(l -> new Lesson(l.id(), l.title(), l.position(),
						"demo-phase-01", l.sourcePath(), l.sourceAvailable(), l.available(), l.bodyPath(),
						l.exercises())).toList(), base.resources(), base.sourceToId());
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, demoWithout, other)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("phase demo-phase-02 belongs to course demo, not otro");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aStoredExerciseWhoseTitleChangesIsRejectedWithoutChanges() {
		// The title binds an exercise ID to its content, so swapping titles in the manifest would move progress.
		ContentFixture swapped = ContentFixture.copyInto(tempDir.resolve("swapped"));
		swapped.editManifest(manifest -> {
			var exercises = ContentFixture.lesson(manifest, "demo-01.01").get("exercises");
			String first = exercises.get(0).get("title").asString();
			String second = exercises.get(1).get("title").asString();
			((ObjectNode) exercises.get(0)).put("title", second);
			((ObjectNode) exercises.get(1)).put("title", first);
		});
		Manifest loaded = swapped.load().manifest();
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, loaded)))
				.isInstanceOf(CatalogConflictException.class)
				.hasMessageContaining("exercise demo-01.01-e01 is titled «Primer ejercicio», not «Segundo ejercicio»")
				.hasMessageContaining("exercise demo-01.01-e02 is titled «Segundo ejercicio», not «Primer ejercicio»");
		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void anExerciseTitleConflictWithTheLessonFailsTheLoadAndLeavesTheDatabaseUntouched() {
		ContentFixture retitled = ContentFixture.copyInto(tempDir.resolve("retitled"));
		retitled.editManifest(manifest -> ((ObjectNode) ContentFixture.lesson(manifest, "demo-01.01").get("exercises").get(0))
			.put("title", "Otro título"));
		ContentFixture missing = ContentFixture.copyInto(tempDir.resolve("missing"));
		missing.replace(ContentFixture.INTRO, "#### Ejercicio 1 · Primer ejercicio", "#### Ejercicio 1 · Primer ejercicio quitado");
		var before = TestDatabase.snapshot(jdbc);

		// The load has no database access: it fails before the synchronization is ever called.
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, retitled.load().manifest())))
				.isInstanceOf(ContentValidationException.class)
				.hasMessageContaining("exercise demo-01.01-e01 «Otro título» has no heading with that title");
		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, missing.load().manifest())))
				.isInstanceOf(ContentValidationException.class)
				.hasMessageContaining("exercise demo-01.01-e01 «Primer ejercicio» has no heading with that title");

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aFailureDuringTheWritesLeavesNoPartialChange() {
		Manifest base = demo.load().manifest();
		// Two lessons on one position only fail when the transaction commits, after every other write.
		Manifest clash = withLessons(base, lesson -> lesson.id().equals("demo-02.01")
				? new Lesson(lesson.id(), "Título nuevo", 1, lesson.phaseId(), lesson.sourcePath(),
						lesson.sourceAvailable(), lesson.available(), lesson.bodyPath(), lesson.exercises())
				: lesson);
		Manifest renamed = new Manifest(clash.schemaVersion(), clash.source(),
				new Manifest.Course("demo", "demo", "Otro título"), clash.phases(), clash.lessons(), clash.resources(),
				clash.sourceToId());
		var before = TestDatabase.snapshot(jdbc);

		assertThat(catchThrowable(() -> synchronizer.synchronize(List.of(java, renamed)))).isNotNull();

		assertThat(TestDatabase.snapshot(jdbc)).isEqualTo(before);
	}

	@Test
	void aLessonLeftWithoutPhaseFailsTheSynchronizationWithoutChanges() {
		// The manifest rules require a phase; this bypasses them to prove the synchronizer never commits a null one.
		Manifest noPhase = withLessons(demo.load().manifest(), lesson -> lesson.id().equals("demo-02.01")
				? new Lesson(lesson.id(), lesson.title(), lesson.position(), null, lesson.sourcePath(),
						lesson.sourceAvailable(), lesson.available(), lesson.bodyPath(), lesson.exercises())
				: lesson);
		var before = TestDatabase.snapshot(jdbc);

		assertThatThrownBy(() -> synchronizer.synchronize(List.of(java, noPhase)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("lessons without phase after synchronization: [demo-02.01]");

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
		return new Manifest(manifest.schemaVersion(), manifest.source(), manifest.course(), manifest.phases(),
				lessons, manifest.resources(), manifest.sourceToId());
	}

	private static Lesson withExercises(Lesson lesson, List<Manifest.Exercise> exercises) {
		return new Lesson(lesson.id(), lesson.title(), lesson.position(), lesson.phaseId(), lesson.sourcePath(),
				lesson.sourceAvailable(), lesson.available(), lesson.bodyPath(), exercises);
	}
}
