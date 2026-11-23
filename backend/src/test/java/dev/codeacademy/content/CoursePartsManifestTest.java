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
