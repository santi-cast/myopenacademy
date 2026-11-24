package dev.codeacademy.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.content.ContentFixture;
import dev.codeacademy.content.ExampleContent;

/**
 * H12, T3 (S4, S1): the syllabus of a course carries its parts, in order, from the loaded content; a course without
 * parts gets an empty list and the rest of its syllabus as before.
 */
class CatalogPartsTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@TempDir Path temp;

	@Test
	void aCourseWithPartsListsThemByPosition() throws Exception {
		ContentFixture fixture = schema2(ContentFixture.copyInto(temp));
		fixture.editManifest(m -> {
			// Written out of order: the syllabus orders them by position.
			ArrayNode parts = m.putArray("parts");
			parts.addObject().put("id", "demo.parte.dos").put("title", "Segunda parte").put("position", 2)
				.putArray("phaseIds").add("demo.phase-02");
			parts.addObject().put("id", "demo.parte.uno").put("title", "Primera parte").put("position", 1)
				.putArray("phaseIds").add("demo.phase-01");
		});

		JsonNode syllabus = syllabus(fixture.root, "demo");

		assertThat(syllabus.propertyNames()).containsExactlyInAnyOrder("course", "phases", "lessons", "documents",
			"project", "parts");
		assertThat(JSON.writeValueAsString(syllabus.get("parts"))).isEqualTo("["
			+ "{\"id\":\"demo.parte.uno\",\"title\":\"Primera parte\",\"position\":1,\"phaseIds\":[\"demo.phase-01\"]},"
			+ "{\"id\":\"demo.parte.dos\",\"title\":\"Segunda parte\",\"position\":2,\"phaseIds\":[\"demo.phase-02\"]}]");
	}

	@Test
	void aPartMayListSeveralPhasesInItsOrder() throws Exception {
		ContentFixture fixture = schema2(ContentFixture.copyInto(temp));
		fixture.editManifest(m -> m.putArray("parts").addObject().put("id", "demo.parte.todo").put("title", "Todo")
			.put("position", 1).putArray("phaseIds").add("demo.phase-01").add("demo.phase-02"));

		assertThat(JSON.writeValueAsString(syllabus(fixture.root, "demo").get("parts"))).isEqualTo("["
			+ "{\"id\":\"demo.parte.todo\",\"title\":\"Todo\",\"position\":1,"
			+ "\"phaseIds\":[\"demo.phase-01\",\"demo.phase-02\"]}]");
	}

	@Test
	void aCourseWithoutPartsHasAnEmptyListAndTheRestAsBefore() throws Exception {
		ContentFixture fixture = ContentFixture.copyInto(temp);

		JsonNode syllabus = syllabus(fixture.root, "demo");

		assertThat(syllabus.propertyNames()).containsExactlyInAnyOrder("course", "phases", "lessons", "documents",
			"project", "parts");
		assertThat(syllabus.get("parts").isArray()).isTrue();
		assertThat(syllabus.get("parts")).isEmpty();
		assertThat(JSON.writeValueAsString(syllabus.get("phases"))).isEqualTo("["
			+ "{\"id\":\"demo.phase-01\",\"title\":\"Primera fase\",\"position\":1},"
			+ "{\"id\":\"demo.phase-02\",\"title\":\"Segunda fase\",\"position\":2}]");
	}

	/** The kit's example courses: «ejemplo» has one part since the kit added them, «ejemplo-destino» none. */
	@Test
	void theExampleCoursesCarryWhatTheirCursoJsonSays() throws Exception {
		assertThat(JSON.writeValueAsString(syllabus(ExampleContent.root(), ExampleContent.COURSE).get("parts")))
			.isEqualTo("[{\"id\":\"ejemplo.parte.basico\",\"title\":\"Lo básico\",\"position\":1,"
				+ "\"phaseIds\":[\"ejemplo.fase.primeros-pasos\"]}]");
		assertThat(JSON.writeValueAsString(syllabus(ExampleContent.root(), ExampleContent.DESTINATION).get("parts")))
			.isEqualTo("[]");
	}

	private static JsonNode syllabus(Path root, String courseId) {
		CatalogController controller = new CatalogController(Catalog.load(root), "");
		return JSON.readTree(JSON.writeValueAsString(controller.course(courseId)));
	}

	/** The synthetic course moved to schema 2 (the one that allows parts), with the provenance it then needs. */
	private static ContentFixture schema2(ContentFixture fixture) {
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
		return fixture;
	}
}
