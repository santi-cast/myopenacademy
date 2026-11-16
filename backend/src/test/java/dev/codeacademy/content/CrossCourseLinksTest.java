package dev.codeacademy.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * H8, T5 (FORMATO.md 10.2): the example course links to a module of ejemplo-destino with an anchor and to a term of its
 * glossary, with their GitHub addresses. With both courses installed they are internal links; alone, they stay on GitHub.
 */
class CrossCourseLinksTest {

	static final String MODULE = "https://github.com/myopenacademy/ejemplo-destino/blob/main/fase-01-tu-primer-proyecto/"
		+ "01-mi-primer-repositorio.md";

	@TempDir Path temp;

	@Test
	void linksToAnInstalledCourseBecomeInternalWithTheirAnchor() {
		String body = ContentLoader.loadAll(ExampleContent.root()).stream()
			.filter(course -> course.manifest().course().id().equals("ejemplo")).findFirst().orElseThrow()
			.lessons().get(ExampleContent.LESSON).bodyHtml();
		assertThat(body)
			.contains("<a href=\"/lessons/ejemplo-destino.mi-primer-repositorio#11-qué-es-un-repositorio\">Mi primer repositorio, 1.1</a>")
			.contains("<a href=\"/documents/ejemplo-destino.glosario#repositorio\">repositorio</a>")
			.doesNotContain("github.com/myopenacademy/ejemplo-destino");
	}

	@Test
	void linksToACourseThatIsNotInstalledKeepTheirGithubAddress() throws Exception {
		Path root = Files.createDirectory(temp.resolve("content"));
		ExampleContent.copyCourseInto("ejemplo", root);
		String body = ContentLoader.loadAll(root).getFirst().lessons().get(ExampleContent.LESSON).bodyHtml();
		assertThat(body).contains("<a href=\"" + MODULE).contains("target=\"_blank\" rel=\"noopener noreferrer\">Mi primer repositorio, 1.1</a>");
	}

	@Test
	void anAnchorTheTargetLacksIsDroppedAndOtherAddressesAreKept() {
		var courses = ContentLoader.loadAll(ExampleContent.root());
		String html = "<p><a href=\"" + MODULE + "#no-existe\" target=\"_blank\" rel=\"noopener noreferrer\">a</a> "
			+ "<a href=\"https://github.com/myopenacademy/ejemplo-destino/blob/main/README.md\" target=\"_blank\" rel=\"noopener noreferrer\">b</a> "
			+ "<a href=\"https://github.com/otra/cosa/blob/main/x.md\" target=\"_blank\" rel=\"noopener noreferrer\">c</a></p>";
		String rewritten = CrossCourseLinks.rewriteFor(courses, html);
		assertThat(rewritten).contains("<a href=\"/lessons/ejemplo-destino.mi-primer-repositorio\">a</a>")
			.contains("blob/main/README.md\" target=\"_blank\"").contains("github.com/otra/cosa");
	}
}
