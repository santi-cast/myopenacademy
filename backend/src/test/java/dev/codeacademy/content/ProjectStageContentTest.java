package dev.codeacademy.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectStageContentTest {

	@TempDir Path tempDir;
	ContentFixture fixture;

	@BeforeEach
	void setup() throws Exception {
		fixture = ContentFixture.copyInto(tempDir);
		fixture.write("demo/lessons/stage.md", "# Inicio\n\n## Ejercicios prácticos resueltos\n\n"
				+ "### Ejercicio sin extracción\n\n~~Manual~~ [lesson](../fase-01/01-intro.md#ejercicio-2--segundo-ejercicio)\n\n"
				+ "[pending](next.md#unknown) [self](#inicio)\n\n![image](image.png)\n");
		fixture.addResource("demo.stage-image", "project/image.png", "image/png", new byte[] { 1, 2, 3 });
		fixture.editManifest(m -> {
			m.put("schemaVersion", 2);
			ArrayNode stages = m.putArray("projectStages");
			stages.addObject().put("id", "demo.stage-first").put("title", "First").put("position", 1)
				.put("sourcePath", "project/first.md").put("sourceAvailable", true).put("available", true)
				.put("bodyPath", "demo/lessons/stage.md");
			stages.addObject().put("id", "demo.stage-next").put("title", "Next").put("position", 2)
				.put("sourcePath", "project/next.md").put("sourceAvailable", false).put("available", false).putNull("bodyPath");
			ObjectNode image = ContentFixture.resource(m, "demo.stage-image");
			image.remove("lessonId");
			image.put("stageId", "demo.stage-first");
			m.get("resources").forEach(r -> ((ObjectNode) r).put("kind", "file"));
			ArrayNode links = (ArrayNode) m.get("sourceToId");
			links.addObject().put("sourcePath", "project/first.md").put("stageId", "demo.stage-first")
				.putObject("fragments").put("inicio", "inicio");
			links.addObject().put("sourcePath", "project/next.md").put("stageId", "demo.stage-next");
		});
		refreshProvenance();
	}

	void refreshProvenance() {
		fixture.editManifest(m -> {
			ObjectNode provenance = m.putObject("provenance");
			ArrayNode files = provenance.putArray("files");
			provenance.putArray("generated");
			for (String array : new String[] { "lessons", "projectStages", "resources", "documents" }) {
				if (!m.has(array)) continue;
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

	boolean glossaryAdded;

	/** H8, T3: a glossary as a document without progress, with its own page and the anchors of its terms. */
	void addGlossary(String kind, String phaseId) {
		if (!glossaryAdded) {
			fixture.write("demo/lessons/glosario.md", "# Glosario\n\n## A\n\n### API\n\nUna definición.\n");
			fixture.replace("demo/lessons/demo.01.01.md", "Texto de cuerpo.", "Texto de cuerpo con la [API](../glosario.md#api).");
			glossaryAdded = true;
		}
		fixture.editManifest(m -> {
			ArrayNode links = (ArrayNode) m.get("sourceToId");
			for (int i = links.size() - 1; i >= 0; i--) {
				if ("glosario.md".equals(links.get(i).path("sourcePath").asString())) links.remove(i);
			}
			ObjectNode document = m.putArray("documents").addObject().put("id", "demo.glosario").put("kind", kind)
				.put("title", "Glosario").put("sourcePath", "glosario.md").put("bodyPath", "demo/lessons/glosario.md");
			if (phaseId == null) document.putNull("phaseId");
			else document.put("phaseId", phaseId);
			((ArrayNode) m.get("sourceToId")).addObject().put("sourcePath", "glosario.md").put("documentId", "demo.glosario")
				.putObject("fragments").put("api", "api");
		});
		refreshProvenance();
	}

	@Test
	void aGlossaryIsADocumentWithItsPageAndTheAnchorsOfItsTerms() {
		addGlossary("glosario", null);
		CourseContent loaded = fixture.load();
		assertThat(loaded.documents()).containsOnlyKeys("demo.glosario");
		assertThat(loaded.documents().get("demo.glosario").bodyHtml()).contains("<h3 id=\"api\">API</h3>");
		assertThat(loaded.lessons().get("demo.01.01").bodyHtml()).contains("<a href=\"/documents/demo.glosario#api\">API</a>");
	}

	@Test
	void aDocumentNeedsAKnownKindAndChallengesNeedTheirPhase() {
		addGlossary("otro", null);
		fixture.rejected("document demo.glosario: unsupported kind otro");
		addGlossary("retos", null);
		fixture.rejected("document demo.glosario: unknown phase null");
		addGlossary("glosario", "demo.phase-01");
		fixture.rejected("document demo.glosario: a glossary has no phase");
	}

	@Test
	void loadsV2WithoutChangingLessonOutput() {
		CourseContent baseline = ContentFixture.copyInto(tempDir.resolve("baseline")).load();
		CourseContent loaded = fixture.load();
		assertThat(loaded.lessons()).isEqualTo(baseline.lessons());
		assertThat(loaded.manifest().lessons()).hasSize(3);
		assertThat(loaded.stages()).containsOnlyKeys("demo.stage-first");
		StageContent stage = loaded.stages().get("demo.stage-first");
		assertThat(stage.stageId()).isEqualTo("demo.stage-first");
		assertThat(stage.bodyHtml()).contains("Ejercicios prácticos resueltos", "Ejercicio sin extracción", "<del>Manual</del>",
			"href=\"/lessons/demo.01.01#demo.01.01.e02\"", "href=\"/stages/demo.stage-next\"", "href=\"#inicio\"",
			"<img src=\"/api/resources/demo.stage-image\" alt=\"image\"");
		assertThat(stage.resourceIds()).containsExactly("demo.stage-image");
		assertThat(loaded.manifest().resources().stream().filter(r -> r.resourceId().equals("demo.stage-image"))
			.findFirst().orElseThrow().stageId()).isEqualTo("demo.stage-first");
	}

	@Test
	void lessonAndExerciseLinksCanTargetStageFinalFragments() {
		fixture.replace(ContentFixture.INTRO, "Texto de cuerpo.", "[stage](../project/first.md#inicio)");
		fixture.replace(ContentFixture.INTRO, "Enunciado dos.", "[stage](../project/first.md#inicio)");
		fixture.replace(ContentFixture.INTRO, "Solución dos.", "[pending](../project/next.md#missing)");
		refreshProvenance();
		LessonContent intro = fixture.load().lessons().get("demo.01.01");
		assertThat(intro.bodyHtml()).contains("href=\"/stages/demo.stage-first#inicio\"");
		assertThat(intro.exercises().get(1).statementHtml()).contains("href=\"/stages/demo.stage-first#inicio\"");
		assertThat(intro.exercises().get(1).solutionHtml()).contains("href=\"/stages/demo.stage-next\"");
	}

	@ParameterizedTest
	@ValueSource(strings = { "[broken](#missing)", "![remote](https://example.org/x.png)",
		"<img src=\"/api/resources/demo.stage-image\">", "[escape](../../outside.md)",
		"[query](first.md?x=1)", "[script](javascript:alert(1))" })
	void stageUsesExistingSanitizerAndResolver(String markdown) throws Exception {
		fixture.write("demo/lessons/stage.md", "# Inicio\n\n" + markdown + "\n");
		refreshProvenance();
		Map<String, String> before = snapshot();
		assertThrows(ContentValidationException.class, fixture::load);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void rejectsMissingFinalStageFragmentFromLesson() throws Exception {
		fixture.replace(ContentFixture.INTRO, "Texto de cuerpo.", "[stage](../project/first.md#missing)");
		refreshProvenance();
		Map<String, String> before = snapshot();
		fixture.rejected("unresolved link ../project/first.md#missing");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void changedControlledBytesFailCopyHashVerification() throws Exception {
		fixture.write("demo/lessons/stage.md", "# Changed after import\n");
		Map<String, String> before = snapshot();
		fixture.rejected("copySha256 mismatch: demo/lessons/stage.md");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void stageBodyUsesCanonicalContainment() throws Exception {
		Path original = fixture.root.resolve("demo/lessons/stage.md");
		Files.createSymbolicLink(fixture.root.resolve("demo/lessons/alias.md"), original);
		fixture.editManifest(m -> ((ObjectNode) m.get("projectStages").get(0))
			.put("bodyPath", "demo/lessons/alias.md"));
		fixture.rejected("goes through a symbolic link");
	}

	@Test
	void stagesHaveIndependentPositionSpaceAndMayBeEmpty() {
		fixture.editManifest(m -> {
			m.putArray("projectStages");
			((ArrayNode) m.get("resources")).remove(3);
			ArrayNode links = (ArrayNode) m.get("sourceToId");
			for (int i = links.size() - 1; i >= 0; i--) {
				if (links.get(i).get("sourcePath").asString().startsWith("project/")) links.remove(i);
			}
		});
		refreshProvenance();
		CourseContent loaded = fixture.load();
		assertThat(loaded.stages()).isEmpty();
		assertThat(loaded.manifest().lessons()).hasSize(3);
	}

	@ParameterizedTest
	@ValueSource(strings = { "missingStages", "missingProvenance", "missingGenerated", "stageGap", "duplicateId",
			"pendingBody", "unwrittenBody", "twoOwners", "unknownOwner", "missingKind", "zip", "unknownKind",
			"badCopyHash", "badSourceHash", "missingCopy", "duplicateCopy", "wrongSource", "unsafeSource",
			"literalMismatch", "unknownTransformation", "v1Stages", "unknownField", "noOwner", "stageZero",
			"repeatedStagePosition", "missingStageMapping", "duplicateSource", "generated", "missingLocalPath" })
	void rejectsInvalidV2WithoutChangingControlledFiles(String rule) throws Exception {
		fixture.editManifest(m -> {
			ObjectNode stage = (ObjectNode) m.get("projectStages").get(0);
			ObjectNode resource = ContentFixture.resource(m, "demo.stage-image");
			ObjectNode provenance = (ObjectNode) m.get("provenance");
			ArrayNode files = (ArrayNode) provenance.get("files");
			ObjectNode file = (ObjectNode) files.get(0);
			switch (rule) {
				case "missingStages" -> m.remove("projectStages");
				case "missingProvenance" -> m.remove("provenance");
				case "missingGenerated" -> provenance.remove("generated");
				case "stageGap" -> stage.put("position", 3);
				case "duplicateId" -> stage.put("id", "demo.01.01");
				case "pendingBody" -> stage.put("available", false);
				case "unwrittenBody" -> stage.put("sourceAvailable", false);
				case "twoOwners" -> resource.put("lessonId", "demo.01.01");
				case "unknownOwner" -> resource.put("stageId", "demo.stage-unknown");
				case "missingKind" -> resource.remove("kind");
				case "zip" -> resource.put("kind", "zip");
				case "unknownKind" -> resource.put("kind", "unknown");
				case "badCopyHash" -> file.put("copySha256", "0".repeat(64));
				case "badSourceHash" -> file.put("sourceSha256", "no-hash");
				case "missingCopy" -> files.remove(0);
				case "duplicateCopy" -> files.add(file.deepCopy());
				case "wrongSource" -> file.put("sourcePath", "wrong.md");
				case "unsafeSource" -> file.put("sourcePath", "../outside.md");
				case "literalMismatch" -> file.put("sourceSha256", "0".repeat(64));
				case "unknownTransformation" -> file.put("transformation", "invented-v1");
				case "v1Stages" -> m.put("schemaVersion", 1);
				case "unknownField" -> stage.put("exercises", "not allowed");
				case "noOwner" -> resource.remove("stageId");
				case "stageZero" -> stage.put("position", 0);
				case "repeatedStagePosition" -> stage.put("position", 2);
				case "missingStageMapping" -> ((ArrayNode) m.get("sourceToId")).remove(10);
				case "duplicateSource" -> stage.put("sourcePath", "fase-01/01-intro.md");
				case "generated" -> ((ArrayNode) provenance.get("generated")).addObject().put("localPath", "generated.zip");
				case "missingLocalPath" -> resource.remove("localPath");
				default -> throw new AssertionError(rule);
			}
		});
		String expected = switch (rule) {
			case "missingStages" -> "needs projectStages";
			case "missingProvenance", "missingGenerated" -> "needs provenance.files and provenance.generated";
			case "stageGap", "stageZero", "repeatedStagePosition" -> "stage positions must be 1..2";
			case "duplicateId" -> "duplicate id demo.01.01";
			case "pendingBody" -> "demo.stage-first is pending but has bodyPath";
			case "unwrittenBody" -> "demo.stage-first is available but not sourceAvailable";
			case "twoOwners", "noOwner" -> "must have exactly one owner";
			case "unknownOwner" -> "unknown stage demo.stage-unknown";
			case "missingKind", "unknownKind" -> "unsupported kind";
			case "zip" -> "invalid ZIP metadata";
			case "badCopyHash", "literalMismatch" -> "literal copy has different source and copy hashes";
			case "badSourceHash" -> "provenance needs SHA-256 hashes";
			case "missingCopy" -> "provenance.files must cover exactly the managed copies";
			case "duplicateCopy" -> "duplicate provenance localPath";
			case "wrongSource", "unsafeSource" -> "provenance path does not match managed copy";
			case "unknownTransformation" -> "unsupported transformation invented-v1";
			case "v1Stages" -> "schemaVersion 1 does not allow";
			case "unknownField" -> "Unrecognized property \"exercises\"";
			case "missingStageMapping" -> "missing sourceToId for demo.stage-first";
			case "duplicateSource" -> "duplicate sourcePath fase-01/01-intro.md";
			case "generated" -> "generated provenance must match ZIP inputs and SHA-256";
			case "missingLocalPath" -> "localPath of demo.stage-image: is missing";
			default -> throw new AssertionError(rule);
		};
		Map<String, String> before = snapshot();
		fixture.rejected(expected);
		assertThat(snapshot()).isEqualTo(before);
	}

	@ParameterizedTest
	@ValueSource(strings = { "projectStages", "provenance", "kind", "stageId" })
	void v1RejectsNewFieldsEvenWhenNull(String field) {
		ContentFixture v1 = ContentFixture.copyInto(tempDir.resolve("v1"));
		v1.editManifest(m -> {
			if (field.equals("kind") || field.equals("stageId")) {
				((ObjectNode) m.get("resources").get(0)).putNull(field);
			}
			else m.putNull(field);
		});
		v1.rejected("schemaVersion 1 does not allow");
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
