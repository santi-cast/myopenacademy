package dev.codeacademy.content;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

		assertThat(content.lessons()).containsOnlyKeys("demo.01.01", "demo.01.02");
		LessonContent intro = content.lessons().get("demo.01.01");
		assertThat(intro.bodyHtml())
			.contains("<h2 id=\"repetida\">Repetida</h2>", "<h2 id=\"repetida-1\">Repetida</h2>")
			.contains("<a href=\"/lessons/demo.02.01\">02.01 Pendiente</a>")
			.contains("<a href=\"#repetida-1\">", "<a href=\"/lessons/demo.01.02#sección-b\">",
					"<a href=\"#demo.01.01.e02\">", "<a href=\"/courses/demo\">índice</a>")
			.contains("<code>codigo/hola/</code> (<a href=\"/api/resources/demo.01.01-r-hello\">Hello.java</a>, "
					+ "<a href=\"/api/resources/demo.01.01-r-other\">Other.java</a>)")
			.contains("<a href=\"/api/resources/demo.01.01-r-solo\">Solo.java</a>")
			.contains("<a href=\"https://dev.java/learn/\" target=\"_blank\" rel=\"noopener noreferrer\">la web</a>")
			.contains("<code class=\"language-mermaid\">graph TD")
			.contains("Final del cuerpo")
			.doesNotContain("Ejercicios prácticos", "Básico", "Enunciado", "Solución", "<details");
		assertThat(intro.exercises()).extracting(ExerciseContent::id).containsExactly("demo.01.01.e01", "demo.01.01.e02");
		assertThat(intro.exercises()).extracting(ExerciseContent::title).containsExactly("Primer ejercicio", "Segundo ejercicio");
		ExerciseContent first = intro.exercises().get(0);
		assertThat(first.courseId()).isEqualTo("demo");
		assertThat(first.statementHtml())
			.contains("Enunciado uno con código (<a href=\"/api/resources/demo.01.01-r-solo\">Solo.java</a>)")
			.doesNotContain("<details", "Solución");
		assertThat(first.solutionHtml()).startsWith("<details>").contains("<summary>Ver solución</summary>",
				"<a href=\"#repetida\">Repetida</a>").doesNotContain("Enunciado");
		assertThat(intro.resourceIds()).containsExactly("demo.01.01-r-hello", "demo.01.01-r-other", "demo.01.01-r-solo");
		assertThat(content.lessons().get("demo.01.02").bodyHtml())
			.contains("<a href=\"/lessons/demo.01.01#qué-aprenderás\">", "<h2 id=\"sección-b\">Sección B</h2>");
		ResourceFile hello = content.resources().get("demo.01.01-r-hello");
		assertThat(hello.originalFileName()).isEqualTo("Hello.java");
		assertThat(hello.localPath()).isEqualTo(fixture.root.resolve("demo/resources/demo.01.01-r-hello.java").toAbsolutePath());
		assertThat(hello.mediaType()).isEqualTo("text/plain; charset=utf-8");
	}

	// --- manifest structure ---

	@Test
	void rejectsUnknownSchemaVersion() {
		fixture.editManifest(m -> m.put("schemaVersion", 3));
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
		fixture.editManifest(m -> lesson(m, "demo.02.01").put("id", "demo.01.02"));
		fixture.rejected("duplicate id demo.01.02");
	}

	@Test
	void rejectsAnExerciseIdThatRepeatsAResourceId() {
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo.01.01").get("exercises").get(1)).put("id", "demo.01.01-r-solo"));
		fixture.rejected("duplicate id demo.01.01-r-solo");
	}

	@Test
	void rejectsALessonIdOutsideTheCourseNamespace() {
		fixture.editManifest(m -> {
			lesson(m, "demo.02.01").put("id", "java.02.01");
			link(m, "fase-02/01-pendiente.md").put("lessonId", "java.02.01");
		});
		fixture.rejected("java.02.01 is not in namespace demo");
	}

	@Test
	void rejectsARecommendationOfItselfOrThatIsNotACourseId() {
		fixture.editManifest(m -> ((ObjectNode) m.get("course")).putArray("recommended").add("demo").add("Inicial"));
		fixture.rejected("course.recommended demo must be the id of another course");
		fixture.rejected("course.recommended Inicial must be the id of another course");
	}

	@Test
	void rejectsAProjectModuleThatIsNotALessonOrAnUnknownKind() {
		fixture.editManifest(m -> ((ObjectNode) m.get("course")).putObject("project").put("kind", "modulo")
			.put("title", "Proyecto").put("lessonId", "demo.no-existe"));
		fixture.rejected("project: unknown module demo.no-existe");
		fixture.editManifest(m -> ((ObjectNode) m.get("course")).putObject("project").put("kind", "otro").put("title", "Proyecto"));
		fixture.rejected("project: unsupported kind otro");
	}

	@Test
	void rejectsTheLegacyHyphenPrefix() {
		// FORMATO.md 3.3: <course>.<name>; <course>-<name> was the form the old importer invented (H7, T2).
		fixture.editManifest(m -> {
			lesson(m, "demo.02.01").put("id", "demo-02.01");
			link(m, "fase-02/01-pendiente.md").put("lessonId", "demo-02.01");
		});
		fixture.rejected("id demo-02.01 is not in namespace demo (expected demo.…)");
	}

	@Test
	void rejectsADeclaredIdWithCapitalsOrAccents() {
		fixture.editManifest(m -> {
			lesson(m, "demo.02.01").put("id", "demo.Lección");
			link(m, "fase-02/01-pendiente.md").put("lessonId", "demo.Lección");
		});
		fixture.rejected("id demo.Lección must be lower-case letters without accents, digits, hyphens and dots");
	}

	@Test
	void rejectsAResourceIdOutsideTheCourseNamespace() {
		fixture.editManifest(m -> {
			resource(m, "demo.01.01-r-solo").put("resourceId", "other.r-solo");
			link(m, "fase-01/codigo/solo/Solo.java").put("resourceId", "other.r-solo");
			((ArrayNode) link(m, "fase-01/codigo/solo/").get("resourceIds")).set(0, "other.r-solo");
		});
		fixture.rejected("other.r-solo is not in namespace demo");
	}

	@Test
	void rejectsAnExerciseThatClaimsAnotherLesson() {
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo.01.01").get("exercises").get(0)).put("lessonId", "demo.01.02"));
		fixture.rejected("demo.01.01.e01 belongs to demo.01.02");
	}

	@Test
	void rejectsAResourceOfAnUnknownLesson() {
		fixture.editManifest(m -> resource(m, "demo.01.01-r-solo").put("lessonId", "demo.09.09"));
		fixture.rejected("unknown lesson demo.09.09");
	}

	@Test
	void rejectsALinkToAnotherCourse() {
		fixture.editManifest(m -> link(m, "00-indice.md").put("courseId", "java"));
		fixture.rejected("unknown course java");
	}

	@Test
	void rejectsAvailableWithoutSourceAvailable() {
		fixture.editManifest(m -> lesson(m, "demo.01.02").put("sourceAvailable", false));
		fixture.rejected("demo.01.02 is available but not sourceAvailable");
	}

	@Test
	void rejectsAvailableWithoutBodyPath() {
		fixture.editManifest(m -> lesson(m, "demo.01.02").putNull("bodyPath"));
		fixture.rejected("demo.01.02 is available without bodyPath");
	}

	@Test
	void rejectsAMissingBody() {
		fixture.editManifest(m -> lesson(m, "demo.01.02").put("bodyPath", "demo/lessons/missing.md"));
		fixture.rejected("demo/lessons/missing.md");
	}

	@Test
	void rejectsAPendingLessonWithBodyOrExercises() {
		fixture.editManifest(m -> lesson(m, "demo.02.01").put("bodyPath", "demo/lessons/demo.01.02.md"));
		fixture.rejected("demo.02.01 is pending but has bodyPath or exercises");
	}

	@Test
	void rejectsPositionsWithAGap() {
		fixture.editManifest(m -> lesson(m, "demo.02.01").put("position", 4));
		fixture.rejected("positions");
	}

	@Test
	void rejectsRepeatedPositions() {
		fixture.editManifest(m -> lesson(m, "demo.02.01").put("position", 2));
		fixture.rejected("positions");
	}

	// --- phases ---

	@Test
	void validCourseKeepsItsPhasesAndTheLessonsPhase() {
		Manifest manifest = fixture.load().manifest();

		assertThat(manifest.phases()).containsExactly(new Manifest.Phase("demo.phase-01", "Primera fase", 1),
				new Manifest.Phase("demo.phase-02", "Segunda fase", 2));
		assertThat(manifest.lessons()).extracting(Manifest.Lesson::phaseId)
			.containsExactly("demo.phase-01", "demo.phase-01", "demo.phase-02");
	}

	@Test
	void rejectsAManifestWithoutPhases() {
		fixture.editManifest(m -> m.remove("phases"));
		fixture.rejected("manifest needs phases");
	}

	@Test
	void rejectsAPhaseWithoutTitleOrPosition() {
		fixture.editManifest(m -> phase(m, "demo.phase-01").put("title", " "));
		fixture.rejected("phase demo.phase-01 needs id, title and position");

		fixture = ContentFixture.copyInto(tempDir.resolve("sin-posicion"));
		fixture.editManifest(m -> phase(m, "demo.phase-02").remove("position"));
		fixture.rejected("phase demo.phase-02 needs id, title and position");
	}

	@Test
	void rejectsDuplicatePhaseIdsAndAPhaseIdThatRepeatsALessonId() {
		fixture.editManifest(m -> phase(m, "demo.phase-02").put("id", "demo.phase-01"));
		fixture.rejected("duplicate id demo.phase-01");

		fixture = ContentFixture.copyInto(tempDir.resolve("repite-leccion"));
		fixture.editManifest(m -> {
			phase(m, "demo.phase-02").put("id", "demo.01.01");
			lesson(m, "demo.02.01").put("phaseId", "demo.01.01");
		});
		fixture.rejected("duplicate id demo.01.01");
	}

	@Test
	void rejectsAPhaseIdOutsideTheCourseNamespace() {
		fixture.editManifest(m -> {
			phase(m, "demo.phase-02").put("id", "java.phase-02");
			lesson(m, "demo.02.01").put("phaseId", "java.phase-02");
		});
		fixture.rejected("id java.phase-02 is not in namespace demo");
	}

	@Test
	void rejectsPhasePositionsWithAGapOrRepeats() {
		fixture.editManifest(m -> phase(m, "demo.phase-02").put("position", 3));
		fixture.rejected("phase positions must be 1..2 without gaps or repeats");

		fixture = ContentFixture.copyInto(tempDir.resolve("repetida"));
		fixture.editManifest(m -> phase(m, "demo.phase-02").put("position", 1));
		fixture.rejected("phase positions must be 1..2 without gaps or repeats");
	}

	@Test
	void rejectsALessonWithoutPhase() {
		fixture.editManifest(m -> lesson(m, "demo.01.02").remove("phaseId"));
		fixture.rejected("lesson demo.01.02 needs phaseId");
	}

	@Test
	void rejectsALessonOfAnUnknownPhase() {
		fixture.editManifest(m -> lesson(m, "demo.02.01").put("phaseId", "demo.phase-09"));
		fixture.rejected("lesson demo.02.01: unknown phase demo.phase-09");
	}

	@Test
	void rejectsAPhaseWithoutLessons() {
		fixture.editManifest(m -> lesson(m, "demo.02.01").put("phaseId", "demo.phase-01"));
		fixture.rejected("phase demo.phase-02 has no lessons");
	}

	@Test
	void rejectsLessonsThatDoNotFollowThePhaseOrder() {
		// Phases in the opposite order of their lessons.
		fixture.editManifest(m -> {
			phase(m, "demo.phase-01").put("position", 2);
			phase(m, "demo.phase-02").put("position", 1);
		});
		fixture.rejected("lesson demo.02.01 (position 3) is in phase demo.phase-02 (position 1), "
				+ "after a lesson of phase demo.phase-01 (position 2)");

		// A phase split in two by a lesson of another phase.
		fixture = ContentFixture.copyInto(tempDir.resolve("partida"));
		fixture.editManifest(m -> {
			lesson(m, "demo.01.02").put("phaseId", "demo.phase-02");
			lesson(m, "demo.02.01").put("phaseId", "demo.phase-01");
		});
		fixture.rejected("lesson demo.02.01 (position 3) is in phase demo.phase-01 (position 1), "
				+ "after a lesson of phase demo.phase-02 (position 2)");
	}

	// --- paths ---

	@Test
	void rejectsABodyPathThatEscapesWithDotDot() throws IOException {
		Files.writeString(tempDir.resolve("outside.md"), "# Fuera\n");
		fixture.editManifest(m -> lesson(m, "demo.01.02").put("bodyPath", "demo/../../outside.md"));
		fixture.rejected("demo/../../outside.md");
	}

	@Test
	void rejectsAnAbsoluteLocalPath() {
		String absolute = fixture.root.resolve("demo/resources/demo.01.01-r-solo.java").toAbsolutePath().toString();
		fixture.editManifest(m -> resource(m, "demo.01.01-r-solo").put("localPath", absolute));
		fixture.rejected(absolute);
	}

	@Test
	void rejectsADirectorySymlinkOnTheBodyPath() throws IOException {
		Path outside = Files.createDirectories(tempDir.resolve("outside"));
		Files.writeString(outside.resolve("lesson.md"), "# Fuera\n");
		Files.createSymbolicLink(fixture.root.resolve("demo/linked"), outside);
		fixture.editManifest(m -> lesson(m, "demo.01.02").put("bodyPath", "demo/linked/lesson.md"));
		fixture.rejected("demo/linked/lesson.md");
	}

	@Test
	void rejectsASymlinkInsideTheContentRootToo() throws IOException {
		Files.createSymbolicLink(fixture.root.resolve("demo/resources/alias.java"),
				fixture.root.resolve("demo/resources/demo.01.01-r-solo.java"));
		fixture.editManifest(m -> resource(m, "demo.01.01-r-solo").put("localPath", "demo/resources/alias.java"));
		fixture.rejected("demo/resources/alias.java");
	}

	@Test
	void rejectsAMissingResourceFile() {
		fixture.editManifest(m -> resource(m, "demo.01.01-r-solo").put("localPath", "demo/resources/missing.java"));
		fixture.rejected("demo/resources/missing.java");
	}

	// --- sourceToId ---

	@Test
	void rejectsSourceToIdPointingToAnUnknownLesson() {
		fixture.editManifest(m -> link(m, "fase-02/01-pendiente.md").put("lessonId", "demo.09.09"));
		fixture.rejected("unknown lesson demo.09.09");
	}

	@Test
	void rejectsSourceToIdPointingToAnUnknownResource() {
		fixture.editManifest(m -> link(m, "fase-01/codigo/solo/Solo.java").put("resourceId", "demo.01.01-r-nope"));
		fixture.rejected("unknown resource demo.01.01-r-nope");
	}

	@Test
	void rejectsASourceToIdEntryWithTwoTargets() {
		fixture.editManifest(m -> link(m, "00-indice.md").put("lessonId", "demo.01.01"));
		fixture.rejected("00-indice.md must have exactly one target");
	}

	@Test
	void rejectsTwoLessonsThatShareAFileOutsideAnyFolder() {
		// Only a lesson and a stage may share one source path; two lessons sharing it is a content conflict.
		fixture.addResource("demo.01.01-r-shared", "fase-01/extra/Shared.java", "text/plain; charset=utf-8",
				"class Shared {}\n".getBytes());
		fixture.addResource("demo.01.02-r-shared", "fase-01/extra/Shared.java", "text/plain; charset=utf-8",
				"class Shared {}\n".getBytes());
		fixture.editManifest(m -> {
			resource(m, "demo.01.02-r-shared").put("lessonId", "demo.01.02");
			ArrayNode links = (ArrayNode) m.get("sourceToId");
			links.remove(links.size() - 1);
		});
		fixture.rejected("duplicate resource sourcePath fase-01/extra/Shared.java");
	}

	@Test
	void rejectsAFolderThatMissesOneOfItsFiles() {
		fixture.editManifest(m -> ((ArrayNode) link(m, "fase-01/codigo/hola/").get("resourceIds")).remove(1));
		fixture.rejected("folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	@Test
	void rejectsAFolderThatListsAFileFromElsewhere() {
		fixture.editManifest(m -> ((ArrayNode) link(m, "fase-01/codigo/hola/").get("resourceIds")).add("demo.01.01-r-solo"));
		fixture.rejected("folder fase-01/codigo/hola/ does not list exactly its resources");
	}

	// --- lesson content ---

	@Test
	void rejectsAnUnmappedRelativeLink() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [otro](03-otra.md).");
		fixture.rejected("demo.01.01: unresolved link 03-otra.md");
	}

	@Test
	void keepsOnlyTheTextOfLinksToProtectedSourcePaths() {
		fixture.replace(INTRO, "Texto de cuerpo.",
			"Texto de cuerpo con [config](codigo/.env), [ejemplo](codigo/.env.local#x) y [repo](../.git/config).");
		String html = fixture.load().lessons().get("demo.01.01").bodyHtml();
		assertThat(html).contains("config", "ejemplo", "repo").doesNotContain(".env", ".git", "<a href=\"codigo");
	}

	@Test
	void emailLinksKeepOnlyTheirText() {
		// CommonMark (and GitHub) turn <ana@example.com> into a mailto: link; the academy keeps only its text (H7).
		fixture.replace(INTRO, "Texto de cuerpo.",
			"Texto de cuerpo de Ana García <ana@example.com> y [escríbeme](mailto:Ana@Example.com).");
		String html = fixture.load().lessons().get("demo.01.01").bodyHtml();
		assertThat(html).contains("Ana García ana&#64;example.com y escríbeme").doesNotContain("mailto", "<a href=\"ana");
	}

	@ParameterizedTest
	@ValueSource(strings = { "codigo/%2Eenv", "codigo/.%65nv", "codigo%2F.env", "codigo/.env#", "./.env", "%2E%2E/.env",
		"codigo/.env/", "codigo//.env", "codigo/.env.local", ".git", "codigo/.git/config" })
	void keepsOnlyTheTextOfEncodedOrRelativeProtectedLinks(String destination) {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [protegido](" + destination + ").");
		String html = fixture.load().lessons().get("demo.01.01").bodyHtml();
		assertThat(html).contains("protegido").doesNotContain("<a href=\"" + destination, ".env", ".git", "%2E", "%65");
	}

	@ParameterizedTest
	@ValueSource(strings = { "codigo/.envrc", "codigo/env", "../../.env", "codigo/.ENV", "codigo/.GIT/config",
		"codigo/.env_x", "codigo/.gitignore", "%2E%2E/%2E%2E/.env", "codigo/.git/../missing.md", "codigo/.env?x",
		"codigo/.environment", "codigo/x.env", "codigo/.git.bak/x", "codigo/.env.x?y" })
	void stillRejectsUnresolvedLinksThatAreNotProtected(String destination) {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [x](" + destination + ").");
		fixture.rejected("demo.01.01: unresolved link " + destination);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/x/.env", "file:.env", "javascript:.env" })
	void stillDisallowsAbsoluteOrSchemeLinksToProtectedNames(String destination) {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [x](" + destination + ").");
		fixture.rejected("demo.01.01: disallowed link " + destination);
	}

	@Test
	void rejectsAResourceIdWithCombiningMarks() {
		renameSolo("demo.01.01-r-an\u0303o");
		fixture.rejected("resource id demo.01.01-r-an\u0303o contains combining marks; use composed (NFC) characters");
	}

	@ParameterizedTest
	@ValueSource(strings = { "demo.01.01-r-x\uFE0F", "demo.01.01-r-\u0301" })
	void rejectsAnyCombiningMarkInAResourceId(String id) {
		renameSolo(id);
		fixture.rejected("resource id " + id + " contains combining marks; use composed (NFC) characters");
	}

	@Test
	void acceptsAResourceIdWithComposedLetters() {
		renameSolo("demo.01.01-r-a\u00f1o");
		assertThat(fixture.load().resources()).containsKey("demo.01.01-r-a\u00f1o");
	}

	@ParameterizedTest
	@ValueSource(strings = { "codigo/.env#a?b", ".git?x", "codigo/.env?", "codigo/solo/Solo.java?x" })
	void aQueryIsNeverAProtectedTarget(String destination) {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [x](" + destination + ").");
		fixture.rejected("demo.01.01: unresolved link " + destination);
	}

	@ParameterizedTest
	@ValueSource(strings = { "demo.01.01-r-\u2126", "demo.01.01-r-\u212A", "demo.01.01-r-\u1F71", "demo.01.01-r-\uF900",
		"demo.01.01-r-\u1100\u1161" })
	void rejectsNonNfcIdsWithoutMarks(String id) {
		renameSolo(id);
		assertThat(fixture.rejected("resource id " + id + " is not in composed (NFC) form").getMessage())
			.doesNotContain("combining marks");
	}

	@Test
	void aDecomposedIdGetsOnlyTheMarksMessage() {
		renameSolo("demo.01.01-r-a\u0303");
		assertThat(fixture.rejected("contains combining marks").getMessage()).doesNotContain("is not in composed (NFC) form");
	}

	@Test
	void protectedNameMatchesSafeNames() {
		for (String path : new String[] { ".env", "a/.env.local", "a/.git", ".env.example/x/.env", "a/.env.example.bak",
				"a/.env.EXAMPLE" }) {
			assertThat(ContentUpdateComparison.protectedName(path)).isTrue();
			assertThat(assertThrows(IllegalArgumentException.class, () -> ContentUpdateComparison.safeNames(path)).getMessage())
				.isEqualTo("protected paths are not allowed");
		}
		for (String path : new String[] { "", ".envrc", ".ENV", ".gitignore", "x.env", ".env_x", ".env.example",
				"a/.env.example" }) {
			assertThat(ContentUpdateComparison.protectedName(path)).isFalse();
		}
	}

	@Test
	void rejectsAResourceIdThatIsNotInComposedForm() {
		// U+212B ANGSTROM SIGN is a letter without combining marks, but NFC turns it into U+00C5.
		renameSolo("demo.01.01-r-\u212B");
		fixture.rejected("resource id demo.01.01-r-\u212B is not in composed (NFC) form");
	}

	private void renameSolo(String id) {
		fixture.editManifest(m -> {
			ContentFixture.resource(m, "demo.01.01-r-solo").put("resourceId", id);
			ContentFixture.link(m, "fase-01/codigo/solo/Solo.java").put("resourceId", id);
			((ArrayNode) ContentFixture.link(m, "fase-01/codigo/solo/").get("resourceIds")).set(0, id);
		});
	}

	@Test
	void rejectsALinkThatClimbsAboveTheSourceRoot() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [fuera](../../00-indice.md).");
		fixture.rejected("demo.01.01: unresolved link ../../00-indice.md");
	}

	@Test
	void rejectsAnUnknownOwnFragment() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [nada](#no-existe).");
		fixture.rejected("demo.01.01: unresolved link #no-existe");
	}

	@Test
	void resolvesAFragmentToTheRemovedExercisesSection() {
		// The section is extracted into the exercise list, so its own heading has no rendered target: the reader is
		// offered the lesson entry instead of a guessed anchor, exactly like a pending document.
		fixture.editManifest(m -> ((ObjectNode) link(m, "fase-01/01-intro.md").get("fragments"))
			.put("5-ejercicios-prácticos-resueltos", "5-ejercicios-prácticos-resueltos"));
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [ejercicios](#5-ejercicios-prácticos-resueltos).");

		LessonContent intro = fixture.load().lessons().get("demo.01.01");

		assertThat(intro.bodyHtml()).contains("<a href=\"/lessons/demo.01.01\">ejercicios</a>");
	}

	@Test
	void resolvesAFragmentOnTheCourseIndexToItsEntry() {
		fixture.replace(INTRO, "[índice](../00-indice.md)", "[índice](../00-indice.md#proyecto-evolutivo)");

		LessonContent intro = fixture.load().lessons().get("demo.01.01");

		assertThat(intro.bodyHtml()).contains("<a href=\"/courses/demo\">índice</a>");
	}

	@Test
	void rejectsACrossLessonFragmentMissingFromItsFragments() {
		fixture.replace(INTRO, "02-siguiente.md#sección-b", "02-siguiente.md#sección-a");
		fixture.rejected("demo.01.01: unresolved link 02-siguiente.md#sección-a");
	}

	@Test
	void rejectsAMappedFragmentWhoseHeadingDoesNotExist() {
		fixture.editManifest(m -> ((ObjectNode) link(m, "fase-01/02-siguiente.md").get("fragments")).put("sección-b", "sección-z"));
		fixture.rejected("demo.01.01: unresolved link 02-siguiente.md#sección-b");
	}

	@Test
	void rejectsAnUnresolvedLinkInsideAStatement() {
		fixture.replace(INTRO, "Enunciado dos.", "Enunciado dos con [roto](codigo/nada/).");
		fixture.rejected("demo.01.01 demo.01.01.e02 statement: unresolved link codigo/nada/");
	}

	@Test
	void rejectsAnUnresolvedFragmentInsideASolution() {
		fixture.replace(INTRO, "Solución dos.", "Solución dos, ver [arriba](#arriba).");
		fixture.rejected("demo.01.01 demo.01.01.e02 solution: unresolved link #arriba");
	}

	@Test
	void rejectsAnExerciseRemovedFromTheManifest() {
		fixture.editManifest(m -> ((ArrayNode) lesson(m, "demo.01.01").get("exercises")).remove(1));
		fixture.rejected("demo.01.01: exercise heading «Segundo ejercicio» has no exercise with that title in the manifest");
	}

	@Test
	void reorderedExercisesKeepTheirIds() {
		String text = fixture.read(INTRO);
		int first = text.indexOf("#### Ejercicio 1 · ");
		int second = text.indexOf("#### Ejercicio 2 · ");
		int end = text.indexOf("---\n\n## 6. Chuleta");
		fixture.write(INTRO, text.substring(0, first) + text.substring(second, end) + text.substring(first, second)
				+ text.substring(end));

		LessonContent intro = fixture.load().lessons().get("demo.01.01");

		assertThat(intro.exercises()).extracting(ExerciseContent::id).containsExactly("demo.01.01.e01", "demo.01.01.e02");
		assertThat(intro.exercises()).extracting(ExerciseContent::title).containsExactly("Primer ejercicio", "Segundo ejercicio");
		assertThat(intro.exercises().get(0).statementHtml()).contains("Enunciado uno");
		assertThat(intro.exercises().get(0).solutionHtml()).contains("Solución uno");
		assertThat(intro.exercises().get(1).statementHtml()).contains("Enunciado dos");
		assertThat(intro.exercises().get(1).solutionHtml()).contains("Solución dos");
		assertThat(intro.bodyHtml()).contains("<a href=\"#demo.01.01.e02\">");
	}

	@Test
	void rejectsAManifestExerciseTitleThatDiffersFromItsHeading() {
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo.01.01").get("exercises").get(0)).put("title", "Otro"));
		fixture.rejected("demo.01.01: exercise demo.01.01.e01 «Otro» has no heading with that title in the lesson");
	}

	@Test
	void rejectsANewExerciseThatTheManifestDoesNotList() {
		fixture.replace(INTRO, "---\n\n## 6. Chuleta", "#### Ejercicio 3 · Ejercicio nuevo\n\nEnunciado tres.\n\n<details>\n"
				+ "<summary>Ver solución</summary>\n\nSolución tres.\n\n</details>\n\n---\n\n## 6. Chuleta");
		fixture.rejected("demo.01.01: exercise heading «Ejercicio nuevo» has no exercise with that title in the manifest");
	}

	@Test
	void rejectsAnExerciseHeadingThatDoesNotFollowTheFormat() throws Exception {
		for (String malformed : java.util.List.of("Ejercicio 3: Mal formado", "Ejercicio 3 - Mal formado",
				"Ejercicio tres · Mal formado", "Ejercicio 3 ·Mal formado")) {
			var fixture = ContentFixture.copyInto(java.nio.file.Files.createTempDirectory(tempDir, "malformed"));
			fixture.replace(INTRO, "---\n\n## 6. Chuleta", "#### " + malformed + "\n\nEnunciado.\n\n---\n\n## 6. Chuleta");
			fixture.rejected("demo.01.01: exercise heading «" + malformed + "» does not match «Ejercicio N · Título»");
		}
	}

	@Test
	void rejectsAManifestExerciseWhoseHeadingNoLongerExists() {
		String text = fixture.read(INTRO);
		fixture.write(INTRO, text.substring(0, text.indexOf("#### Ejercicio 1 · "))
				+ text.substring(text.indexOf("#### Ejercicio 2 · ")));
		fixture.rejected("demo.01.01: exercise demo.01.01.e01 «Primer ejercicio» has no heading with that title in the lesson");
	}

	@Test
	void rejectsAHeadingWithoutManifestEntryAndDuplicateTitles() {
		fixture.replace(INTRO, "Ejercicio 1 · Primer ejercicio", "Ejercicio 1 · Primer ejercicio renombrado");
		fixture.rejected("demo.01.01: exercise heading «Primer ejercicio renombrado» has no exercise with that title in the manifest");

		ContentFixture headings = ContentFixture.copyInto(tempDir.resolve("headings"));
		headings.replace(INTRO, "Ejercicio 2 · Segundo `ejercicio`", "Ejercicio 2 · Primer ejercicio");
		headings.rejected("demo.01.01: more than one exercise heading titled «Primer ejercicio»");

		ContentFixture entries = ContentFixture.copyInto(tempDir.resolve("entries"));
		entries.editManifest(m -> ((ObjectNode) lesson(m, "demo.01.01").get("exercises").get(1)).put("title", "Primer ejercicio"));
		entries.rejected("exercise demo.01.01.e02 of demo.01.01 repeats the title «Primer ejercicio»");
	}

	@Test
	void rejectsAHeadingThatDiffersFromItsManifestTitleOnlyInUnicodeNormalization() {
		fixture.replace(INTRO, "Ejercicio 1 · Primer ejercicio", "Ejercicio 1 · Primer ejercicio cafe\u0301");
		fixture.editManifest(m -> ((ObjectNode) lesson(m, "demo.01.01").get("exercises").get(0))
			.put("title", "Primer ejercicio caf\u00e9"));
		fixture.rejected("demo.01.01: exercise demo.01.01.e01 «Primer ejercicio caf\u00e9» has no heading with that title in the lesson");
	}

	@Test
	void rejectsDuplicatedHeadingIdsFromRawHtml() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo.\n\n<h2 id=\"qué-aprenderás\">x</h2>");
		fixture.rejected("demo.01.01: repeated id qué-aprenderás");
	}

	@Test
	void keepsTheExercisesSectionLeadInInTheLessonBody() {
		// Authored prose between the section heading and its first exercise is not assessment material: it stays
		// rendered in the lesson body. The grade heading that introduces the exercises still leaves with the section.
		fixture.replace(INTRO, "## 5. Ejercicios prácticos resueltos\n\n### 🟢 Básico",
			"## 5. Ejercicios prácticos resueltos\n\nAntes de empezar, repasa [la teoría](#qué-aprenderás).\n\n### 🟢 Básico");

		LessonContent intro = fixture.load().lessons().get("demo.01.01");

		assertThat(intro.bodyHtml()).contains("Antes de empezar, repasa <a href=\"#qué-aprenderás\">la teoría</a>")
			.doesNotContain("Ejercicios prácticos", "Básico", "Enunciado", "Solución");
		assertThat(intro.exercises()).extracting(ExerciseContent::id)
			.containsExactly("demo.01.01.e01", "demo.01.01.e02");
		assertThat(intro.exercises().get(0).statementHtml()).doesNotContain("Antes de empezar");
	}

	@Test
	void theExercisesSectionTakesItsOwnTrailingThematicBreakWithIt() {
		// The break that closes the last exercise leaves with the section, as in H4: the body keeps only its own break.
		String body = fixture.load().lessons().get("demo.01.01").bodyHtml();

		assertThat(body).doesNotContainPattern("<hr />\\s*<hr />");
		assertThat(body.split("<hr />", -1)).hasSize(2);
	}

	@Test
	void theSolutionIsTheBalancedBlockThatAnnouncesVerSolucion() {
		// The statement carries its own top-level <details> and the solution nests another block: counting open and
		// close keeps the whole solution together, and everything after it belongs to the solution instead of failing.
		fixture.replace(INTRO, "Enunciado dos.\n\n<details>\n<summary>Ver solución</summary>\n\nSolución dos.\n\n</details>",
			"Enunciado dos.\n\n<details>\n<summary>Una pista</summary>\n\nPista.\n\n</details>\n\n"
				+ "<details>\n<summary>Ver solución</summary>\n\nSolución dos.\n\n"
				+ "<details>\n<summary>Ampliar</summary>\n\nM\u00e1s detalle.\n\n</details>\n\n</details>\n\n"
				+ "Nota final de la solución.");

		ExerciseContent second = fixture.load().lessons().get("demo.01.01").exercises().get(1);

		assertThat(second.statementHtml()).contains("Pista").doesNotContain("Solución dos", "<summary>Ver solución</summary>");
		assertThat(second.solutionHtml()).startsWith("<details>")
			.contains("<summary>Ver solución</summary>", "Solución dos", "Ampliar", "M\u00e1s detalle",
				"Nota final de la solución");
	}

	@Test
	void withoutAVerSolucionSummaryTheFirstDetailsBlockStaysTheSolution() {
		fixture.replace(INTRO, "<details>\n<summary>Ver solución</summary>\n\nSolución dos.",
			"<details>\n<summary>Solución</summary>\n\nSolución dos.");

		ExerciseContent second = fixture.load().lessons().get("demo.01.01").exercises().get(1);

		assertThat(second.statementHtml()).doesNotContain("Solución dos");
		assertThat(second.solutionHtml()).startsWith("<details>").contains("<summary>Solución</summary>", "Solución dos");
	}

	@Test
	void rejectsAnExerciseWithoutSolution() {
		fixture.replace(INTRO, "Enunciado dos.\n\n<details>", "Enunciado dos.\n\n<div>");
		fixture.rejected("demo.01.01: exercise Segundo ejercicio has no <details> solution");
	}

	@Test
	void rejectsADisallowedLinkScheme() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [clic](javascript:alert(1)).");
		fixture.rejected("demo.01.01: disallowed link javascript:alert(1)");
	}

	@Test
	void rejectsAProtocolRelativeLink() {
		fixture.replace(INTRO, "Texto de cuerpo.", "Texto de cuerpo con [clic](//evil.example/x).");
		fixture.rejected("demo.01.01: disallowed link //evil.example/x");
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
				"id demo.01.01.e02 is used by more than one course", "id demo.01.01-r-hello is used by more than one course",
				"id demo.phase-01 is used by more than one course");
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
