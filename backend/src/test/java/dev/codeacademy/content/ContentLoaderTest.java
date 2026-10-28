package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static dev.codeacademy.content.ContentFixture.INTRO;
import static dev.codeacademy.content.ContentFixture.lesson;
import static dev.codeacademy.content.ContentFixture.link;
import static dev.codeacademy.content.ContentFixture.phase;
import static dev.codeacademy.content.ContentFixture.resource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The synthetic course: what a valid manifest produces, and every fail-fast rejection. */
class ContentLoaderTest {

	@TempDir
	Path tempDir;

	ContentFixture fixture;

	@BeforeEach
	void copy() {
		fixture = ContentFixture.copyInto(tempDir);
	}

	@Test
	void validCourseRendersLinksExercisesAndResources() {
		CourseContent content = fixture.load();

		assertThat(content.lessons()).containsOnlyKeys("demo-01.01", "demo-01.02");
		LessonContent intro = content.lessons().get("demo-01.01");
		assertThat(intro.bodyHtml())
			.contains("<h2 id=\"repetida\">Repetida</h2>", "<h2 id=\"repetida-1\">Repetida</h2>")
			.contains("<a href=\"/lessons/demo-02.01\">02.01 Pendiente</a>")
			.contains("<a href=\"#repetida-1\">", "<a href=\"/lessons/demo-01.02#sección-b\">",
					"<a href=\"#demo-01.01-e02\">", "<a href=\"/courses/demo\">índice</a>")
			.contains("<code>codigo/hola/</code> (<a href=\"/api/resources/demo-01.01-r-hello\">Hello.java</a>, "
					+ "<a href=\"/api/resources/demo-01.01-r-other\">Other.java</a>)")
			.contains("<a href=\"/api/resources/demo-01.01-r-solo\">Solo.java</a>")
			.contains("<a href=\"https://dev.java/learn/\" target=\"_blank\" rel=\"noopener noreferrer\">la web</a>")
			.contains("<code class=\"language-mermaid\">graph TD")
			.contains("Final del cuerpo")
			.doesNotContain("Ejercicios prácticos", "Básico", "Enunciado", "Solución", "<details");
		assertThat(intro.exercises()).extracting(ExerciseContent::id).containsExactly("demo-01.01-e01", "demo-01.01-e02");
		assertThat(intro.exercises()).extracting(ExerciseContent::title).containsExactly("Primer ejercicio", "Segundo ejercicio");
		ExerciseContent first = intro.exercises().get(0);
		assertThat(first.courseId()).isEqualTo("demo");
		assertThat(first.statementHtml())
			.contains("Enunciado uno con código (<a href=\"/api/resources/demo-01.01-r-solo\">Solo.java</a>)")
			.doesNotContain("<details", "Solución");
		assertThat(first.solutionHtml()).startsWith("<details>").contains("<summary>Ver solución</summary>",
				"<a href=\"#repetida\">Repetida</a>").doesNotContain("Enunciado");
		assertThat(intro.resourceIds()).containsExactly("demo-01.01-r-hello", "demo-01.01-r-other", "demo-01.01-r-solo");
		assertThat(content.lessons().get("demo-01.02").bodyHtml())
			.contains("<a href=\"/lessons/demo-01.01#qué-aprenderás\">", "<h2 id=\"sección-b\">Sección B</h2>");
		ResourceFile hello = content.resources().get("demo-01.01-r-hello");
		assertThat(hello.originalFileName()).isEqualTo("Hello.java");
		assertThat(hello.localPath()).isEqualTo(fixture.root.resolve("demo/resources/demo-01.01-r-hello.java").toAbsolutePath());
		assertThat(hello.mediaType()).isEqualTo("text/plain; charset=utf-8");
	}

	// --- manifest structure ---

	@Test
	void rejectsUnknownSchemaVersion() {
		fixture.editManifest(m -> m.put("schemaVersion", 2));
		fixture.rejected("schemaVersion");
	}

	@Test
	void rejectsUnknownManifestField() {
		fixture.editManifest(m -> m.put("extra", true));
		fixture.rejected("manifest");
	}

	@Test
	void rejectsThePlaceholderVersionRef() {
		fixture.editManifest(m -> ((ObjectNode) m.get("source")).put("versionRef", "PENDIENTE_HASH_COMPLETO_DEL_COMMIT"));
		fixture.rejected("versionRef");
	}

	@Test
	void rejectsAnAbbreviatedVersionRef() {
		fixture.editManifest(m -> ((ObjectNode) m.get("source")).put("versionRef", "0c2df357"));
		fixture.rejected("versionRef");
	}

	@Test
	void rejectsDuplicateLessonIds() {
		fixture.editManifest(m -> lesson(m, "demo-02.01").put("id", "demo-01.02"));
		fixture.rejected("duplicate id demo-01.02");
	}

	@Test
	void rejectsAnExerciseIdThatRepeatsAResourceId() {
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo-01.01").get("exercises").get(1)).put("id", "demo-01.01-r-solo"));
		fixture.rejected("duplicate id demo-01.01-r-solo");
	}

	@Test
	void rejectsALessonIdOutsideTheCourseNamespace() {
		fixture.editManifest(m -> {
			lesson(m, "demo-02.01").put("id", "java-02.01");
			link(m, "fase-02/01-pendiente.md").put("lessonId", "java-02.01");
		});
		fixture.rejected("java-02.01 is not in namespace demo");
	}

	@Test
	void rejectsAResourceIdOutsideTheCourseNamespace() {
		fixture.editManifest(m -> {
			resource(m, "demo-01.01-r-solo").put("resourceId", "other-r-solo");
			link(m, "fase-01/codigo/solo/Solo.java").put("resourceId", "other-r-solo");
			((ArrayNode) link(m, "fase-01/codigo/solo/").get("resourceIds")).set(0, "other-r-solo");
		});
		fixture.rejected("other-r-solo is not in namespace demo");
	}

	@Test
	void rejectsAnExerciseThatClaimsAnotherLesson() {
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo-01.01").get("exercises").get(0)).put("lessonId", "demo-01.02"));
		fixture.rejected("demo-01.01-e01 belongs to demo-01.02");
	}

	@Test
	void rejectsAResourceOfAnUnknownLesson() {
		fixture.editManifest(m -> resource(m, "demo-01.01-r-solo").put("lessonId", "demo-09.09"));
		fixture.rejected("unknown lesson demo-09.09");
	}

	@Test
	void rejectsALinkToAnotherCourse() {
		fixture.editManifest(m -> link(m, "00-indice.md").put("courseId", "java"));
		fixture.rejected("unknown course java");
	}

	@Test
	void rejectsAvailableWithoutSourceAvailable() {
		fixture.editManifest(m -> lesson(m, "demo-01.02").put("sourceAvailable", false));
		fixture.rejected("demo-01.02 is available but not sourceAvailable");
	}

	@Test
	void rejectsAvailableWithoutBodyPath() {
		fixture.editManifest(m -> lesson(m, "demo-01.02").putNull("bodyPath"));
		fixture.rejected("demo-01.02 is available without bodyPath");
	}

	@Test
	void rejectsAMissingBody() {
		fixture.editManifest(m -> lesson(m, "demo-01.02").put("bodyPath", "demo/lessons/missing.md"));
		fixture.rejected("demo/lessons/missing.md");
	}

	@Test
	void rejectsAPendingLessonWithBodyOrExercises() {
		fixture.editManifest(m -> lesson(m, "demo-02.01").put("bodyPath", "demo/lessons/demo-01.02.md"));
		fixture.rejected("demo-02.01 is pending but has bodyPath or exercises");
	}

	@Test
	void rejectsPositionsWithAGap() {
		fixture.editManifest(m -> lesson(m, "demo-02.01").put("position", 4));
		fixture.rejected("positions");
	}

	@Test
	void rejectsRepeatedPositions() {
		fixture.editManifest(m -> lesson(m, "demo-02.01").put("position", 2));
		fixture.rejected("positions");
	}

	// --- phases ---

	@Test
	void validCourseKeepsItsPhasesAndTheLessonsPhase() {
		Manifest manifest = fixture.load().manifest();

		assertThat(manifest.phases()).containsExactly(new Manifest.Phase("demo-phase-01", "Primera fase", 1),
				new Manifest.Phase("demo-phase-02", "Segunda fase", 2));
		assertThat(manifest.lessons()).extracting(Manifest.Lesson::phaseId)
			.containsExactly("demo-phase-01", "demo-phase-01", "demo-phase-02");
	}

	@Test
	void rejectsAManifestWithoutPhases() {
		fixture.editManifest(m -> m.remove("phases"));
		fixture.rejected("manifest needs phases");
	}

	@Test
	void rejectsAPhaseWithoutTitleOrPosition() {
		fixture.editManifest(m -> phase(m, "demo-phase-01").put("title", " "));
		fixture.rejected("phase demo-phase-01 needs id, title and position");

		fixture = ContentFixture.copyInto(tempDir.resolve("sin-posicion"));
		fixture.editManifest(m -> phase(m, "demo-phase-02").remove("position"));
		fixture.rejected("phase demo-phase-02 needs id, title and position");
	}

	@Test
	void rejectsDuplicatePhaseIdsAndAPhaseIdThatRepeatsALessonId() {
		fixture.editManifest(m -> phase(m, "demo-phase-02").put("id", "demo-phase-01"));
		fixture.rejected("duplicate id demo-phase-01");

		fixture = ContentFixture.copyInto(tempDir.resolve("repite-leccion"));
		fixture.editManifest(m -> {
			phase(m, "demo-phase-02").put("id", "demo-01.01");
			lesson(m, "demo-02.01").put("phaseId", "demo-01.01");
		});
		fixture.rejected("duplicate id demo-01.01");
	}

	@Test
	void rejectsAPhaseIdOutsideTheCourseNamespace() {
		fixture.editManifest(m -> {
			phase(m, "demo-phase-02").put("id", "java-phase-02");
			lesson(m, "demo-02.01").put("phaseId", "java-phase-02");
		});
		fixture.rejected("id java-phase-02 is not in namespace demo");
	}

	@Test
	void rejectsPhasePositionsWithAGapOrRepeats() {
		fixture.editManifest(m -> phase(m, "demo-phase-02").put("position", 3));
		fixture.rejected("phase positions must be 1..2 without gaps or repeats");

		fixture = ContentFixture.copyInto(tempDir.resolve("repetida"));
		fixture.editManifest(m -> phase(m, "demo-phase-02").put("position", 1));
		fixture.rejected("phase positions must be 1..2 without gaps or repeats");
	}

	@Test
	void rejectsALessonWithoutPhase() {
		fixture.editManifest(m -> lesson(m, "demo-01.02").remove("phaseId"));
		fixture.rejected("lesson demo-01.02 needs phaseId");
	}

	@Test
	void rejectsALessonOfAnUnknownPhase() {
		fixture.editManifest(m -> lesson(m, "demo-02.01").put("phaseId", "demo-phase-09"));
		fixture.rejected("lesson demo-02.01: unknown phase demo-phase-09");
	}

	@Test
	void rejectsAPhaseWithoutLessons() {
		fixture.editManifest(m -> lesson(m, "demo-02.01").put("phaseId", "demo-phase-01"));
		fixture.rejected("phase demo-phase-02 has no lessons");
	}

	@Test
	void rejectsLessonsThatDoNotFollowThePhaseOrder() {
		// Phases in the opposite order of their lessons.
		fixture.editManifest(m -> {
			phase(m, "demo-phase-01").put("position", 2);
			phase(m, "demo-phase-02").put("position", 1);
		});
		fixture.rejected("lesson demo-02.01 (position 3) is in phase demo-phase-02 (position 1), "
				+ "after a lesson of phase demo-phase-01 (position 2)");

		// A phase split in two by a lesson of another phase.
		fixture = ContentFixture.copyInto(tempDir.resolve("partida"));
		fixture.editManifest(m -> {
			lesson(m, "demo-01.02").put("phaseId", "demo-phase-02");
			lesson(m, "demo-02.01").put("phaseId", "demo-phase-01");
		});
		fixture.rejected("lesson demo-02.01 (position 3) is in phase demo-phase-01 (position 1), "
				+ "after a lesson of phase demo-phase-02 (position 2)");
	}

	// --- paths ---

	@Test
	void rejectsABodyPathThatEscapesWithDotDot() throws IOException {
		Files.writeString(tempDir.resolve("outside.md"), "# Fuera\n");
		fixture.editManifest(m -> lesson(m, "demo-01.02").put("bodyPath", "demo/../../outside.md"));
		fixture.rejected("demo/../../outside.md");
	}

	@Test
	void rejectsAnAbsoluteLocalPath() {
		String absolute = fixture.root.resolve("demo/resources/demo-01.01-r-solo.java").toAbsolutePath().toString();
		fixture.editManifest(m -> resource(m, "demo-01.01-r-solo").put("localPath", absolute));
		fixture.rejected(absolute);
	}

	@Test
	void rejectsADirectorySymlinkOnTheBodyPath() throws IOException {
		Path outside = Files.createDirectories(tempDir.resolve("outside"));
		Files.writeString(outside.resolve("lesson.md"), "# Fuera\n");
		Files.createSymbolicLink(fixture.root.resolve("demo/linked"), outside);
		fixture.editManifest(m -> lesson(m, "demo-01.02").put("bodyPath", "demo/linked/lesson.md"));
		fixture.rejected("demo/linked/lesson.md");
	}

	@Test
	void rejectsASymlinkInsideTheContentRootToo() throws IOException {
		Files.createSymbolicLink(fixture.root.resolve("demo/resources/alias.java"),
				fixture.root.resolve("demo/resources/demo-01.01-r-solo.java"));
		fixture.editManifest(m -> resource(m, "demo-01.01-r-solo").put("localPath", "demo/resources/alias.java"));
		fixture.rejected("demo/resources/alias.java");
	}

	@Test
	void rejectsAMissingResourceFile() {
		fixture.editManifest(m -> resource(m, "demo-01.01-r-solo").put("localPath", "demo/resources/missing.java"));
		fixture.rejected("demo/resources/missing.java");
	}

	// --- sourceToId ---

	@Test
	void rejectsSourceToIdPointingToAnUnknownLesson() {
		fixture.editManifest(m -> link(m, "fase-02/01-pendiente.md").put("lessonId", "demo-09.09"));
		fixture.rejected("unknown lesson demo-09.09");
	}

	@Test
	void rejectsSourceToIdPointingToAnUnknownResource() {
		fixture.editManifest(m -> link(m, "fase-01/codigo/solo/Solo.java").put("resourceId", "demo-01.01-r-nope"));
		fixture.rejected("unknown resource demo-01.01-r-nope");
	}

	@Test
	void rejectsASourceToIdEntryWithTwoTargets() {
		fixture.editManifest(m -> link(m, "00-indice.md").put("lessonId", "demo-01.01"));
		fixture.rejected("00-indice.md must have exactly one target");
	}

	@Test
	void rejectsAFolderThatMissesOneOfItsFiles() {
		fixture.editManifest(m -> ((ArrayNode) link(m, "fase-01/codigo/hola/").get("resourceIds")).remove(1));
		fixture.rejected("folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	@Test
	void rejectsAFolderThatListsAFileFromElsewhere() {
		fixture.editManifest(m -> ((ArrayNode) link(m, "fase-01/codigo/hola/").get("resourceIds")).add("demo-01.01-r-solo"));
		fixture.rejected("folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	// --- lesson content ---

	@Test
	void rejectsAnUnmappedRelativeLink() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [otro](03-otra.md).");
		fixture.rejected("demo-01.01: unresolved link 03-otra.md");
	}

	@Test
	void rejectsALinkThatClimbsAboveTheSourceRoot() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [fuera](../../00-indice.md).");
		fixture.rejected("demo-01.01: unresolved link ../../00-indice.md");
	}

	@Test
	void rejectsAnUnknownOwnFragment() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [nada](#no-existe).");
		fixture.rejected("demo-01.01: unresolved link #no-existe");
	}

	@Test
	void rejectsAFragmentToTheRemovedExercisesSection() {
		fixture.editManifest(m -> ((ObjectNode) link(m, "fase-01/01-intro.md").get("fragments"))
			.put("5-ejercicios-prácticos-resueltos", "5-ejercicios-prácticos-resueltos"));
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [ejercicios](#5-ejercicios-prácticos-resueltos).");
		fixture.rejected("demo-01.01: unresolved link #5-ejercicios-prácticos-resueltos");
	}

	@Test
	void rejectsACrossLessonFragmentMissingFromItsFragments() {
		fixture.replace(INTRO, "02-siguiente.md#sección-b", "02-siguiente.md#sección-a");
		fixture.rejected("demo-01.01: unresolved link 02-siguiente.md#sección-a");
	}

	@Test
	void rejectsAMappedFragmentWhoseHeadingDoesNotExist() {
		fixture.editManifest(m -> ((ObjectNode) link(m, "fase-01/02-siguiente.md").get("fragments")).put("sección-b", "sección-z"));
		fixture.rejected("demo-01.01: unresolved link 02-siguiente.md#sección-b");
	}

	@Test
	void rejectsAnUnresolvedLinkInsideAStatement() {
		fixture.replace(INTRO, "Enunciado dos.", "Enunciado dos con [roto](codigo/nada/).");
		fixture.rejected("demo-01.01 demo-01.01-e02 statement: unresolved link codigo/nada/");
	}

	@Test
	void rejectsAnUnresolvedFragmentInsideASolution() {
		fixture.replace(INTRO, "Solución dos.", "Solución dos, ver [arriba](#arriba).");
		fixture.rejected("demo-01.01 demo-01.01-e02 solution: unresolved link #arriba");
	}

	@Test
	void rejectsAnExerciseRemovedFromTheManifest() {
		fixture.editManifest(m -> ((ArrayNode) lesson(m, "demo-01.01").get("exercises")).remove(1));
		fixture.rejected("demo-01.01: exercise heading «Segundo ejercicio» has no exercise with that title in the manifest");
	}

	@Test
	void reorderedExercisesKeepTheirIds() {
		String text = fixture.read(INTRO);
		int first = text.indexOf("#### Ejercicio 1 · ");
		int second = text.indexOf("#### Ejercicio 2 · ");
		int end = text.indexOf("---\n\n## 6. Chuleta");
		fixture.write(INTRO, text.substring(0, first) + text.substring(second, end) + text.substring(first, second)
				+ text.substring(end));

		LessonContent intro = fixture.load().lessons().get("demo-01.01");

		assertThat(intro.exercises()).extracting(ExerciseContent::id).containsExactly("demo-01.01-e01", "demo-01.01-e02");
		assertThat(intro.exercises()).extracting(ExerciseContent::title).containsExactly("Primer ejercicio", "Segundo ejercicio");
		assertThat(intro.exercises().get(0).statementHtml()).contains("Enunciado uno");
		assertThat(intro.exercises().get(0).solutionHtml()).contains("Solución uno");
		assertThat(intro.exercises().get(1).statementHtml()).contains("Enunciado dos");
		assertThat(intro.exercises().get(1).solutionHtml()).contains("Solución dos");
		assertThat(intro.bodyHtml()).contains("<a href=\"#demo-01.01-e02\">");
	}

	@Test
	void rejectsAManifestExerciseTitleThatDiffersFromItsHeading() {
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo-01.01").get("exercises").get(0)).put("title", "Otro"));
		fixture.rejected("demo-01.01: exercise demo-01.01-e01 «Otro» has no heading with that title in the lesson");
	}

	@Test
	void rejectsANewExerciseThatTheManifestDoesNotList() {
		fixture.replace(INTRO, "---\n\n## 6. Chuleta", "#### Ejercicio 3 · Ejercicio nuevo\n\nEnunciado tres.\n\n<details>\n"
				+ "<summary>Ver solución</summary>\n\nSolución tres.\n\n</details>\n\n---\n\n## 6. Chuleta");
		fixture.rejected("demo-01.01: exercise heading «Ejercicio nuevo» has no exercise with that title in the manifest");
	}

	@Test
	void rejectsAnExerciseHeadingThatDoesNotFollowTheFormat() throws Exception {
		for (String malformed : java.util.List.of("Ejercicio 3: Mal formado", "Ejercicio 3 - Mal formado",
				"Ejercicio tres · Mal formado", "Ejercicio 3 ·Mal formado")) {
			var fixture = ContentFixture.copyInto(java.nio.file.Files.createTempDirectory(tempDir, "malformed"));
			fixture.replace(INTRO, "---\n\n## 6. Chuleta", "#### " + malformed + "\n\nEnunciado.\n\n---\n\n## 6. Chuleta");
			fixture.rejected("demo-01.01: exercise heading «" + malformed + "» does not match «Ejercicio N · Título»");
		}
	}

	@Test
	void rejectsAManifestExerciseWhoseHeadingNoLongerExists() {
		String text = fixture.read(INTRO);
		fixture.write(INTRO, text.substring(0, text.indexOf("#### Ejercicio 1 · "))
				+ text.substring(text.indexOf("#### Ejercicio 2 · ")));
		fixture.rejected("demo-01.01: exercise demo-01.01-e01 «Primer ejercicio» has no heading with that title in the lesson");
	}

	@Test
	void rejectsAHeadingWithoutManifestEntryAndDuplicateTitles() {
		fixture.replace(INTRO, "Ejercicio 1 · Primer ejercicio", "Ejercicio 1 · Primer ejercicio renombrado");
		fixture.rejected("demo-01.01: exercise heading «Primer ejercicio renombrado» has no exercise with that title in the manifest");

		ContentFixture headings = ContentFixture.copyInto(tempDir.resolve("headings"));
		headings.replace(INTRO, "Ejercicio 2 · Segundo `ejercicio`", "Ejercicio 2 · Primer ejercicio");
		headings.rejected("demo-01.01: more than one exercise heading titled «Primer ejercicio»");

		ContentFixture entries = ContentFixture.copyInto(tempDir.resolve("entries"));
		entries.editManifest(m -> ((ObjectNode) lesson(m, "demo-01.01").get("exercises").get(1)).put("title", "Primer ejercicio"));
		entries.rejected("exercise demo-01.01-e02 of demo-01.01 repeats the title «Primer ejercicio»");
	}

	@Test
	void rejectsAHeadingThatDiffersFromItsManifestTitleOnlyInUnicodeNormalization() {
		fixture.replace(INTRO, "Ejercicio 1 · Primer ejercicio", "Ejercicio 1 · Primer ejercicio cafe\u0301");
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo-01.01").get("exercises").get(0))
			.put("title", "Primer ejercicio caf\u00e9"));
		fixture.rejected("demo-01.01: exercise demo-01.01-e01 «Primer ejercicio caf\u00e9» has no heading with that title in the lesson");
	}

	@Test
	void rejectsDuplicatedHeadingIdsFromRawHtml() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo.\n\n<h2 id=\"qué-aprenderás\">x</h2>");
		fixture.rejected("demo-01.01: repeated id qué-aprenderás");
	}

	@Test
	void rejectsAnExerciseWithoutSolution() {
		fixture.replace(INTRO, "Enunciado dos.\n\n<details>", "Enunciado dos.\n\n<div>");
		fixture.rejected("demo-01.01: exercise Segundo ejercicio has no <details> solution");
	}

	@Test
	void rejectsADisallowedLinkScheme() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [clic](javascript:alert(1)).");
		fixture.rejected("demo-01.01: disallowed link javascript:alert(1)");
	}

	@Test
	void rejectsAProtocolRelativeLink() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [clic](//evil.example/x).");
		fixture.rejected("demo-01.01: disallowed link //evil.example/x");
	}

	// --- every course of the content root ---

	@Test
	void loadAllReadsEveryCourseDirectoryOfTheRoot() {
		assertThat(ContentLoader.loadAll(fixture.root)).extracting(course -> course.manifest().course().id())
			.containsExactly("demo");
	}

	@Test
	void loadAllRejectsIdsRepeatedAcrossCourses() throws IOException {
		Path copy = Files.createDirectories(fixture.root.resolve("copia"));
		Files.copy(fixture.root.resolve(ContentFixture.MANIFEST), copy.resolve("manifest.json"));

		ContentValidationException ex = assertThrows(ContentValidationException.class,
				() -> ContentLoader.loadAll(fixture.root));

		assertThat(ex.getMessage()).contains("id demo is used by more than one course",
				"id demo-01.01-e02 is used by more than one course", "id demo-01.01-r-hello is used by more than one course",
				"id demo-phase-01 is used by more than one course");
	}

	@Test
	void loadAllRejectsARootWithoutCoursesOrACourseWithoutManifest() throws IOException {
		Path empty = Files.createDirectories(tempDir.resolve("vacio"));
		assertThat(assertThrows(ContentValidationException.class,
				() -> ContentLoader.loadAll(empty)).getMessage()).contains("has no course");

		Files.createDirectories(fixture.root.resolve("sin-manifiesto"));
		assertThat(assertThrows(ContentValidationException.class,
				() -> ContentLoader.loadAll(fixture.root)).getMessage())
			.contains("manifest sin-manifiesto/manifest.json does not exist");
	}
}
