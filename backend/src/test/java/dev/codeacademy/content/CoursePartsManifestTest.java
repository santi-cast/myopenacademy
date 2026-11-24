package dev.codeacademy.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The parts of a course in its manifest (H12, T2, S3): optional in schema 2, checked on load with the rules the
 * importer applies to {@code partes} of curso.json.
 */
class CoursePartsManifestTest {

	@TempDir Path tempDir;
	ContentFixture fixture;

	@BeforeEach
	void setup() {
		fixture = ContentFixture.copyInto(tempDir);
		upgrade(fixture);
	}

	/** The synthetic course in schema 2, with its provenance, and no parts. */
	static void upgrade(ContentFixture fixture) {
		fixture.editManifest(m -> {
			m.put("schemaVersion", 2);
			m.putArray("projectStages");
			m.get("resources").forEach(r -> ((ObjectNode) r).put("kind", "file"));
			ObjectNode provenance = m.putObject("provenance");
			ArrayNode files = provenance.putArray("files");
			provenance.putArray("generated");
			for (String array : new String[] { "lessons", "resources" }) {
				m.get(array).forEach(entry -> {
					String field = array.equals("resources") ? "localPath" : "bodyPath";
					if (entry.get(field).isNull()) return;
					String local = entry.get(field).asString();
					try {
						String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
							.digest(Files.readAllBytes(fixture.root.resolve(local))));
						files.addObject().put("sourcePath", entry.get("sourcePath").asString()).put("localPath", local)
							.put("sourceSha256", hash).put("copySha256", hash).putNull("transformation");
					}
					catch (Exception ex) { throw new IllegalStateException(ex); }
				});
			}
		});
	}

	/** Two valid parts, one per phase of the synthetic course. */
	static void addParts(ObjectNode manifest) {
		ArrayNode parts = manifest.putArray("parts");
		parts.addObject().put("id", "demo.parte.uno").put("title", "Primera parte").put("position", 1)
			.putArray("phaseIds").add("demo.phase-01");
		parts.addObject().put("id", "demo.parte.dos").put("title", "Segunda parte").put("position", 2)
			.putArray("phaseIds").add("demo.phase-02");
	}

	static ObjectNode part(ObjectNode manifest, int index) {
		return (ObjectNode) manifest.get("parts").get(index);
	}

	/** Loading fails as a whole with exactly these problems, and nothing of the copy changes. */
	void rejected(Consumer<ObjectNode> edit, String... problems) throws Exception {
		fixture.editManifest(m -> {
			addParts(m);
			edit.accept(m);
		});
		Map<String, String> before = snapshot();
		ContentValidationException ex = assertThrows(ContentValidationException.class, fixture::load);
		assertThat(ex.problems()).containsExactly(problems);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void aSchema2ManifestWithoutPartsStillLoads() {
		assertThat(fixture.read(ContentFixture.MANIFEST)).doesNotContain("\"parts\"");
		CourseContent loaded = fixture.load();
		assertThat(loaded.manifest().schemaVersion()).isEqualTo(2);
		assertThat(loaded.manifest().parts()).isNull();
	}

	@Test
	void aSchema2ManifestWithPartsLoadsThemInOrder() {
		fixture.editManifest(CoursePartsManifestTest::addParts);
		CourseContent loaded = fixture.load();
		assertThat(loaded.manifest().parts()).containsExactly(
			new Manifest.Part("demo.parte.uno", "Primera parte", 1, List.of("demo.phase-01")),
			new Manifest.Part("demo.parte.dos", "Segunda parte", 2, List.of("demo.phase-02")));
	}

	@Test
	void aPartMayListSeveralPhases() {
		fixture.editManifest(m -> {
			addParts(m);
			((ArrayNode) m.get("parts")).remove(1);
			part(m, 0).withArray("phaseIds").add("demo.phase-02");
		});
		assertThat(fixture.load().manifest().parts()).containsExactly(
			new Manifest.Part("demo.parte.uno", "Primera parte", 1, List.of("demo.phase-01", "demo.phase-02")));
	}

	@Test
	void schema1DoesNotAllowParts() {
		ContentFixture v1 = ContentFixture.copyInto(tempDir.resolve("v1"));
		v1.editManifest(CoursePartsManifestTest::addParts);
		ContentValidationException ex = assertThrows(ContentValidationException.class, v1::load);
		assertThat(ex.problems()).containsExactly("schemaVersion 1 does not allow parts");
	}

	@Test
	void aPartNeedsIdTitlePositionAndPhaseIds() throws Exception {
		for (String field : List.of("id", "title", "position", "phaseIds")) {
			fixture = ContentFixture.copyInto(tempDir.resolve("missing-" + field));
			upgrade(fixture);
			// An incomplete part is left out: its position and its phases count for no part.
			rejected(m -> part(m, 0).remove(field),
				"part " + (field.equals("id") ? "null" : "demo.parte.uno") + " needs id, title, position and phaseIds",
				"part positions must be 1..1 without gaps or repeats", "phase demo.phase-01 is in no part");
		}
	}

	@Test
	void aPartTitleIsNotBlank() throws Exception {
		rejected(m -> part(m, 0).put("title", "  "), "part demo.parte.uno needs id, title, position and phaseIds",
			"part positions must be 1..1 without gaps or repeats", "phase demo.phase-01 is in no part");
	}

	@Test
	void aPartTitleIsPlainText() throws Exception {
		rejected(m -> part(m, 0).put("title", "El **lenguaje**"),
			"part demo.parte.uno: title «El **lenguaje**» has Markdown formatting (bold or italics with *); titles are plain text");
	}

	@Test
	void aPartIdIsInTheCourseNamespaceWithThePlainForm() throws Exception {
		rejected(m -> part(m, 0).put("id", "otro.parte.uno"),
			"id otro.parte.uno is not in namespace demo (expected demo.…)");
	}

	@Test
	void aPartIdHasThePlainForm() throws Exception {
		rejected(m -> part(m, 0).put("id", "demo.Parte_uno"),
			"id demo.Parte_uno must be lower-case letters without accents, digits, hyphens and dots");
	}

	@Test
	void aPartIdIsNotUsedByAnyOtherId() throws Exception {
		rejected(m -> part(m, 0).put("id", "demo.phase-01"), "duplicate id demo.phase-01");
	}

	@Test
	void aPartIdIsNotUsedByALesson() throws Exception {
		rejected(m -> part(m, 0).put("id", "demo.01.01.e01"), "duplicate id demo.01.01.e01");
	}

	@Test
	void twoPartsCannotShareAnId() throws Exception {
		rejected(m -> part(m, 1).put("id", "demo.parte.uno"), "duplicate id demo.parte.uno");
	}

	@Test
	void partPositionsAreOneToNWithoutGaps() throws Exception {
		rejected(m -> part(m, 1).put("position", 3), "part positions must be 1..2 without gaps or repeats");
	}

	@Test
	void aPartListsOnlyExistingPhases() throws Exception {
		rejected(m -> part(m, 1).withArray("phaseIds").add("demo.phase-09"),
			"part demo.parte.dos: unknown phase demo.phase-09");
	}

	@Test
	void aPhaseGoesInOnlyOnePart() throws Exception {
		rejected(m -> part(m, 1).withArray("phaseIds").add("demo.phase-01"),
			"part demo.parte.dos: phase demo.phase-01 is already in part demo.parte.uno");
	}

	@Test
	void withPartsEveryPhaseIsInOne() throws Exception {
		rejected(m -> {
			((ArrayNode) m.get("parts")).remove(1);
		}, "phase demo.phase-02 is in no part");
	}

	@Test
	void anEmptyListOfPartsLeavesEveryPhaseOut() throws Exception {
		rejected(m -> m.putArray("parts"), "phase demo.phase-01 is in no part", "phase demo.phase-02 is in no part");
	}

	@Test
	void anUnknownFieldInAPartIsRejectedLikeAnyOtherOfTheManifest() throws Exception {
		fixture.editManifest(m -> {
			addParts(m);
			part(m, 0).put("color", "azul");
		});
		ContentValidationException ex = assertThrows(ContentValidationException.class, fixture::load);
		assertThat(ex.getMessage()).contains("Unrecognized property \"color\"");
	}

	@Test
	void partIdsAreUniqueAcrossInstalledCourses() throws Exception {
		fixture.editManifest(CoursePartsManifestTest::addParts);
		ContentFixture other = ContentFixture.copyInto(tempDir.resolve("other"));
		upgrade(other);
		other.editManifest(CoursePartsManifestTest::addParts);
		// The second course is the same synthetic course under another directory: every ID repeats, parts included.
		Files.move(other.root.resolve("demo"), fixture.root.resolve("demo-copia"));
		ContentValidationException ex = assertThrows(ContentValidationException.class,
			() -> ContentLoader.loadAll(fixture.root));
		assertThat(ex.problems()).contains("id demo.parte.uno is used by more than one course");
	}

	// --- parts as stretches (H12, T14, S16): the cases of Partes in the kit's test_formato_academia.py (44a8e32) ---

	static final String F1 = "demo.phase-01", F2 = "demo.phase-02", F3 = "demo.phase-03";

	@Test
	void kitStretchesThatFollowEachOtherInOrderAreValid() {
		fixture.editManifest(m -> kitParts(m, "a", List.of(F1, F2), "b", List.of(F3)));
		assertThat(fixture.load().manifest().parts()).containsExactly(
			new Manifest.Part("demo.parte.a", "Parte a", 1, List.of(F1, F2)),
			new Manifest.Part("demo.parte.b", "Parte b", 2, List.of(F3)));
	}

	@Test
	void kitOnePartWithEveryPhaseIsValid() {
		fixture.editManifest(m -> kitParts(m, "a", List.of(F1, F2, F3)));
		assertThat(fixture.load().manifest().parts()).containsExactly(
			new Manifest.Part("demo.parte.a", "Parte a", 1, List.of(F1, F2, F3)));
	}

	@Test
	void kitAPartWithoutPhasesIsRejected() throws Exception {
		kitRejected("a", List.of(F1, F2, F3), "b", List.of(),
			"part demo.parte.b has no phases (each part groups at least one)");
	}

	@Test
	void kitPhasesOutOfTheCourseOrderAreRejectedAndNotAlsoAsNotConsecutive() throws Exception {
		kitRejected("a", List.of(F3, F1), "b", List.of(F2),
			"part demo.parte.a: its phases are not in the course order (phase " + F3 + " is listed before phase " + F1 + ")");
	}

	@Test
	void kitPhasesThatAreNotConsecutiveNameThePhaseBetweenAndItsPart() throws Exception {
		kitRejected("a", List.of(F1, F3), "b", List.of(F2), "part demo.parte.a: its phases are not consecutive (between"
			+ " phase " + F1 + " and phase " + F3 + " is phase " + F2 + ", of part demo.parte.b)");
	}

	@Test
	void kitThePhaseBetweenInNoPartLeavesItsPartOut() throws Exception {
		kitRejected("a", List.of(F1, F3), "phase " + F2 + " is in no part",
			"part demo.parte.a: its phases are not consecutive (between phase " + F1 + " and phase " + F3 + " is phase "
				+ F2 + ")");
	}

	@Test
	void kitPartsInReverseOrderAreRejected() throws Exception {
		kitRejected("b", List.of(F3), "a", List.of(F1, F2),
			"part demo.parte.b comes before part demo.parte.a in parts, but its phases come after");
	}

	@Test
	void kitTheOrderOfPartsSkipsAPartWithoutPhases() throws Exception {
		// The previous part with phases is c, not the empty b.
		kitRejected("c", List.of(F3), "b", List.of(), "a", List.of(F1, F2),
			"part demo.parte.b has no phases (each part groups at least one)",
			"part demo.parte.c comes before part demo.parte.a in parts, but its phases come after");
	}

	@Test
	void kitTheOrderOfPartsFollowsTheirPositionsNotTheirPlaceInTheList() {
		// The manifest orders parts by position: b listed first but at position 2 follows a.
		fixture.editManifest(m -> {
			kitParts(m, "b", List.of(F3), "a", List.of(F1, F2));
			part(m, 0).put("position", 2);
			part(m, 1).put("position", 1);
		});
		assertThat(fixture.load().manifest().parts()).hasSize(2);
	}

	@Test
	void kitAPhaseInNoPart() throws Exception {
		kitRejected("a", List.of(F1, F2), "phase " + F3 + " is in no part");
	}

	@Test
	void kitAPhaseInTwoPartsIsTheSecondPartsProblemOnly() throws Exception {
		kitRejected("a", List.of(F1, F2), "b", List.of(F2, F3),
			"part demo.parte.b: phase " + F2 + " is already in part demo.parte.a");
	}

	/**
	 * The kit's three phases: a third phase with one pending lesson after the two of the synthetic course, and parts
	 * demo.parte.LETTER at positions 1..n with these phases, given as letter, phases, letter, phases…
	 */
	static void kitParts(ObjectNode manifest, Object... letterAndPhases) {
		((ArrayNode) manifest.get("phases")).addObject().put("id", F3).put("title", "Tercera fase").put("position", 3);
		ObjectNode lesson = ((ArrayNode) manifest.get("lessons")).addObject().put("id", "demo.03.01")
			.put("title", "Pendiente tres").put("position", 4).put("phaseId", F3)
			.put("sourcePath", "fase-03/01-pendiente.md").put("sourceAvailable", false).put("available", false);
		lesson.putNull("bodyPath");
		lesson.putArray("exercises");
		((ArrayNode) manifest.get("sourceToId")).addObject().put("sourcePath", "fase-03/01-pendiente.md")
			.put("lessonId", "demo.03.01").putObject("fragments");
		ArrayNode parts = manifest.putArray("parts");
		for (int i = 0; i < letterAndPhases.length; i += 2) {
			ArrayNode phaseIds = parts.addObject().put("id", "demo.parte." + letterAndPhases[i])
				.put("title", "Parte " + letterAndPhases[i]).put("position", i / 2 + 1).putArray("phaseIds");
			for (Object phase : (List<?>) letterAndPhases[i + 1]) phaseIds.add((String) phase);
		}
	}

	/** Loading the kit's parts fails as a whole with exactly these problems (the trailing strings). */
	void kitRejected(Object... partsThenProblems) throws Exception {
		int split = 0;
		while (split + 1 < partsThenProblems.length && partsThenProblems[split + 1] instanceof List) split += 2;
		Object[] parts = java.util.Arrays.copyOfRange(partsThenProblems, 0, split);
		String[] problems = java.util.Arrays.stream(partsThenProblems, split, partsThenProblems.length)
			.map(String.class::cast).toArray(String[]::new);
		fixture.editManifest(m -> kitParts(m, parts));
		Map<String, String> before = snapshot();
		ContentValidationException ex = assertThrows(ContentValidationException.class, fixture::load);
		assertThat(ex.problems()).containsExactly(problems);
		assertThat(snapshot()).isEqualTo(before);
	}

	Map<String, String> snapshot() throws Exception {
		Map<String, String> result = new LinkedHashMap<>();
		try (var paths = Files.walk(fixture.root)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				result.put(fixture.root.relativize(path).toString(), HexFormat.of().formatHex(Files.readAllBytes(path)));
			}
		}
		return result;
	}
}
