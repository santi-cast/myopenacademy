package dev.codeacademy.content;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Image provenance and GFM are checked through the public loader, after exercise extraction and sanitizing. */
class MarkdownImagesTest {

	private static final List<String> PARTS = List.of("body", "statement", "solution");

	@TempDir Path tempDir;
	ContentFixture fixture;

	@BeforeEach
	void copy() {
		fixture = ContentFixture.copyInto(tempDir);
		for (String type : List.of("png", "jpeg", "svg")) ImageFixture.register(fixture, type);
	}

	static Stream<Arguments> images() {
		return PARTS.stream().flatMap(part -> Stream.of("png", "jpeg", "svg")
			.map(type -> Arguments.of(part, type)));
	}

	@ParameterizedTest(name = "registered {1} in {0}")
	@MethodSource("images")
	void rewritesRegisteredImagesInEveryHtmlField(String part, String type) {
		insert(part, "![Diagram & \"quote\" &lt;tag&gt;](images/diagram." + type + ")");

		LessonContent lesson = fixture.load().lessons().get("demo.01.01");

		assertThat(field(lesson, part)).contains("<img src=\"/api/resources/demo.image-" + type
			+ "\" alt=\"Diagram &amp; &#34;quote&#34; &lt;tag&gt;\" />")
			.doesNotContain("images/diagram", "localPath", "onerror=", "title=", "srcset=");
		assertThat(lesson.resourceIds()).containsOnlyOnce("demo.image-" + type);
		for (String other : PARTS) {
			if (!other.equals(part)) assertThat(field(lesson, other)).doesNotContain("<img");
		}
	}

	static Stream<Arguments> rejectedImages() {
		return PARTS.stream().flatMap(part -> Stream.of(
			"https://evil.example/image.png", "http://evil.example/image.png", "//evil.example/image.png",
			"data:image/png;base64,AAAA", "javascript:alert(1)", "file:///tmp/image.png",
			"/api/resources/demo.image-png", "%2Fapi%2Fresources%2Fdemo-image-png",
			"/tmp/image.png", "images/missing.png", "../../images/diagram.png",
			"%2e%2e/%2e%2e/images/diagram.png", "images/diagram.png?download=1", "images/diagram.png#fragment",
			"codigo/solo/Solo.java", "codigo/hola/", "02-siguiente.md", "#repetida")
			.map(destination -> Arguments.of(part, destination)));
	}

	@ParameterizedTest(name = "reject {1} in {0}")
	@MethodSource("rejectedImages")
	void rejectsImagesWithoutRegisteredImageResolution(String part, String destination) {
		insert(part, "![hostile](" + destination + ")");

		// Loading throws instead of exposing a partial course or a silently dropped image.
		fixture.rejected(label(part));
	}

	@ParameterizedTest
	@ValueSource(strings = {"body", "statement", "solution"})
	void rawHtmlCannotBorrowTheUrlOfAResolvedMarkdownImage(String part) {
		insert(part, "![valid](images/diagram.png)\n\n<img src=\"/api/resources/demo.image-png\" alt=\"forged\">");

		fixture.rejected(label(part));
	}

	@ParameterizedTest
	@ValueSource(strings = {"body", "statement", "solution"})
	void rawHtmlCannotBorrowARegisteredResourceLink(String part) {
		insert(part, "[download](images/diagram.png)\n\n<img src=\"/api/resources/demo.image-png\" alt=\"forged\">");

		fixture.rejected(label(part));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"<img src=\"images/diagram.png\" alt=\"raw\">",
		"<img src=\"/api/resources/demo.image-png\" alt=\"raw\" onerror=\"alert(1)\">",
		"<img src=\"/api/resources/demo.image-png\" alt=\"raw\" srcset=\"https://evil.example/x.png 2x\">",
		"<svg><image href=\"/api/resources/demo.image-svg\"/></svg>",
		"<object data=\"/api/resources/demo.image-svg\"></object>",
		"![title](images/diagram.png \"not an allowed attribute\")"
	})
	void rejectsImageAttributesAndEmbeddingOutsideTheAllowList(String snippet) {
		insert("body", "![valid](images/diagram.png)\n\n" + snippet);

		fixture.rejected("demo.01.01");
	}

	@Test
	void resolvesEncodedRelativeImagePathsFromTheSourceDocument() {
		fixture.addResource("demo.image-space", "assets/diagram one.png", "image/png", ImageFixture.bytes("png"));
		insert("body", "![](../assets/diagram%20one.png)");

		LessonContent lesson = fixture.load().lessons().get("demo.01.01");

		assertThat(lesson.bodyHtml()).contains("<img src=\"/api/resources/demo.image-space\" alt=\"\" />")
			.doesNotContain("../assets", "demo/resources");
		assertThat(lesson.resourceIds()).containsOnlyOnce("demo.image-space");
	}

	@ParameterizedTest
	@ValueSource(strings = {"image/gif", "text/html", "application/octet-stream"})
	void rejectsRegisteredFilesOutsideTheImageMediaTypeAllowList(String mediaType) {
		fixture.editManifest(m -> ContentFixture.resource(m, "demo.image-png").put("mediaType", mediaType));
		insert("body", "![wrong type](images/diagram.png)");

		fixture.rejected("demo.01.01");
	}

	@Test
	void rejectsARegisteredImageWithoutItsSourceMapping() {
		fixture.editManifest(m -> {
			var links = (tools.jackson.databind.node.ArrayNode) m.get("sourceToId");
			for (int i = links.size() - 1; i >= 0; i--) {
				if (links.get(i).get("sourcePath").asString().equals("fase-01/images/diagram.png")) links.remove(i);
			}
		});
		insert("body", "![unmapped](images/diagram.png)");

		fixture.rejected("demo.01.01");
	}

	@Test
	void rejectsImageResourcesReachedThroughASymlink() throws Exception {
		Path outside = tempDir.resolve("outside.png");
		Files.write(outside, ImageFixture.bytes("png"));
		Files.createSymbolicLink(fixture.root.resolve("demo/resources/alias.png"), outside);
		fixture.editManifest(m -> ContentFixture.resource(m, "demo.image-png")
			.put("localPath", "demo/resources/alias.png"));
		insert("body", "![alias](images/diagram.png)");

		fixture.rejected("demo/resources/alias.png");
	}

	@ParameterizedTest
	@ValueSource(strings = {"body", "statement", "solution"})
	void rendersGfmStrikethroughInEveryHtmlField(String part) {
		insert(part, "~~obsolete **bold**~~ and `~~literal~~`");

		assertThat(field(fixture.load().lessons().get("demo.01.01"), part))
			.contains("<del>obsolete <strong>bold</strong></del> and <code>~~literal~~</code>");
	}

	@Test
	void preservesLinksFolderExpansionHeadingIdsTablesAndMermaid() {
		insert("body", "| Feature | Value |\n| --- | --- |\n| *plain* | **bold** |");

		LessonContent lesson = fixture.load().lessons().get("demo.01.01");

		assertThat(lesson.bodyHtml()).contains(
			"<h2 id=\"repetida-1\">Repetida</h2>",
			"<a href=\"/lessons/demo.01.02#sección-b\">la siguiente lección</a>",
			"<a href=\"#demo.01.01.e02\">el ejercicio 2</a>",
			"<code>codigo/hola/</code> (<a href=\"/api/resources/demo.01.01-r-hello\">Hello.java</a>, "
				+ "<a href=\"/api/resources/demo.01.01-r-other\">Other.java</a>)",
			"<a href=\"https://dev.java/learn/\" target=\"_blank\" rel=\"noopener noreferrer\">la web</a>",
			"<table>", "<em>plain</em>", "<strong>bold</strong>",
			"<code class=\"language-mermaid\">graph TD", "&lt;br/&gt;", "--&gt; B")
			.doesNotContain("Ejercicios prácticos resueltos", "<svg");
		assertThat(lesson.exercises().get(0).statementHtml()).contains("/api/resources/demo.01.01-r-solo");
		assertThat(lesson.exercises().get(0).solutionHtml()).startsWith("<details>")
			.contains("<a href=\"#repetida\">Repetida</a>");
	}

	private void insert(String part, String snippet) {
		String marker = switch (part) {
			case "body" -> "Texto de cuerpo.";
			case "statement" -> "Enunciado dos.";
			default -> "Solución dos.";
		};
		fixture.replace(ContentFixture.INTRO, marker, marker + "\n\n" + snippet + "\n");
	}

	private static String field(LessonContent lesson, String part) {
		return switch (part) {
			case "body" -> lesson.bodyHtml();
			case "statement" -> lesson.exercises().get(1).statementHtml();
			default -> lesson.exercises().get(1).solutionHtml();
		};
	}

	private static String label(String part) {
		return part.equals("body") ? "demo.01.01" : "demo.01.01 demo.01.01.e02 " + part;
	}
}
