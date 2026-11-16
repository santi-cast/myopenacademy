package dev.codeacademy.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import dev.codeacademy.content.ExampleContent;

/** H7, T6: a recommended course that is installed carries its title; one that is not keeps only its ID. */
class CatalogRecommendationsTest {

	@TempDir Path temp;

	@Test
	void anInstalledRecommendationCarriesItsTitleAndAMissingOneOnlyItsId() throws Exception {
		Path root = ExampleContent.copyInto(temp.resolve("content"));
		Path manifest = root.resolve("ejemplo-destino/manifest.json");
		JsonMapper json = JsonMapper.builder().build();
		ObjectNode data = (ObjectNode) json.readTree(manifest.toFile());
		data.withObject("course").putArray("recommended").add("ejemplo").add("inicial");
		Files.writeString(manifest, json.writeValueAsString(data));

		CatalogController controller = new CatalogController(Catalog.load(root));
		CatalogController.CourseView destino = controller.courses().courses().stream()
			.filter(course -> course.id().equals("ejemplo-destino")).findFirst().orElseThrow();
		assertThat(destino.description())
			.isEqualTo("Un curso pequeño al que enlaza el curso de ejemplo, para las pruebas del kit y de la academia.");
		assertThat(destino.recommended()).containsExactly(
			new CatalogController.RecommendedView("ejemplo", "Curso de ejemplo"),
			new CatalogController.RecommendedView("inicial", null));
	}
}
